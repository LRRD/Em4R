#pragma once

#include <impl.h>

#include <stdint.h>

// Values for the flag byte.
#define	SPD315_FLAG_ENABLED		0x80	// motor is enabled
#define	SPD315_FLAG_CHANGING		0x40	// speed change request is active
#define	SPD315_FLAG_LOWER_LIMIT		0x10	// motor is at minimum speed
#define	SPD315_FLAG_UPPER_LIMIT		0x08	// motor is at maximum speed
#define	SPD315_FLAG_LOWER_LIMIT_STOP	0x02	// speed holding after reaching minimum
#define	SPD315_FLAG_UPPER_LIMIT_STOP	0x01	// speed holding after reaching maximum

// Critical Velocity SPD-315 Driver
struct SPD315
{
public:
	~SPD315();

	// Construct.
	// instance_ is the instance number for debugging.
	// gpo_en_ is the pin number of the EN signal (HIGH to disable).
	// pwm_ is the pin number for the PWM output.
	// gpi_jog_inc_ and gpi_jog_dec_ are the pin numbers for jog switches.
	// If a jog switch pin number is 0, that function is not provided.
        // gpi_jog_en_ is the pin number for the jog enable, and gpi_jog_en_level_ is the enabling level (0 or 1).
	// jog_interval_ is the number of ticks between speed changes when jogging, plus 1 (0 disables jogging).
        SPD315(uint8_t instance_, uint8_t gpo_en_, uint8_t pwm_,
		uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_,
                uint8_t gpi_jog_en_, uint8_t gpi_jog_en_level_,
		uint32_t jog_interval_);

	// Set the requested motor speed, and the rate of change.
	// pos_ is the intended speed.
	// interval_ is the interval between speed changes, in 250 microsecond ticks, plus 1.
	// An interval_ value of 1 means to change speed every tick; a value of 0 means to stop.
	void ChangeSpeed(int16_t speed_, uint32_t interval_);

	// Stop changing speed.
	void HoldSpeed();

	// Get the current speed.
	int16_t GetSpeed();

	// Get the current interval between steps, in 250 microsecond ticks, plus 1.
	// An interval_ value of 1 means the motor is changing speed every tick.
	// An interval_ value of 0 means that the motor is holding its speed.
	uint32_t GetInterval();

	// Get flags.
	// The returned value will be an ORed combination of the SPD315_FLAG_ values.
	uint8_t GetFlags();

	// Log information to debug console.
	void Log();

	// Do motion; call exactly once per tick.
	void Work();

	DECLARE_IMPL(SPD315);
};
