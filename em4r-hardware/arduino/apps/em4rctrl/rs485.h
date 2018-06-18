#pragma once

#include <impl.h>

#include <stdint.h>

class HardwareSerial;
class RS485
{
public:
	~RS485();

	// Construct an RS-485 half duplex interface.
	// serial_ points to the serial interface connected to the transceiver.
	// dir_gpio_ is the number of the digital pin connected to the direction
	// line on the transceiver.  The sense is assumed to be DE/DI#, as per
	// the MAX485, so this output is HIGH for transmit, LOW for receive.
	// baud_ is the data rate in bits per second.
	RS485(HardwareSerial* serial_, int dir_gpio_, uint32_t baud_);

	// Write a byte sequence to the bus.
	void Write(const uint8_t* data_, uint8_t bytes_);

	// Read a byte sequence from the bus.
	// data_ points to a buffer with space for at least bytes_ bytes.
	// bytes_ is the maximum number of bytes to read.
	// timeout_ms_ is the number of milliseconds after which to return no matter what.
	// Returns the number of bytes actually read (guaranteed <= bytes_).
	uint8_t Read(uint8_t* data_, uint8_t bytes_, uint32_t timeout_ms_);

	DECLARE_IMPL(RS485)
};
