#pragma once

#include <stdint.h>

class ControlLinkMaster;
class PhysicalEncoder;
class Table;
struct operator_control_capsule_data_t;
struct operator_status_capsule_data_t;
class Clock;
class Preferences;
class Operator
{
public:
	virtual ~Operator();
	Operator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_);

	// Simulate movement.
	virtual void Work() = 0;

	// Handle a control message, and generate a status message.
	virtual void Control(const operator_control_capsule_data_t& occd_) = 0;
	virtual void Status(operator_status_capsule_data_t& ocsd_) = 0;

	// Handle a link status message.
	virtual void DriverStatus(const uint8_t* msg_, uint16_t bytes_) = 0;

	// Get the driver flag byte (or simulation thereof).
	// Note that these flags may vary based on driver type.
	virtual uint8_t DriverFlags() = 0;

	// Get the operator's instance ID.
	uint8_t GetInstanceID();

	// Post a control command.
	void PostControlMoveImmediate(int16_t pos_);
	void PostControlStop();
	void PostControlResetPos(int16_t step_);

protected:
	// The instance ID.
	uint8_t instance;

	// The instance name, for debugging.
	const char* instance_name;

	// The serial link to the driver.
	ControlLinkMaster* link;
};

// Operator controlled by the ControlLink driver.
class CLOperator : public Operator
{
public:
	virtual ~CLOperator();
	CLOperator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_,
		Clock& clock_, Preferences& prefs_);

	// Set the minimum and maximum number of 250 usec ticks per step.
	void SetMinMaxTicksPerStep(uint16_t min_, uint16_t max_);

	// Reset the position to a known value.  May not be meaningful for all operator types.
	void ResetStepPos(int16_t pos_);

protected:
	// Access to the system clock and preferences.
	Clock& clock;
	Preferences& prefs;

	// The minimum and maximum motor positions in steps.
	int16_t min_pos_step;
	int16_t max_pos_step;

	// The minimum number of 250usec ticks per step (1 would be 4,000 steps/sec).
	uint16_t min_ticks_per_step;

	// The maximum number of 250usec ticks per step (4,000 would be 1 step/sec).
	// This must not be larger than 0xFFFF to be communicated to the driver.
	// 0xFFFF is 16 seconds per step, likely slow enough as to be useless.
	uint16_t max_ticks_per_step;

	// Raw motor step, reported by motor driver (or simulated locally).
	int16_t current_pos_step;

	// The absolute step value to which the motor is expected to move.
	// Ignored if ticks_per_step is 0.
	// 0 is nominally the zero-degree position.
	int16_t requested_pos_step;

	// If nonzero, the requested number of ticks per step for the current move.
	uint16_t ticks_per_step;

	// The time at which the motor is expected to reach the requested position.
	uint32_t requested_time_to_achieve;

	// The clock time at we last simulated movement.
	uint32_t last_work_clock_ms;

	// Flag information from the driver.
	uint8_t driver_flags;

	// Implement main-class methods.
	void Work() override;
	void DriverStatus(const uint8_t* msg_, uint16_t bytes_) override;
	uint8_t DriverFlags() override;
};

// An operator which knows about a physical unit.
class PhysicalOperator : public CLOperator
{
public:
	virtual ~PhysicalOperator();
	PhysicalOperator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_,
		Clock& clock_, Preferences& prefs_,
		PhysicalEncoder* enc_, const char* (*format_)(int16_t));

	// Implement main-class methods.
	void Control(const operator_control_capsule_data_t& occd_) override;
	void Status(operator_status_capsule_data_t& ocsd_) override;

protected:
	// Map a position in physical units to a motor step.
	// The base implementation just does a linear ramp.
	virtual int16_t PosPUToPosStep(int16_t pu_);

	// Map a position in motor staeps to a position in physical units.
	// The base implementation just does a linear ramp.
	virtual int16_t PosStepToPosPU(int16_t steps_);

	// If non-null, the encoder which reports the position of this operator's underlying device.
	// If null, the position must be determined open-loop based on the motor step value.
	PhysicalEncoder* enc;

	// The function which formats a signed value in the native units.
	const char* (*format)(int16_t);

	// Position in physical units, from encoder signal.
	int16_t encoder_pos_pu;

	// The minimum and maximum position, in physical units.
	int16_t min_pos_pu;
	int16_t max_pos_pu;

	// The position to which the model is expected to move, in physical units.
	int16_t requested_pos_pu;

	// Handle control commands.
	void ControlMove(const operator_control_capsule_data_t& occd_);
	void ControlStop(const operator_control_capsule_data_t& occd_);
	void ControlReset(const operator_control_capsule_data_t& occd_);
};

class TableMappedOperator : public PhysicalOperator
{
public:
	virtual ~TableMappedOperator();
	// table_ maps motor position (x) to physical position (y).
	TableMappedOperator(uint8_t instance_, const char* instance_name_, ControlLinkMaster* link_,
		Clock& clock_, Preferences& prefs_,
		PhysicalEncoder* enc_, const char* (format_)(int16_t),
		const Table* table_);

protected:
	// PhysicalOperator overrides.
	virtual int16_t PosPUToPosStep(int16_t pu_) override;
	virtual int16_t PosStepToPosPU(int16_t steps_) override;

	// The step-to-physical-position lookup table and its size.
	const Table* table;
};
