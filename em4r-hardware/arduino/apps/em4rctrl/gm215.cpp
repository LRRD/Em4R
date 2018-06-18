
#include "gm215.h"
#include "rs485.h"

#include <debug.h>

#include <stdio.h>
#include <string.h>

#include <Arduino.h>

// Special commands for edit mode.
#define	GM215_SPECIAL_E_STOP	    0x00
#define	GM215_SPECIAL_RUN	    0x04
#define	GM215_SPECIAL_PAUSE         0x02
#define	GM215_SPECIAL_QUERY_SHORT   0x07
#define	GM215_SPECIAL_QUERY_LONG    0x08
#define	GM215_SPECIAL_LOAD_PC       0x09
#define	GM215_SPECIAL_BULK_ERASE    0x0C
#define	GM215_SPECIAL_VERSION       0x0E

// Commands 
#define	GM215_CMD_MOVE_ABSOLUTE     0x00
#define	GM215_CMD_MOVE_RELATIVE     0x01
#define	GM215_CMD_VELOCITY          0x07
#define	GM215_CMD_ACCELERATION      0x0C
#define	GM215_CMD_CONFIG            0x0E
#define	GM215_CMD_LIMIT_CW          0x0F
#define	GM215_CMD_RESPOS            0x15

// The GM215 command execution timeout
#define	GM215_RUN_MS                5000

// The time we expect to wait for a QUERY SHORT response.
#define	GM215_SHORT_QUERY_MS        5

// The time we expect to wait for a QUERY LONG response.
#define	GM215_LONG_QUERY_MS         20

// Single-character name of each axis.
static const char axis_name[4] = { 'X', 'Y', 'Z', 'W' };

struct GM215::Impl
{
	// Implement main-class methods.
	~Impl();
	Impl(RS485* bus_);

	// The underlying RS-485 bus.
	RS485* bus;

	// Wait for the specific axis to be not busy.
	// axis_ is the axis number.
	// ms_ is how long to wait, in milliseconds.
	// Returns true on success; false on failure (timeout).
	bool Wait(uint8_t axis_, uint32_t ms_);

	// Issue a command using the RUN special command.
	// axis_ is the axis number.
	// buf_ points to a 4-byte command in wire order.
	// command_ is a description of the command, for use in error messages.
	// Returns true on success; false on failure.
	bool RunCommand(uint8_t axis_, uint8_t* buf_, const char* command_, bool wait_);

	// Issue the QUERY LONG special command and process its response.
	// Returns true on success; false on failure.
	bool QueryLong(GM215::query_result_t& response_, debug_t debug_);

	// Issue the QUERY SHORT special command and process its response.
	// Returns true on success; false on failure.
	bool QueryShort(GM215::query_result_t& response_, debug_t debug_);

	// Erase the flash.
	bool BulkErase(uint8_t axis_);

	// Issue the VERSION special command and process its response.
	// Note that this only appears to be useful if one Gecko is connected.
	void Version();
};

GM215::Impl::~Impl()
{
}

GM215::Impl::Impl(RS485* bus_) :
	bus(bus_)
{
}

bool GM215::Impl::Wait(uint8_t axis_, uint32_t ms_)
{
	// Keep track of the starting time.
	uint32_t start = 0;
	if (ms_) {
		start = (uint32_t)millis();
	}
	
	// Do a QUERY SHORT and wait for completion.
	query_result_t qr;
	do {
//		if (!QueryShort(qr, DBGC_GECKO)) {
		if (!QueryLong(qr, DBGC_GECKO)) {
			break;
		}

		// Exit once we have a valid report for this axis with the busy bit cleared.
		if ((qr.axes_valid & (1U << axis_)) && !(qr.axis[axis_].status & GM215_STATUS_BUSY_BIT)) {
			return true;
		}

		delay(50);

		// Run until we hit the timeout.
	} while (ms_ && (((uint32_t)millis() - start) < ms_));

	// Timeout.
	return false;
}

