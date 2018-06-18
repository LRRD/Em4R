
#include "operator.h"
#include "clock.h"
#include "messages.h"
#include "controllinkmaster.h"
#include "physicalencoder.h"
#include "table.h"

#include <debug.h>
#include <crc.h>

#include <Arduino.h>
#include <Wire.h>
#include <Ethernet.h>
#include <IPAddress.h>

Operator::~Operator()
{
}

Operator::Operator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_) :
	instance(instance_),
	instance_name(instance_name_),
	link(link_)
{
}

uint8_t Operator::GetInstanceID()
{
	return instance;
}

void Operator::PostControlMoveImmediate(int16_t pos_)
{
	// Set up a control capsule.
	operator_control_capsule_data_t occd;

	// Go to the specified value.
	occd.requested_value = pos_;

	// Move.
	occd.command = OPERATOR_CONTROL_CMD_MOVE;

	// As soon as possible.
	occd.time_to_achieve = 0;

	// Execute the control capsule.
        Control(occd);
}

void Operator::PostControlStop()
{
	// Set up a control capsule.
	operator_control_capsule_data_t occd;

	// Stop.
	occd.command = OPERATOR_CONTROL_CMD_STOP;

	// Execute the control capsule.
        Control(occd);
}

void Operator::PostControlResetPos(int16_t step_)
{
	// Set up a control capsule.
	operator_control_capsule_data_t occd;

	// Tell the motor it's at that maximum value.
	occd.requested_value = step_;

	// Reset.
	occd.command = OPERATOR_CONTROL_CMD_RESET;

	// Execute the control capsule.
	Control(occd);
}

CLOperator::~CLOperator()
{
}

CLOperator::CLOperator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_,
	Clock& clock_, Preferences& prefs_) :
	Operator(instance_, instance_name_, link_),
	clock(clock_),
	prefs(prefs_),
	min_pos_step(0),
	max_pos_step(0),
	min_ticks_per_step(0),
	max_ticks_per_step(0),
	current_pos_step(0),
	requested_pos_step(0),
	ticks_per_step(0),
	requested_time_to_achieve(0),
	last_work_clock_ms(0),
	driver_flags(0)
{
}

void CLOperator::SetMinMaxTicksPerStep(uint16_t min_, uint16_t max_)
{
	min_ticks_per_step = min_;
	max_ticks_per_step = max_;
}

void CLOperator::ResetStepPos(int16_t pos_)
{
	// Stop moving and reset to the specified position.
	requested_pos_step = pos_;
	ticks_per_step = 0;

	// If we have driver hardware, generate a reset request.
	if (link) {
		uint8_t msg[4];
		msg[0] = (instance << 4) | 0x03;
		msg[1] = (uint8_t)((pos_ >> 8) & 0xFF);
		msg[2] = (uint8_t)(pos_ & 0xFF);
		msg[3] = crc8(msg, 5);
		link->Send(msg, 4);
	}

	// Otherwise, simulate it.
	else {
		current_pos_step = pos_;
	}
}

void CLOperator::Work()
{
	// If we have a real driver, we don't need to do this.
	if (link) {
		return;
	}

	// How many ticks has it been since the last run?
	uint32_t now = clock.Milliseconds();
	if (!last_work_clock_ms) {
		last_work_clock_ms = now;
		return;
	}
	// 4 ticks per millisecond.
	uint32_t elapsed_ticks = (now - last_work_clock_ms) * 4;
	if (elapsed_ticks == 0) {
		elapsed_ticks = 1;
	}
	last_work_clock_ms = now;

	// Are we moving?
	if (ticks_per_step) {

		// Are we already there?
		if (current_pos_step == requested_pos_step) {
			DBG_ERR("Simulator [%s]: Reached position %d at 0x%08X ms", instance_name, current_pos_step, now);
			ticks_per_step = 0;
		}

		// Otherwise, how far should we move in the amount of time that has elapsed since the last move?
		else {
			int16_t move_steps = elapsed_ticks / ticks_per_step;
			if (move_steps == 0) {
				move_steps = 1;
			}
			DBG_ERR("Simulator [%s]: steps to move = %d", instance_name, move_steps);

			// Determine which direction we are moving, and update the position.
			bool increase = (current_pos_step < requested_pos_step);
			current_pos_step += (increase ? move_steps : -move_steps);

			// Stop when the requested step is reached.
			if (increase && (current_pos_step > requested_pos_step)) {
				current_pos_step = requested_pos_step;
			}
			if (!increase && (current_pos_step < requested_pos_step)) {
				current_pos_step = requested_pos_step;
			}

			// Fail-safe; this shouldn't happen since the request is capped to the step limits.
			if (current_pos_step > max_pos_step) {
				current_pos_step = max_pos_step;
			}
			if (current_pos_step < min_pos_step) {
				current_pos_step = min_pos_step;
			}

			DBG_ERR("Simulator [%s]: %lu ticks elapsed, moved %d steps to step %d",
				instance_name, elapsed_ticks, move_steps, current_pos_step);
		}
	}
};

