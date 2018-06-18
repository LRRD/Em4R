
#include "sd6575.h"

#include <debug.h>

#include <Arduino.h>

// The number of ticks between enabling the motor and sending the first step.
#define	STARTUP_WAIT_TICKS	10

// The sense of the DIR signal for CCW and CW motion.
#define	DIR_CCW			HIGH
#define	DIR_CW			LOW

int pinMode(uint8_t pin)
{
  if (pin >= NUM_DIGITAL_PINS) return (-1);

  uint8_t bit = digitalPinToBitMask(pin);
  uint8_t port = digitalPinToPort(pin);
  volatile uint8_t *reg = portModeRegister(port);
  if (*reg & bit) return (OUTPUT);

  volatile uint8_t *out = portOutputRegister(port);
  return ((*out & bit) ? INPUT_PULLUP : INPUT);
}

struct SD6575::Impl
{
	// Implement main class methods.
	~Impl();
	Impl(uint8_t instance_, bool pos_is_cw_,
		uint8_t gpo_en_, uint8_t gpo_dir_, uint8_t gpo_step_,
		uint8_t gpi_fault_, uint8_t gpi_min_lim_, uint8_t gpi_max_lim_,
		uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_,
		uint8_t gpi_jog_en_, uint8_t jog_en_level_,
		uint32_t jog_interval_);

	// The instance number, for debugging.
	uint8_t instance;

	// true if position values increase in the clockwise direction; false if the opposite is true.
	bool pos_is_cw;

	// output pins for EN, DIR and STEP signals.
	uint8_t gpo_en;
	uint8_t gpo_dir;
	uint8_t gpo_step;

	// input pin for FAULT signal.
	uint8_t gpi_fault;

	// input pins for the limit switches, if nonzero.
	uint8_t gpi_min_lim;
	uint8_t gpi_max_lim;

	// input pins for the jog switches, if nonzero.
	uint8_t gpi_jog_inc;
	uint8_t gpi_jog_dec;

	// input pins for the jog enable switch, and the corresponding level.
	uint8_t gpi_jog_en;
	uint8_t jog_en_level;

	// the number of ticks between steps, plus 1 (0 = stopped; 1 = every tick) while jogging.
	uint32_t jog_interval;

	// the current position, in number of motor steps
	int16_t current_pos;

	// the intended position, in number of motor steps
	int16_t target_pos;

	// the number of ticks between steps, plus 1 (0 = stopped; 1 = every tick)
	uint32_t interval;

	// the current flag values.
	uint8_t flags;

	// is the motor enabled?
	bool enabled;

	// the current state of the direction pin.
	uint8_t direction;

	// the number of ticks until the next move.
	uint32_t wait_ticks;

	// the jog status on the previous loop.
	int8_t previous_jog;

	// the flags on the previous loop.
	uint8_t previous_flags;

	// Log debug information.
	void Log();

	// Do periodic work (call once per tick).
	void Work();
};

SD6575::Impl::~Impl()
{
}

SD6575::Impl::Impl(uint8_t instance_, bool pos_is_cw_,
	uint8_t gpo_en_, uint8_t gpo_dir_, uint8_t gpo_step_,
	uint8_t gpi_fault_, uint8_t gpi_min_lim_, uint8_t gpi_max_lim_,
	uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_,
	uint8_t gpi_jog_en_, uint8_t jog_en_level_,
	uint32_t jog_interval_) :
	instance(instance_),
	pos_is_cw(pos_is_cw_),
	gpo_en(gpo_en_),
	gpo_dir(gpo_dir_),
	gpo_step(gpo_step_),
	gpi_fault(gpi_fault_),
	gpi_min_lim(gpi_min_lim_),
	gpi_max_lim(gpi_max_lim_),
	gpi_jog_inc(gpi_jog_inc_),
	gpi_jog_dec(gpi_jog_dec_),
	gpi_jog_en(gpi_jog_en_),
	jog_en_level(jog_en_level_),
	jog_interval(jog_interval_),
	current_pos(0),
	target_pos(0),
	interval(0),
	flags(0),
	enabled(false),
	direction(0),
	wait_ticks(0),
	previous_jog(0),
	// Force flags to update.
	previous_flags(0xFF)
{
	ASSERT(gpo_en);
	ASSERT(gpo_dir);
	ASSERT(gpo_step);
	ASSERT(gpi_fault);

	// No jog if jog interval not set.
	if (!jog_interval) {
		gpi_jog_inc = gpi_jog_dec = 0;
	}

	// Start with the motor disabled.
	digitalWrite(gpo_en, HIGH);
	direction = DIR_CCW;
	digitalWrite(gpo_dir, direction);
	digitalWrite(gpo_step, LOW);

	// Enable the GPIO pins.
	pinMode(gpo_en, OUTPUT);
	pinMode(gpo_dir, OUTPUT);
	pinMode(gpo_step, OUTPUT);
	pinMode(gpi_fault, INPUT_PULLUP);
	if (gpi_min_lim) {
		pinMode(gpi_min_lim, INPUT_PULLUP);
	}
	if (gpi_max_lim) {
		pinMode(gpi_max_lim, INPUT_PULLUP);
	}
	if (gpi_jog_inc) {
		pinMode(gpi_jog_inc, INPUT_PULLUP);
	}
	if (gpi_jog_dec) {
		pinMode(gpi_jog_dec, INPUT_PULLUP);
	}
	if (gpi_jog_en) {
		pinMode(gpi_jog_en, INPUT_PULLUP);
	}
}