bool GM215::Impl::RunCommand(uint8_t axis_, uint8_t* buf_, const char* command_, bool wait_)
{
	int start;
	uint8_t special[4];

#if 0
	// Issue the LOAD PC special command.
	special[0] = GM215_SPECIAL_LOAD_PC;
	special[1] = 0;
	special[2] = 0;
	special[3] = 0;
	bus->Write(special, 2);

	// Wait.
	delay(4);
#endif

	// Issue the RUN special command.
	special[0] = GM215_SPECIAL_RUN;
	special[1] = 0;
	bus->Write(special, 2);

	// Wait 8 ms as per GUI application.
	delay(8);

	// Send the command bytes.
	bus->Write(buf_, 2);
	delay(4);
	bus->Write(buf_ + 2, 2);

	// Wait again.
	delay(4);

	if (wait_) {
		// Keep track of when we started waiting, and wait for completion.
		start = millis();
		if (!Wait(axis_, GM215_RUN_MS)) {
			DBG_GECKO("GM215 %c: RUN %02X%02X %02X%02X (%s) timed out",
				axis_name[axis_], buf_[1], buf_[0], buf_[3], buf_[2], command_, millis() - start);
			return false;
		}

		// Log how long the command took to complete.
		DBG_GECKO("GM215 %c: RUN %02X%02X %02X%02X (%s) completed in %u ms",
			axis_name[axis_], buf_[1], buf_[0], buf_[3], buf_[2], command_, millis() - start);
	}
	return true;
}

bool GM215::Impl::QueryLong(GM215::query_result_t& response_, debug_t debug_)
{
	// Read any crap.
	uint8_t junk;
	uint8_t crap = 0;
	while (bus->Read(&junk, 1, 0)) {
		crap++;
	}
	printf("QL: Turfed %u bytes\n", crap);

	digitalWrite(24, HIGH);
	delayMicroseconds(500);
	digitalWrite(24, LOW);

	// Issue the QUERY LONG special commmand.
	uint8_t special[2];
	special[0] = GM215_SPECIAL_QUERY_LONG;
	special[1] = 0;
	bus->Write(special, 2);

	// Read up to 60 bytes, 10 for each axis, plus any framing junk.
	// A 4-axis query reply should take 3.48 milliseconds (see GM215 manual).
	// The response is time-slot based; i.e., there will be gap between the X
	// and Z axis if the Y axis doesn't respond, but we should get a multiple
	// of 10 bytes in any event, and it should always complete within 5 ms.
	uint8_t buf[60];
	uint16_t bytes = bus->Read(buf, 60, GM215_LONG_QUERY_MS);

	// No response is an error.
	if (bytes == 0) {
		DBG_GECKO("GM215  : No response to QUERY LONG");
		return false;
	}

	// Skip framing bytes.
	uint8_t byte = 0;
	uint8_t skips = 0;
	while ((byte < bytes) && ((buf[byte] == 0xFF) || (buf[byte] == 0x00))) {
		byte++;
		skips++;
	}

	// Preset results.
	response_.axes_valid = 0;

	// Process as many axes as we got, in 10-byte chunks.
	for (; (byte + 9) < bytes; byte += 10) {
		uint8_t* s = &buf[byte];

		// Build the axis status.
		uint16_t status = ((uint16_t)s[0] << 8) | s[1];

		// Which axis is this?
		uint8_t axis =
			(((status & GM215_STATUS_AXIS1_BIT) != 0) << 1) |
			((status & GM215_STATUS_AXIS0_BIT) != 0);

		// Point to the axis data.
		GM215::axis_status_t* a = &response_.axis[axis];

		// Save the status word.
		a->status = status;

		// Convert the PC.
		a->pc = ((uint16_t)s[3] << 8) | s[2];

		// Convert the analog inputs.
		a->ana = s[4];

		// Convert the position and sign-extend.
		uint32_t pos = ((uint32_t)s[7] << 16) | ((uint32_t)s[6] << 8) | s[5];
		if (pos & 0x00800000U) {
			pos |= 0xFF000000U;
		}
		a->pos = pos;

		// Convert the velocity.
		a->vel = ((uint16_t)s[9] << 8) | s[8];

		// Mark this axis valid.
		response_.axes_valid |= (1U << axis);

		if (debug_) {
			debug_printf(debug_, "GM215 %c: STAT %04X, PC %04X, POS %06lX, VEL %04X",
				axis_name[axis], a->status, a->pc, a->pos, a->vel);
		}
	}

	if (skips != 1) {
		DBG_ERR("QL: Skipped %u framing bytes", skips);
	}

	// Success.
	return true;
}

