
#include "rs485.h"

#include <debug.h>

#include <Arduino.h>

struct RS485::Impl
{
	// Implement main-class methods.
	~Impl();
	Impl(HardwareSerial* serial_, int dir_gpio_, uint32_t baud_);
	void Write(const uint8_t* data_, uint8_t bytes_);
	uint8_t Read(uint8_t* data_, uint8_t bytes_, uint32_t timeout_ms_);

	// The underlying serial interface.
	HardwareSerial* serial;

	// The GPIO direction output (HIGH = tx, LOW = rx).
	int dir_gpio;
};

RS485::Impl::~Impl()
{
	serial->end();
}

RS485::Impl::Impl(HardwareSerial* serial_, int dir_gpio_, uint32_t baud_) :
	serial(serial_),
	dir_gpio(dir_gpio_)
{
	ASSERT(serial_);

	// Initialize the direction pin as an output.
	pinMode(dir_gpio, OUTPUT);

	// Set the direction pin for receive.
	digitalWrite(dir_gpio, LOW);

	// Initialize for serial communication.
	serial->begin((long)baud_, SERIAL_8N1);
}

void RS485::Impl::Write(const uint8_t* data_, uint8_t bytes_)
{
	// No data supplied?
	if (!bytes_) {
		return;
	}
	ASSERT(data_);

	// Log the data.
	if (debug_is_relevant(DBGC_RS485)) {
		char line[1024];
		line[0] = '\0';
		for (uint8_t byte = 0; byte < bytes_; byte++) {
			char word[4];
			snprintf(word, 4, " %02X", data_[byte]);
			strncat(line, word, 4);
		}
		DBG_RS485("RS485: W%s", line);
	}

	// NOTE: we cannot generate any more debug output after we write the first
	// byte to the RS485 bus, or we may throw off the timing of the following read.

	// Set the direction pin for output.
	digitalWrite(dir_gpio, HIGH);

	delayMicroseconds(5);

	// Send the traffic.
	for (uint8_t byte = 0; byte < bytes_; byte++) {

		// Send the byte.
		serial->write(&data_[byte], 1);

		// Wait for the traffic to be sent.
		serial->flush();
	}

	delayMicroseconds(250);

	// Set the direction pin for input.
	digitalWrite(dir_gpio, LOW);
}

uint8_t RS485::Impl::Read(uint8_t* data_, uint8_t bytes_, uint32_t timeout_ms_)
{
	ASSERT(data_);

	// Set the read timeout.
	serial->setTimeout((int)timeout_ms_);

	// NOTE: we cannot generate any debug output before we read from the bus,
	// since we may be in a time-critical state following a preceding write.

	// Preset to no return.
	uint8_t bytes_read = 0;
	
	// Receive the traffic.
	bytes_read = (uint8_t)serial->readBytes(data_, (int)bytes_);

	// Log the data.
	if (debug_is_relevant(DBGC_RS485)) {
		char line[1024];
		line[0] = '\0';
		for (uint8_t byte = 0; byte < bytes_read; byte++) {
			char word[4];
			snprintf(word, 4, " %02X", data_[byte]);
			strncat(line, word, 4);
		}
		DBG_RS485("RS485: R%s", line);
	}

	// Return what we got.
	return bytes_read;
}

RS485::~RS485()
{
	delete impl;
}

RS485::RS485(HardwareSerial* serial_, int dir_gpio_, uint32_t baud_) :
	impl(new Impl(serial_, dir_gpio_, baud_))
{
}

void RS485::Write(const uint8_t* data_, uint8_t bytes_)
{
	impl->Write(data_, bytes_);
}

uint8_t RS485::Read(uint8_t* data_, uint8_t bytes_, uint32_t timeout_ms_)
{
	return impl->Read(data_, bytes_, timeout_ms_);
}

