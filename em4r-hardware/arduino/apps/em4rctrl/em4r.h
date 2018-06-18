#pragma once

#include "config.h"

#include <impl.h>

// One EM4R model
class EM4R
{
public:
	~EM4R();
	EM4R();

#if CONFIG_LOG
	// Log state.
	void Log();
#endif

	// Process.
	void Work();
	
	DECLARE_IMPL(EM4R)
};
