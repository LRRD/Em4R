#pragma once

#include <impl.h>

#include <stdint.h>

class Preferences;
class OperatorBase;
class EncoderBase;
class UDPServer
{
public:
	~UDPServer();
	UDPServer(Preferences& prefs_, OperatorBase* operator_base_, EncoderBase* encoder_base_);

	// Get the number of milliseconds since the most recent peer became active.
	uint32_t ActiveMilliseconds();

	// Do periodic work.
	void Work();

	DECLARE_IMPL(UDPServer)
};
