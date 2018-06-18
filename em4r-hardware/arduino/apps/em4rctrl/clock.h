#pragma once

#include <stdint.h>

class Clock
{
public:
	virtual uint32_t Milliseconds() = 0;
};
