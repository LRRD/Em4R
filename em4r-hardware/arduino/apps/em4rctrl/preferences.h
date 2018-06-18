#pragma once

#include <impl.h>

#include <stdint.h>

class Preferences
{
public:
	~Preferences();
	Preferences();

	// Get a preference string, with a default value.
	const char* Get(const char* name_, const char* default_);

	// Get an unsigned 16-bit integer preference.
	uint16_t GetUint16(const char* name_, uint16_t default_);

	// Get an IPv4 address.  ip_ and default_ point to a uint8_t[4].
	void GetIPAddress(uint8_t* ip_, const char* name_, const uint8_t* default_);

	// Get a MAC address.  ip_ and default_ point to a uint8_t[6].
	void GetMACAddress(uint8_t* mac_, const char* name_, const uint8_t* default_); 
	
	DECLARE_IMPL(Preferences);
};