void CLOperator::DriverStatus(const uint8_t* msg_, uint16_t bytes_)
{
	// Update flags and current position.
	if (bytes_ == 3) {
		driver_flags = msg_[0];
		current_pos_step = (int16_t)(((uint16_t)msg_[1] << 8) | msg_[2]);
	}
}

uint8_t CLOperator::DriverFlags()
{
	return driver_flags;
}

PhysicalOperator::~PhysicalOperator()
{
}

PhysicalOperator::PhysicalOperator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_,
	Clock& clock_, Preferences& prefs_,
	PhysicalEncoder* enc_, const char* (*format_)(int16_t)) :
	CLOperator(instance_, instance_name_, link_, clock_, prefs_),
	enc(enc_),
	format(format_),
	encoder_pos_pu(0),
	min_pos_pu(0),
	max_pos_pu(0),
	requested_pos_pu(0)
{
}

int16_t	PhysicalOperator::PosPUToPosStep(int16_t pu_)
{
	return Table::Interpolate(pu_, min_pos_pu, max_pos_pu, min_pos_step, max_pos_step);
}

int16_t	PhysicalOperator::PosStepToPosPU(int16_t steps_)
{
	return Table::Interpolate(steps_, min_pos_step, max_pos_step, min_pos_pu, max_pos_pu);
}

void PhysicalOperator::Control(const operator_control_capsule_data_t& occd_)
{
	// Check the command.
	switch (occd_.command) {
	case OPERATOR_CONTROL_CMD_NOP:
		break;
	case OPERATOR_CONTROL_CMD_MOVE:
		ControlMove(occd_);
		break;
	case OPERATOR_CONTROL_CMD_STOP:
		ControlStop(occd_);
		break;
	case OPERATOR_CONTROL_CMD_RESET:
		ControlReset(occd_);
		break;
	default:
		DBG_ERR("Control [%s]: requested unknown command %u", instance_name, occd_.command);
	}
}

void PhysicalOperator::ControlMove(const operator_control_capsule_data_t& occd_)
{
	// Save the new requested value.
	requested_pos_pu = occd_.requested_value;
	requested_time_to_achieve = occd_.time_to_achieve;
	DBG_ERR("requested_pos_pu = %d", requested_pos_pu);

	bool capped = false;
	if (requested_pos_pu < min_pos_pu) {
		requested_pos_pu = min_pos_pu;
		capped = true;
	}
	if (requested_pos_pu > max_pos_pu) {
		requested_pos_pu = max_pos_pu;
		capped = true;
	}

	// Convert to a motor step.
	requested_pos_step = PosPUToPosStep(requested_pos_pu);

	// Determine the absolute number of steps to move.
	uint16_t absolute_steps_to_move = (requested_pos_step > current_pos_step) ?
		requested_pos_step - current_pos_step :
		current_pos_step - requested_pos_step;

	// Determine the number of 250us ticks per step.
	uint32_t ticks_per_step_32 = min_ticks_per_step;
	if (absolute_steps_to_move) {
		// 4 steps per millisecond
		ticks_per_step_32 = (requested_time_to_achieve * 4) / absolute_steps_to_move;
	}
	if (ticks_per_step_32 < min_ticks_per_step) {
		ticks_per_step_32 = min_ticks_per_step;
	}
	if (ticks_per_step_32 > max_ticks_per_step) {
		ticks_per_step_32 = max_ticks_per_step;
	}
	if (ticks_per_step_32 > 0xFFFFU) {
		ticks_per_step_32 = 0xFFFFU;
	}
	ticks_per_step = (uint16_t)ticks_per_step_32;

	DBG_ERR("Control [%s]: requested %s %sin %lu ms",
		instance_name, (*format)(requested_pos_pu), capped ? "(capped) " : "", requested_time_to_achieve);
	DBG_ERR("              %d -> %d at %u ticks/step", current_pos_step, requested_pos_step, ticks_per_step);

	// If we have driver hardware, generate a move request.
	// Otherwise it will be simulated in Work().
	if (link) {
		uint8_t msg[6];
		msg[0] = (instance << 4) | 0x01;
		msg[1] = (uint8_t)((requested_pos_step >> 8) & 0xFF);
		msg[2] = (uint8_t)(requested_pos_step & 0xFF);
		msg[3] = (uint8_t)((ticks_per_step >> 8) & 0xFF);
		msg[4] = (uint8_t)(ticks_per_step & 0xFF);
		msg[5] = crc8(msg, 5);
		link->Send(msg, 6);
	}
}

