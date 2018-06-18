
#include "controllinkmaster.h"

#include <debug.h>

#include <Arduino.h>

// The low 8 bits of characters in which bit 8 is set.
#define	STX		0x02
#define	ETX		0x03

struct ControlLinkMaster::Impl
{
	// Implement main-class methods.
	~Impl();
	Impl(HardwareSerial& serial_, uint32_t baud_, uint16_t max_bytes_);
	void Send(const uint8_t* buf_, uint16_t bytes_);
	uint16_t Receive(uint8_t* buf_, uint16_t bytes_);

	// Handle receive data up to the next complete message.
	void ProcessReceiveData();

	// The serial port.
	HardwareSerial& serial;

	// The maximum message size for receive.
	uint16_t max_bytes;
	
	// The receive buffer.
	uint8_t* rx_buf;

	// The number of valid payload bytes in the receive buffer.
	uint16_t rx_valid_bytes;

	// Set if a start-of-message has been received (message being built).
	uint8_t rx_in_progress;

	// Set if a complete message payload is present in the receive buffer.
	bool rx_complete;

	// A debug buffer.
	char* debug_text;
};

ControlLinkMaster::Impl::~Impl()
{
	serial.end();
}

ControlLinkMaster::Impl::Impl(HardwareSerial& serial_, uint32_t baud_, uint16_t max_bytes_) :
	serial(serial_),
	max_bytes(max_bytes_),
	rx_buf(nullptr),
	rx_in_progress(0),
	rx_complete(false),
	debug_text(new char[10 + (max_bytes_ * 3)])
{
	// Allocate the receive buffer.
	rx_buf = new uint8_t[max_bytes];

	// Set up the serial port for 1 stop bit, 9 data bits, no parity.
	// (Note that this requires the 9-bit patch to the Arduino library).
	serial_.begin(baud_, SERIAL_9N1);
}

void ControlLinkMaster::Impl::Send(const uint8_t* buf_, uint16_t bytes_)
{
	// Just eat empty messages.
	if (!bytes_) {
		return;
	}

	// Flush any serial data.
	serial.flush();

	// Send STX.
	serial.write(0x100 | STX);
	delayMicroseconds(500);
	serial.flush();

	// Send the user data.
	for (uint16_t byte = 0; byte < bytes_; byte++) {
		serial.write(buf_[byte]);
		serial.flush();
		delayMicroseconds(500);
	}

	// Send ETX.
	serial.write(0x100 | ETX);
	serial.flush();
	delayMicroseconds(500);

#if 1
	// Output the message for debugging.
	debug_text[0] = 0;
	char byte_text[4];
	for (uint16_t byte = 0; byte < bytes_; byte++) {
		snprintf(byte_text, sizeof(byte_text), "%02X ", buf_[byte]);
		strcat(debug_text, byte_text);
	}
	DBG_ERR("TX %s", debug_text);
#endif
}

uint16_t ControlLinkMaster::Impl::Receive(uint8_t* buf_, uint16_t bytes_)
{
	// Process any receive data.
	ProcessReceiveData();

	// If there is no complete message, we cannot return anything.
	if (!rx_complete) {
		return 0;
	}

	// Copy the message into the supplied buffer.
	ASSERT(bytes_ <= max_bytes);
	if (bytes_ > rx_valid_bytes) {
		bytes_ = rx_valid_bytes;
	}
	memcpy(buf_, rx_buf, bytes_);

	debug_text[0] = 0;
	char byte_text[4];
	for (uint16_t byte = 0; byte < bytes_; byte++) {
		snprintf(byte_text, sizeof(byte_text), "%02X ", buf_[byte]);
		strcat(debug_text, byte_text);
	}
//	DBG_ERR("RX %s", debug_text);

	// Set up to receive.
	rx_valid_bytes = 0;
	rx_complete = false;

	// Return the number of bytes we copied.
	return bytes_;
}

void ControlLinkMaster::Impl::ProcessReceiveData()
{
	for (;;) {
		int c = serial.read();
		if (c < 0) {
			break;
		}

		// Control character?
		if (c & 0x100) {
			//DBG_ERR("RX *%02X", c & 0xFF);
			switch (c & 0xFF) {
			case STX:
				// Start a new message.
				rx_complete = false;
				rx_valid_bytes = 0;
				rx_in_progress = 1;
				break;

			case ETX:
				// Complete a message in progress.
				if (rx_in_progress) {
					rx_complete = true;
				}
				break;
			
			default:
				// Unknown control character.
				break;
			}
		}

		// Regular character?
		else if (rx_in_progress) {
			//DBG_ERR("RX  %02X", c & 0xFF);
			if (rx_valid_bytes < max_bytes) {
				rx_buf[rx_valid_bytes++] = c & 0xFF;
			}
		}
	}
}

ControlLinkMaster::~ControlLinkMaster()
{
	delete impl;
}

ControlLinkMaster::ControlLinkMaster(HardwareSerial& serial_, uint32_t baud_, uint16_t max_bytes_) :
	impl(new Impl(serial_, baud_, max_bytes_))
{
}

void ControlLinkMaster::Send(const uint8_t* buf_, uint16_t bytes_)
{
	impl->Send(buf_, bytes_);
}

uint16_t ControlLinkMaster::Receive(uint8_t* buf_, uint16_t bytes_)
{
	return impl->Receive(buf_, bytes_);
}
