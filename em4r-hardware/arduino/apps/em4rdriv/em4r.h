#pragma once

#include <impl.h>

// Robotic EM4R model motors
class EM4R
{
public:
	~EM4R();
	EM4R();

	// Do periodic work.
	void Work();
	
	DECLARE_IMPL(EM4R);
};
