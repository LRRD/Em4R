#pragma once

#include <impl.h>

#include <stdint.h>

// The number of USARTs.
#define	NUM_USARTS	4

// A serial link which transmits/receives one byte per call to its Work() method.
class SerialLink
{
public:
	~SerialLink();

	// Construct.
	// usart_ is the ordinal of the USART to use (0 .. NUM_USARTS-1).
	// baud_ is the serial bit rate.
	// max_bytes_ is the maximum length of a transmit or receive message.
	SerialLink(uint8_t usart_, uint32_t baud_, uint16_t max_bytes_);

	// See if a message can be sent.
	bool ClearToSend();

	// Submit a message for transmission, if possible.
	// buf_ points to a buffer containing a message of bytes_ bytes.
	// bytes_ must be at least as large as the max_bytes_ value passed to the constructor.
	// Returns true if the message was copied from buf_ and will be sent.
	// Returns false if a message was already being transmitted (try again later).
	bool Send(const uint8_t* buf_, uint16_t bytes_);

	// If a complete message has been received, copy it.
	// buf_ points to a buffer at least bytes_ bytes in size.
	// bytes_ must be at least as large as the max_bytes_ value passed to the constructor.
	// If a message is present, it is copied into buf_ and the number of copied bytes is returned.
	// If no message is present, nothing is copied to buf_ and 0 is returned.
	// Once a message has been copied, it is invalidated.
	uint16_t Receive(uint8_t* buf_, uint16_t bytes_);

	// Do periodic processing (receive a byte and/or transmit a byte).
	// After Work() has been called, Receive() must be called to check for a completed message;
	// if Work() is called with a completed message in hand, that message will be discarded.
	void Work();

	DECLARE_IMPL(SerialLink);
};