void SD6575::Impl::Log()
{
	char flag_text[150];
	snprintf(flag_text, 149, "%s%s%s%s%s%s%s%s", 
		(flags & SD6575_FLAG_ENABLED) ? "ENABLED " : "",
		(flags & SD6575_FLAG_MOVING) ? "MOVING " : "",
		(flags & SD6575_FLAG_FAULT) ? "FAULT " : "",
		(flags & SD6575_FLAG_MIN_LIMIT) ? "MINLIM " : "",
		(flags & SD6575_FLAG_MAX_LIMIT) ? "MAXLIM " : "",
		(flags & SD6575_FLAG_FAULT_STOP) ? "FAULT-STOP " : "", 
		(flags & SD6575_FLAG_MIN_LIMIT_STOP) ? "MINLIM-STOP " : "",
		(flags & SD6575_FLAG_MAX_LIMIT_STOP) ? "MAXLIM-STOP " : "");
	if (interval) {
		if (previous_jog) {
			DBG_MOTOR("MOT%u: at step %d, wait %lu, jog interval %lu [%s]", instance, current_pos, wait_ticks, jog_interval, flag_text);
		} else {
			DBG_MOTOR("MOT%u: at step %d, wait %lu, interval %lu [%s]", instance, current_pos, wait_ticks, interval, flag_text);
		}
	} else {
//		DBG_MOTOR("MOT%u: at step %d [%s] minlim=%d(%d), maxlim=%d(%d)", instance, current_pos, flag_text, gpi_min_lim, digitalRead(gpi_min_lim), gpi_max_lim, digitalRead(gpi_max_lim));
		DBG_MOTOR("MOT%u: at step %d [%s]", instance, current_pos, flag_text);
	}
}

void SD6575::Impl::Work()
{
	// FAULT input is active low.
	flags = (flags & ~SD6575_FLAG_FAULT) | ((digitalRead(gpi_fault) == LOW) ? SD6575_FLAG_FAULT : 0);

	// Limit switches are active high.
	if (gpi_min_lim) {
		flags = (flags & ~SD6575_FLAG_MIN_LIMIT) | ((digitalRead(gpi_min_lim) == HIGH) ? SD6575_FLAG_MIN_LIMIT : 0);
	}
	if (gpi_max_lim) {
		flags = (flags & ~SD6575_FLAG_MAX_LIMIT) | ((digitalRead(gpi_max_lim) == HIGH) ? SD6575_FLAG_MAX_LIMIT : 0);
	}

	// If we are not at a limit, we can clear the "stopped" indication for that limit.
	if (!(flags & SD6575_FLAG_MIN_LIMIT)) {
		flags &= ~SD6575_FLAG_MIN_LIMIT_STOP;
	}
	if (!(flags & SD6575_FLAG_MAX_LIMIT)) {
		flags &= ~SD6575_FLAG_MAX_LIMIT_STOP;
	}

	// Determine our jog mode.
	int8_t jog = 0;
	if (!gpi_jog_en || (digitalRead(gpi_jog_en) == jog_en_level)) {
		if (gpi_jog_inc && (digitalRead(gpi_jog_inc) == LOW)) {
			jog = 1;
		} else if (gpi_jog_dec && (digitalRead(gpi_jog_dec) == LOW)) {
			jog = -1;
		}
	}
	if (jog != previous_jog) {
		DBG_JOG("MOT%u: jog %s at step %d", instance, (jog ? ((jog > 0) ? "inc" : "dec") : "off"), current_pos);
		previous_jog = jog;
	}

	// Are we not moving?
	if (!jog && ((target_pos == current_pos) || !interval)) {

		// Regardless of why we aren't moving, make sure settings are consistent.
		target_pos = current_pos;
		interval = 0;
		flags &= ~SD6575_FLAG_MOVING;

		// Turn off the motor if enabled.
		if (flags & SD6575_FLAG_ENABLED) {
			//DBG_MOTOR("MOT%u: disabling motor", instance);
			digitalWrite(gpo_en, HIGH);
			flags &= ~SD6575_FLAG_ENABLED;
		}
	}

	// Do we need to enable the motor?
	else if (!(flags & SD6575_FLAG_ENABLED)) {
		//DBG_MOTOR("MOT%u: enabling motor", instance);
		digitalWrite(gpo_en, LOW);
		flags |= SD6575_FLAG_ENABLED;

		// Impose the startup delay.
		wait_ticks = STARTUP_WAIT_TICKS;
	}

	// Is it not yet time to move?
	else if (wait_ticks) {
		wait_ticks--;
	}

	// Time to move.
	else {
		// Determine the direction to move.
		bool pos_decreasing = (jog < 0) || (!jog && (target_pos < current_pos));
		bool move_cw = pos_decreasing ^ pos_is_cw;

		// Are we faulted?
		if (flags & SD6575_FLAG_FAULT) {
			target_pos = current_pos;
			flags |= SD6575_FLAG_FAULT_STOP;
		}

		// Are we at a limit?
		else if (pos_decreasing && (flags & SD6575_FLAG_MIN_LIMIT)) {
			target_pos = current_pos;
			flags |= SD6575_FLAG_MIN_LIMIT_STOP;
		} else if (!pos_decreasing && (flags & SD6575_FLAG_MAX_LIMIT)) {
			target_pos = current_pos;
			flags |= SD6575_FLAG_MAX_LIMIT_STOP;
		}

		else {
			// Clear fault/stop indications.
			flags &= ~SD6575_FLAG_FAULT_STOP;
			flags &= ~SD6575_FLAG_MIN_LIMIT_STOP;
			flags &= ~SD6575_FLAG_MAX_LIMIT_STOP;

			// Set the direction.
			uint8_t new_direction = move_cw ? DIR_CW : DIR_CCW;
			if (new_direction != direction) {
				direction = new_direction;
				digitalWrite(gpo_dir, new_direction);
			}

			// Do the step.
			//DBG_ERR("step");
			digitalWrite(gpo_step, HIGH);
			digitalWrite(gpo_step, LOW);

			// Adjust the position.
			current_pos += (pos_decreasing) ? -1 : 1;

			// When do we move again?
			if (jog) {
				wait_ticks = jog_interval - 1;
			} else {
				wait_ticks = interval - 1;
			}
		}
	}

	if (flags != previous_flags) {
		DBG_MOTOR("MOT%u: flags: %s%s%s%s%s%s%s%s", instance,
			(flags & SD6575_FLAG_ENABLED) ? "ENABLED " : "",
			(flags & SD6575_FLAG_MOVING) ? "MOVING " : "",
			(flags & SD6575_FLAG_FAULT) ? "FAULT " : "",
			(flags & SD6575_FLAG_MIN_LIMIT) ? "MINLIM " : "",
			(flags & SD6575_FLAG_MAX_LIMIT) ? "MAXLIM " : "",
			(flags & SD6575_FLAG_FAULT_STOP) ? "FAULT-STOP " : "", 
			(flags & SD6575_FLAG_MIN_LIMIT_STOP) ? "MINLIM-STOP " : "",
			(flags & SD6575_FLAG_MAX_LIMIT_STOP) ? "MAXLIM-STOP " : "");
		previous_flags = flags;
	}
}