bool GM215::Impl::QueryShort(GM215::query_result_t& response_, debug_t debug_)
{
	// Read any crap.
	uint8_t junk;
	uint8_t crap = 0;
	while (bus->Read(&junk, 1, 0)) {
		crap++;
	}
	printf("QS: Turfed %u bytes\n", crap);


	// Issue the QUERY SHORT special commmand.
	uint8_t special[2];
	special[0] = GM215_SPECIAL_QUERY_SHORT;
	special[1] = 0;
	bus->Write(special, 2);

	// Read up to 20 bytes.
	// A 4-axis query reply should take 0.87 milliseconds (see GM215 manual).
	// The response is time-slot based; i.e., there will be gap between the X
	// and Z axis if the Y axis doesn't respond, but we should get a multiple
	// of 10 bytes in any event, and it should always complete within 5 ms.
	uint8_t buf[20];
	uint8_t bytes = bus->Read(buf, 20, GM215_SHORT_QUERY_MS);

	// No response is an error.
	if (bytes == 0) {
		DBG_GECKO("GM215  : No response to QUERY SHORT");
		return false;
	}

	// Skip framing bytes.
	uint8_t byte = 0;
	uint8_t skips = 0;
	while ((byte < bytes) && ((buf[byte] == 0xFF) || (buf[byte] == 0x00))) {
		byte++;
		skips++;
	}

	// Preset results.
	response_.axes_valid = 0;

	// Process as many axes as we got.
	while (byte < bytes) {

		// We must have at least 2 bytes (the status word).
		if ((byte + 1) >= bytes) {
			break;
		}

		// The next thing must be a status byte.
		uint8_t* s = &buf[byte];

		// Build the axis status.
		uint16_t status = ((uint16_t)s[0] << 8) | s[1];

		// Which axis is this?
		uint8_t axis =
			(((status & GM215_STATUS_AXIS1_BIT) != 0) << 1) |
			((status & GM215_STATUS_AXIS0_BIT) != 0);

		// Point to the axis data.
		GM215::axis_status_t* a = &response_.axis[axis];

		// Save the status word.
		a->status = status;

		// If this is axis 0, we must have a PC address.
		if (axis == 0) {
			if ((byte + 3) >= bytes) {
				// Not enough bytes for the PC.
				break;
			}

			// Convert the PC.
			a->pc = ((uint16_t)s[3] << 8) | s[2];

			// Skip the PC.
			byte += 2;

			// Log output.
			if (debug_) {
				debug_printf(debug_, "GM215 %c: STAT %04X, PC %04X",
					axis_name[axis], a->status, a->pc);
			}
		}
		
		else {
			// Log output.
			if (debug_) {
				debug_printf(debug_, "GM215 %c: STAT %04X",
					axis_name[axis], a->status);
			}
		}

		// Mark this axis valid.
		response_.axes_valid |= (1U << axis);

		// Go on to the next status word.
		byte += 2;
	}

	if (skips) {
		DBG_ERR("QS: Skipped %u framing bytes\n", skips);
	}

	// Success.
	return true;
}

bool GM215::Impl::BulkErase(uint8_t axis_)
{
	uint8_t special[2];

	// Issue the BULK ERASE special command.
	special[0] = GM215_SPECIAL_BULK_ERASE;
	special[1] = 0;
	bus->Write(special, 2);

	// Wait.
	delay(5000);
	return true;
}


