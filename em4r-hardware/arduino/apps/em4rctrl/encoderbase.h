#pragma once

#include <stdint.h>

struct encoder_control_capsule_data_t;
struct encoder_status_capsule_data_t;
class EncoderBase
{
public:
	// Generate an encoder status capsule.
	// Return true if one was added; false if one was not.
	virtual bool Status(uint8_t instance_, encoder_status_capsule_data_t& oscd_) = 0;

	// Get the number of encoders.
	virtual uint8_t EncoderCount() = 0;
};
