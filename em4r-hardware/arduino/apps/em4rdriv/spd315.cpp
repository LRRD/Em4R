
#include "spd315.h"

#include <debug.h>

#include <Arduino.h>

struct SPD315::Impl
{
	// Implement main class methods.
	~Impl();
	Impl(uint8_t instance_, uint8_t gpo_en_, uint8_t pwm_,
		uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_,
		uint8_t gpi_jog_en_, uint8_t jog_en_level_,
		uint32_t jog_interval_);

	// The instance number.
	uint8_t instance;

	// output pin for EN signal.
	uint8_t gpo_en;

	// output pin for PWM level.
	uint8_t pwm;

	// input pins for the jog switches, if nonzero.
        uint8_t gpi_jog_inc;
        uint8_t gpi_jog_dec;

	// input pins for the jog enable switch, and the corresponding level.
	uint8_t gpi_jog_en;
	uint8_t jog_en_level;

	// the number of ticks between speed changes, plus 1 (0 = stopped; 1 = every tick) while jogging.
	uint32_t jog_interval;

	// the current speed, as a PWM level (0..255)
	int16_t current_speed;

	// the intended speed, as a PWM level (0..255)
	int16_t target_speed;

	// the number of ticks between speed changes, plus 1 (0 = holding; 1 = every tick)
	uint32_t interval;

	// the current flag values.
	uint8_t flags;

	// is the driver enabled?
	bool enabled;

	// the number of ticks until the next speed change.
	uint32_t wait_ticks;

	// the jog status on the previous loop.
	int8_t previous_jog;

	// the flag values on the previous loop.
	uint8_t previous_flags;

	// Log debug information.
	void Log();

	// Do periodic work (call once per tick).
	void Work();
};

SPD315::Impl::~Impl()
{
}

SPD315::Impl::Impl(uint8_t instance_, uint8_t gpo_en_, uint8_t pwm_,
	uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_,
	uint8_t gpi_jog_en_, uint8_t jog_en_level_,
	uint32_t jog_interval_) :
	instance(instance_),
	gpo_en(gpo_en_),
	pwm(pwm_),
	gpi_jog_inc(gpi_jog_inc_),
	gpi_jog_dec(gpi_jog_dec_),
	gpi_jog_en(gpi_jog_en_),
	jog_en_level(jog_en_level_),
	jog_interval(jog_interval_),
	current_speed(0),
	target_speed(0),
	interval(0),
	flags(0),
	enabled(false),
	wait_ticks(0),
	// Force flags to update.
	previous_flags(0xFF)
{
	ASSERT(gpo_en);
	ASSERT(pwm);

	// No jog if jog interval not set.
	if (!jog_interval) {
		gpi_jog_inc = gpi_jog_dec = 0;
	}

	// Start with the pump disabled.
	digitalWrite(gpo_en, LOW);

	// Enable the GPIO pins.
	pinMode(gpo_en, OUTPUT);
	pinMode(pwm, OUTPUT);
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

void SPD315::Impl::Log()
{
	if (interval) {
		if (previous_jog) {
			DBG_MOTOR("SPD%u: at speed %d, wait %lu, jog interval %lu", instance, current_speed, wait_ticks, jog_interval);
		} else {
			DBG_MOTOR("SPD%u: at speed %d, wait %lu, interval %lu", instance, current_speed, wait_ticks, interval);
		}
	} else {
		DBG_MOTOR("SPD%u: at speed %d", instance, current_speed);
	}
}

void SPD315::Impl::Work()
{
	// Check for limits.
	flags = (flags & ~SPD315_FLAG_LOWER_LIMIT) | ((current_speed <= 0) ? SPD315_FLAG_LOWER_LIMIT : 0);
	flags = (flags & ~SPD315_FLAG_UPPER_LIMIT) | ((current_speed >= 255) ? SPD315_FLAG_UPPER_LIMIT : 0);

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
		DBG_JOG("SPD%u: jog %s at speed %d", instance, (jog ? ((jog > 0) ? "inc" : "dec") : "off"), current_speed);
		previous_jog = jog;
	}

	// Are we not changing speed?
	if (!jog && ((target_speed == current_speed) || !interval)) {

		// Regardless of why we aren't changing speed, make sure settings are consistent.
		target_speed = current_speed;
		interval = 0;
		flags &= ~SPD315_FLAG_CHANGING;
	}

	// Do we need to enable the motor?
	else if (!(flags & ~SPD315_FLAG_ENABLED)) {
		digitalWrite(gpo_en, HIGH);
		flags |= ~SPD315_FLAG_ENABLED;
	}

	// Is it not yet time to change speeds?
	else if (wait_ticks) {
		wait_ticks--;
		return;
	}

	// Time to change speed.
	else {
		// Determine the direction to move.
		bool speed_decreasing = (jog < 0) || (!jog && (target_speed < current_speed));

		// Are we at a limit?
		if (speed_decreasing && (current_speed == 0)) {
			target_speed = current_speed;
			flags |= SPD315_FLAG_LOWER_LIMIT_STOP;
		} else if (!speed_decreasing && (current_speed == 255)) {
			target_speed = current_speed;
			flags |= SPD315_FLAG_UPPER_LIMIT_STOP;
		} else {
			// Clear stop indications.
			flags &= ~SPD315_FLAG_LOWER_LIMIT_STOP;
			flags &= ~SPD315_FLAG_UPPER_LIMIT_STOP;

			// Adjust the speed.
			current_speed += (speed_decreasing) ? -1 : 1;

			// Cap the speed.
			if (current_speed < 0) {
				current_speed = 0;
			}
			if (current_speed > 255) {
				current_speed = 255;
			}

			// Set the output level.
			analogWrite(pwm, current_speed);

			// When do we move again?
			if (jog) {
				wait_ticks = jog_interval - 1;
			} else {
				wait_ticks = interval - 1;
			}
		}
	}

	// If motor is stopped, turn off the driver if enabled.
	if (!current_speed && (flags & SPD315_FLAG_ENABLED)) {
		DBG_MOTOR("SPD%u: disabling motor", instance);
		digitalWrite(gpo_en, LOW);
		flags &= ~SPD315_FLAG_ENABLED;
	}

	// If motor is running, turn on the driver if disabled.
	if (current_speed && !(flags & SPD315_FLAG_ENABLED)) {
		DBG_MOTOR("SPD%u: enabling motor", instance);
		digitalWrite(gpo_en, HIGH);
		flags |= SPD315_FLAG_ENABLED;
	}

	if (flags != previous_flags) {
		DBG_MOTOR("SPD%u: flags: %s%s%s%s%s%s", instance,
			(flags & SPD315_FLAG_ENABLED) ? "EN " : "",
			(flags & SPD315_FLAG_CHANGING) ? "CHANGING " : "",
			(flags & SPD315_FLAG_LOWER_LIMIT) ? "SLOWLIM " : "",
			(flags & SPD315_FLAG_UPPER_LIMIT) ? "FASTLIM " : "",
			(flags & SPD315_FLAG_LOWER_LIMIT_STOP) ? "SLOWLIM-STOP " : "",
			(flags & SPD315_FLAG_UPPER_LIMIT_STOP) ? "FASTLIM-STOP " : "");
		previous_flags = flags;
	}
}