void GM215::Impl::Version()
{
	// Issue the VERSION special command.
	uint8_t special[2];
	special[0] = GM215_SPECIAL_VERSION;
	special[1] = 0;
	bus->Write(special, 2);
	
	// Read the reply, waiting up to 100 milliseconds.
	uint8_t response[16];
	uint8_t response_bytes = bus->Read(response, 16, 100);

	DBG_ERR("GM215 received %u bytes in response to VERSION request", response_bytes);
	if (response_bytes > 0) {
		char line[16 * 3];
		line[0] = '\0';
		for (uint16_t byte = 0; byte < response_bytes; byte++) {
			char word[4];
			snprintf(word, sizeof(word), "%02 ", response[byte]);
			strncat(line, word, sizeof(line));
		}
		DBG_ERR("%s", line);
	}
}

GM215::~GM215()
{
	delete impl;
}

GM215::GM215(RS485* bus_) :
	impl(new Impl(bus_))
{
}

bool GM215::Configure(uint8_t axis_, uint16_t tenths_of_amps_, uint8_t idle_percentage_, uint8_t idle_tenths_of_seconds_)
{
	ASSERT(axis_ < 4);
	ASSERT(tenths_of_amps_ <= 70U);
	ASSERT(idle_percentage_ <= 100U);

	// Format the command for debugging.
	char cmd[80];
	snprintf(cmd, sizeof(cmd), "CONFIGURE: %u.%u AMPS, IDLE AT %u%% AFTER %u.%u SECONDS",
		tenths_of_amps_ / 10, tenths_of_amps_ % 10,
		idle_percentage_,
		idle_tenths_of_seconds_ / 10, idle_tenths_of_seconds_ % 10);

	// Issue the CONFIGURE AXIS command.
	uint8_t buf[4];
	buf[0] = tenths_of_amps_ & 0x7F;
	buf[1] = (axis_ << 6) | GM215_CMD_CONFIG;
	buf[2] = idle_tenths_of_seconds_;
	buf[3] = idle_percentage_ & 0x7F;
	return impl->RunCommand(axis_, buf, cmd, true);
}

bool GM215::Acceleration(uint8_t axis_, uint16_t accel_)
{
	ASSERT(axis_ < 4);
	ASSERT(accel_ <= 0x7FFF);

	// Format the command for debugging.
	char cmd[80];
	snprintf(cmd, sizeof(cmd), "ACCELERATION %u", accel_);

	// Issue the ACCELERATION command.
	uint8_t buf[4];
	buf[0] = 0;
	buf[1] = (axis_ << 6) | GM215_CMD_ACCELERATION;
	buf[2] = accel_ & 0xFF;
	buf[3] = (accel_ >> 8) & 0xFF;
	return impl->RunCommand(axis_, buf, cmd, true);
}

bool GM215::Velocity(uint8_t axis_, uint16_t vel_)
{
	ASSERT(axis_ < 4);
	ASSERT(vel_ <= 0x7FFF);

	// Format the command for debugging.
	char cmd[80];
	snprintf(cmd, sizeof(cmd), "VELOCITY %u", vel_);

	// Issue the VELOCITY command.
	uint8_t buf[4];
	buf[0] = 0;
	buf[1] = (axis_ << 6) | GM215_CMD_VELOCITY;
	buf[2] = vel_ & 0xFF;
	buf[3] = (vel_ >> 8) & 0xFF;
	return impl->RunCommand(axis_, buf, cmd, true);
}

bool GM215::LimitCW(uint8_t axis_, uint32_t steps_)
{
	ASSERT(axis_ < 4);
	ASSERT(steps_ <= 0xFFFFFF);

	// Format the command for debugging.
	char cmd[80];
	snprintf(cmd, sizeof(cmd), "LIMIT CW %u", steps_);

	// Issue the LIMIT CW command.
	uint8_t buf[4];
	buf[0] = (steps_ >> 16) & 0xFF;
	buf[1] = (axis_ << 6) | GM215_CMD_LIMIT_CW;
	buf[2] = steps_ & 0xFF;
	buf[3] = (steps_ >> 8) & 0xFF;
	return impl->RunCommand(axis_, buf, cmd, true);
}