SD6575::~SD6575()
{
	delete impl;
}

SD6575::SD6575(uint8_t instance_, bool pos_is_cw_,
	uint8_t gpo_en_, uint8_t gpo_dir_, uint8_t gpo_step_,
	uint8_t gpi_fault_, uint8_t gpi_min_lim_, uint8_t gpi_max_lim_,
	uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_, 
	uint8_t gpi_jog_en_, uint8_t jog_en_level_,
	uint32_t jog_interval_) :
	impl(new Impl(instance_, pos_is_cw_, gpo_en_, gpo_dir_, gpo_step_, gpi_fault_, gpi_min_lim_, gpi_max_lim_,
		gpi_jog_inc_, gpi_jog_dec_, gpi_jog_en_, jog_en_level_, jog_interval_))
{
}

void SD6575::Move(int16_t pos_, uint32_t interval_)
{
	// Ignore non-move moves.
	if ((pos_ == impl->target_pos) || !interval_) {
		return;
	}

	// TODO: constrain interval to jog interval
	if (impl->jog_interval && (interval_ < impl->jog_interval)) {
		interval_ = impl->jog_interval;
	}

	// Save the new position and interval.
	impl->target_pos = pos_;
	impl->interval = interval_;

	// Take action on the next tick.
	impl->wait_ticks = 0;

	// Indicate that we are moving.
	impl->flags |= SD6575_FLAG_MOVING;

	DBG_MOTOR("MOT%u: Moving to %d at %u ticks/step", impl->instance, pos_, interval_);
}

void SD6575::Stop()
{
	// Set the target to the current position, so we will stop on the next tick.
	impl->target_pos = impl->current_pos;
	impl->interval = 0;

	DBG_MOTOR("MOT%u: Stopping motion", impl->instance);
}

void SD6575::ResetPosition(int16_t pos_)
{
	// Reset both the current and target position and stop moving.
	impl->target_pos = impl->current_pos = pos_;
	impl->interval = 0;

	DBG_MOTOR("MOT%u: Resetting position to %d", impl->instance, pos_);
}

int16_t SD6575::GetPos()
{
	return impl->current_pos;
}

uint32_t SD6575::GetInterval()
{
	return impl->interval;
}

uint8_t SD6575::GetFlags()
{
	return impl->flags;
}

void SD6575::Log()
{
	impl->Log();
}

void SD6575::Work()
{
	impl->Work();
}