void PhysicalOperator::ControlStop(const operator_control_capsule_data_t& occd_)
{
	// Stop moving.
	ticks_per_step = 0;

	DBG_ERR("Control [%s]: requested stop/hold", instance_name);

	// If we have driver hardware, generate a stop/hold request.
	// Otherwise it will be simulated in Work().
	if (link) {
		uint8_t msg[2];
		msg[0] = (instance << 4) | 0x02;
		msg[1] = crc8(msg, 1);
		link->Send(msg, 2);
	}
}

void PhysicalOperator::ControlReset(const operator_control_capsule_data_t& occd_)
{
	// Stop moving.
	ticks_per_step = 0;

	// requested_pos_pu, despite the name, is a step number for a reset request.
	DBG_ERR("Control [%s]: requested reset to step %u", instance_name, occd_.requested_value);

	// If we have driver hardware, generate a stop/hold request.
	// Otherwise it will be simulated in Work().
	if (link) {
		uint8_t msg[4];
		msg[0] = (instance << 4) | 0x03;
		msg[1] = (uint8_t)((occd_.requested_value >> 8) & 0xFF);
		msg[2] = (uint8_t)(occd_.requested_value & 0xFF);
		msg[3] = crc8(msg, 3);
		link->Send(msg, 4);
	}
}

void PhysicalOperator::Status(operator_status_capsule_data_t& ocsd_)
{
	// Report driver flags.
	ocsd_.flags = driver_flags;
	ocsd_.dummy1 = 0;

	// If we have an associated encoder, report its position.
	if (enc && enc->GetValuePU(ocsd_.current_value)) {
//		DBG_ERR("Status [%s]: at step %d, encoder reporting %s", instance_name, current_pos_step, (*format)(ocsd_.current_value));
	}

	// If we have no encoder, or if the encoder isn't reporting, rely on the motor position.
	else {
		ocsd_.current_value = PosStepToPosPU(current_pos_step);
//		DBG_ERR("Status [%s]: at step %d, reporting %s", instance_name, current_pos_step, (*format)(ocsd_.current_value));
	}

	// Report back the request.
	ocsd_.requested_value = requested_pos_pu;
	ocsd_.time_to_achieve = requested_time_to_achieve;
}

TableMappedOperator::~TableMappedOperator()
{
}

TableMappedOperator::TableMappedOperator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_,
	Clock& clock_, Preferences& prefs_,
	PhysicalEncoder* enc_, const char* (*format_)(int16_t),
	const Table* table_) :
	PhysicalOperator(instance_, instance_name_, link_, clock_, prefs_, enc_, format_),
	table(table_)
{
	ASSERT(table);
	min_pos_step = table->First().x < table->Last().x ? table->First().x : table->Last().x;
	max_pos_step = table->First().x > table->Last().x ? table->First().x : table->Last().x;
	min_pos_pu = table->First().y < table->Last().y ? table->First().y : table->Last().y;
	max_pos_pu = table->First().y > table->Last().y ? table->First().y : table->Last().y;
}

int16_t	TableMappedOperator::PosPUToPosStep(int16_t pu_)
{
	// Do the reverse table lookup (physical unit to step).
	return table->MapYToX(pu_);
}

int16_t TableMappedOperator::PosStepToPosPU(int16_t steps_)
{
	// Do the forward table lookup (step to physical unit).
	return table->MapXToY(steps_);
}