SPD315::~SPD315()
{
	delete impl;
}

SPD315::SPD315(uint8_t instance_, uint8_t gpo_en_, uint8_t pwm_,
	uint8_t gpi_jog_inc_, uint8_t gpi_jog_dec_,
	uint8_t gpi_jog_en_, uint8_t jog_en_level_,
	uint32_t jog_interval_) :
	impl(new Impl(instance_, gpo_en_, pwm_,
		gpi_jog_inc_, gpi_jog_dec_,
		gpi_jog_en_, jog_en_level_,
		jog_interval_))
{
}

void SPD315::ChangeSpeed(int16_t speed_, uint32_t interval_)
{
	// Ignore non-change changes.
	if ((speed_ == impl->target_speed) || !interval_) {
		return;
	}

        // TODO: constrain interval to jog interval
        if (impl->jog_interval && (interval_ < impl->jog_interval)) {
                interval_ = impl->jog_interval;
        }

	// Constrain the target speed.
	if (speed_ < 0) {
		speed_ = 0;
	}
	if (speed_ > 255) {
		speed_ = 255;
	}

	// Save the new speed and interval.
	impl->target_speed = speed_;
	impl->interval = interval_;

	// Take action on the next tick.
	impl->wait_ticks = 0;

	// Indicate that we are moving.
	impl->flags |= SPD315_FLAG_CHANGING;

	DBG_MOTOR("SPD%u: Changing speed to %d at %u ticks/step", impl->instance, speed_, interval_);
}

void SPD315::HoldSpeed()
{
	// Set the target speed to the current speed.
	impl->target_speed = impl->current_speed;
	impl->interval = 0;
}

int16_t SPD315::GetSpeed()
{
	return impl->current_speed;
}

uint32_t SPD315::GetInterval()
{
	return impl->interval;
}

uint8_t SPD315::GetFlags()
{
	return impl->flags;
}

void SPD315::Log()
{
	impl->Log();
}

void SPD315::Work()
{
	impl->Work();
}