bool GM215::MoveAbsolute(uint8_t axis_, uint32_t step_, bool wait_)
{
	ASSERT(axis_ < 4);
	ASSERT(step_ <= 0xFFFFFF);

	// Format the command for debugging.
	char cmd[80];
	snprintf(cmd, sizeof(cmd), "MOVE %u", step_);

	// Issue the MOVE command in its absolute form.
	uint8_t buf[4];
	buf[0] = (step_ >> 16) & 0xFF;
	// Bit 5 is 0 to indicate no additional axes follow.
	buf[1] = (axis_ << 6) | GM215_CMD_MOVE_ABSOLUTE;
	buf[2] = step_ & 0xFF;
	buf[3] = (step_ >> 8) & 0xFF;
	DBG_ERR("MOVE: %02X%02X %02X%02X\n", buf[1], buf[0], buf[3], buf[2]);
	return impl->RunCommand(axis_, buf, cmd, wait_);
}

bool GM215::MoveRelative(uint8_t axis_, int32_t step_, bool wait_)
{
	ASSERT(axis_ < 4);
	ASSERT((step_ >= -0x7FFFFF) && (step_ < 0x7FFFFF));

	// Convert to sign-magnitude.
	uint32_t mag = ((step_ < 0) ? (uint32_t)-step_ : (uint32_t)step_) & 0x7FFFFF;

	// Format the command for debugging.
	char cmd[80];
	snprintf(cmd, sizeof(cmd), "MOVE %c%d", (step_ < 0) ? '-' : '+', mag);

	// Issue the MOVE command in its relative form.
	uint8_t buf[4];
	buf[0] = ((step_ >= 0) << 7) | ((mag >> 16) & 0x7F);
	// Bit 5 is 0 to indicate no additional axes follow.
	buf[1] = (axis_ << 6) | GM215_CMD_MOVE_RELATIVE;
	buf[2] = mag & 0xFF;
	buf[3] = (mag >> 8) & 0xFF;
	DBG_ERR("MOVE: %02X%02X %02X%02X\n", buf[1], buf[0], buf[3], buf[2]);
	return impl->RunCommand(axis_, buf, cmd, wait_);
}

bool GM215::ResPos(uint8_t axis_)
{
	ASSERT(axis_ < 4);

	// Format the command for debugging.
	char cmd[80];
	strncpy(cmd, "RESPOS", sizeof(cmd));

	// Issue the RESPOS command.
	uint8_t buf[4];
	// RESPOS can do any combination of axes, but we only do one at a time.
	buf[0] = (1U << axis_);
	buf[1] = GM215_CMD_RESPOS;
	buf[2] = 0;
	buf[3] = 0;
	return impl->RunCommand(axis_, buf, cmd, true);
}

bool GM215::Pause(uint8_t axis_)
{
	ASSERT(axis_ < 4);

	int start;

	// Issue the PAUSE special command.
	uint8_t special[2];
	special[0] = GM215_SPECIAL_PAUSE;
	special[1] = 0;
	impl->bus->Write(special, 2);

	// Log how long the command took to complete.
	DBG_GECKO("GM215 %c: PAUSE", axis_name[axis_]);
}

bool GM215::WaitReady(uint8_t axis_, uint32_t ms_)
{
	ASSERT(axis_ < 4);
	return impl->Wait(axis_, ms_);
}

bool GM215::QueryLong(query_result_t& result_, debug_t debug_)
{
	return impl->QueryLong(result_, debug_);
}

void GM215::Version()
{
	impl->Version();
}

bool GM215::BulkErase(uint8_t axis_)
{
	return impl->BulkErase(axis_);
}

