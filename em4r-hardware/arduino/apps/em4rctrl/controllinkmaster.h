#pragma once

#include <impl.h>

#include <stdint.h>

// The master end of the serial link to the driver subsystem.
class HardwareSerial;
class ControlLinkMaster
{
public:
	~ControlLinkMaster();

	// Construct.
	// serial_ is the serial port to use.
	// baud_ is the serial bit rate.
	// max_bytes_ is the maximum length of a transmit or receive message.
	ControlLinkMaster(HardwareSerial& serial_, uint32_t baud_, uint16_t max_bytes_);

	// Send a message and wait for it to be sent.
	// buf_ points to a buffer containing a message of bytes_ bytes.
	// bytes_ must be at least as large as the max_bytes_ value passed to the constructor.
	void Send(const uint8_t* buf_, uint16_t bytes_);

	// Process any pending serial input, and see if it constitutes a complete message.
	// buf_ points to a buffer at least bytes_ bytes in size.
	// bytes_ must be at least as large as the max_bytes_ value passed to the constructor.
	// If a full message is present, it is copied into buf_ and the number of copied bytes is returned.
	// If a full message is not present, nothing is copied to buf_ and 0 is returned.
	// Once a message has been copied, it is invalidated.
	uint16_t Receive(uint8_t* buf_, uint16_t bytes_);

	DECLARE_IMPL(ControlLinkMaster);
};
