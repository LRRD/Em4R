#pragma once

#include <impl.h>

#include <stdint.h>

// Values for the flag byte.
#define	SD6575_FLAG_ENABLED		0x80	// motor is enabled
#define	SD6575_FLAG_MOVING		0x40	// move request is active
#define	SD6575_FLAG_FAULT		0x20	// current fault indication
#define	SD6575_FLAG_MIN_LIMIT		0x10	// current minimum limit switch indication
#define	SD6575_FLAG_MAX_LIMIT		0x08	// current maximum limit switch indication
#define	SD6575_FLAG_FAULT_STOP		0x04	// stopped due to motor fault
#define	SD6575_FLAG_MIN_LIMIT_STOP	0x02	// stopped by minimum limit switch
#define	SD6575_FLAG_MAX_LIMIT_STOP	0x01	// stopped by maximum limit switch

// Automation Direct STP-DRV-6575 stepper driver
class SD6575
{
public:
	~SD6575();

	// Construct.
	// instance_ is the instance number for debugging.
        // pos_is_cw_ is true if position values increase in the clockwise direction.
	// gpo_en_, gpo_dir_ and gpo_step_ are the pin numbers for the EN, DIR and STEP outputs.
	// gpi_fault_ is the pin number for the FAULT input (normally open, closure to ground on fault).
	// gpi_min_lim_ and gpi_max_lim_ are the pin numbers for the minimum and maximum limit switches.
	// If pos_is_cw_ is true, the maximum limit switch is the limit of CW rotation, and vice versa.
	// If pos_is_cw_ is false, the maximum limit switch is the limit of CCW rotation, and vice versa.
	// Limit switches are assumed to be normally closed to ground, and to open on fault.
	// If a limit switch pin number is 0, that limit switch is not monitored.
	// gpi_jog_inc_ and gpi_jog_dec_ are the pin numbers for jog switches.
	// If a jog switch pin number is 0, that function is not provided.
	// gpi_jog_en_ is the pin number for the jog enable, and gpi_jog_en_level_ is the enabling level (0 or 1).
	// jog_interval_ is the number of ticks between steps when jogging, plus 1 (0 disables jogging).
        SD6575(uint8_t instance_, bool pos_is_cw_,
		uint8_t gpo_en_, uint8_t gpo_dir_, uint8_t gpo_step_,
		uint8_t gpi_fault_, uint8_t gpi_min_lim_, uint8_t gpi_max_lim_,
		uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_,
		uint8_t gpi_jog_en_, uint8_t gpi_jog_en_level_,
		uint32_t jog_interval_);

	// Set the requested stepper position and speed.
	// pos_ is the intended stepper position.
	// interval_ is the interval between steps, in 250 microsecond ticks, plus 1.
	// An interval_ value of 1 means to move every tick; a value of 0 means to stop.
	void Move(int16_t pos_, uint32_t interval_);

	// Stop moving and stay at the current position.
	void Stop();

	// Reset the stepper position to the specified value without moving the motor.
	void ResetPosition(int16_t pos_);

	// Get the current position.
	int16_t GetPos();

	// Get the current interval between steps, in 250 microsecond ticks, plus 1.
	// An interval_ value of 1 means the motor is stepping every tick.
	// An interval_ value of 0 means that the motor is stopped.
	uint32_t GetInterval();

	// Get flags.
	// The returned value will be an ORed combination of the SD6575_FLAG_ values.
	uint8_t GetFlags();

	// Log information to the debug console.
	void Log();

	// Do motion; call exactly once per tick.
	void Work();

	DECLARE_IMPL(SD6575);
};
