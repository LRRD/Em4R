#pragma once

#include <stdint.h>

// System uptime timestamp
struct uptime_t
{
	uint32_t hours;
	uint8_t minutes;
	uint8_t seconds;
	uint32_t milliseconds;
};

// Get the current system uptime.
uptime_t uptime();
