
#include "uptime.h"

#include <Arduino.h>

uptime_t uptime()
{
	// Get the millisecond counter.
	// TODO: deal with the millisecond counter wrapping.
	unsigned long ms = millis();

	// Convert to hours, minutes and seconds.
	struct uptime_t ut;
	ut.hours = ms / (60UL * 60UL * 1000UL);
	ms -= ut.hours * 60UL * 60UL * 1000UL;
	ut.minutes = ms / (60UL * 1000UL);
	ms -= ut.minutes * 60UL * 1000UL;
	ut.seconds = ms / 1000UL;
	ms -= ut.seconds * 1000UL;
	ut.milliseconds = ms;
	return ut;
}
