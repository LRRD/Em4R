#pragma once

#include <stdint.h>

struct operator_control_capsule_data_t;
struct operator_status_capsule_data_t;
class OperatorBase
{
public:
	// Process an operator control capsule.
	virtual void Control(uint8_t instance_, const operator_control_capsule_data_t& occd_) = 0;
	
	// Generate an operator status capsule.
	// Return true if one was added; false if one was not.
	virtual bool Status(uint8_t instance_, operator_status_capsule_data_t& oscd_) = 0;

	// Get the number of operators.
	virtual uint8_t OperatorCount() = 0;

	// Do a system initialization, whatever that entails for a particular system.
	// This will be done at initialization time, but can also be initiated remotely.
	virtual void Initialize();
};
