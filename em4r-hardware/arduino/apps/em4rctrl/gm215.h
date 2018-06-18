#pragma once

#include <impl.h>
#include <debug.h>

#include <stdint.h>

// Axis identifiers, selected by DIP switch
#define	GM215_AXIS_X            0
#define	GM215_AXIS_Y            1
#define	GM215_AXIS_Z            2
#define	GM215_AXIS_W            3

// bits in the drive status word
#define	GM215_STATUS_IN1_BIT    0x8000U
#define	GM215_STATUS_IN2_BIT    0x4000U
#define	GM215_STATUS_IN3_BIT    0x2000U
#define	GM215_STATUS_ERR1_BIT   0x1000U
#define	GM215_STATUS_ERR2_BIT   0x0800U
#define	GM215_STATUS_BUSY_BIT   0x0400U
#define	GM215_STATUS_AXIS1_BIT  0x0200U
#define	GM215_STATUS_AXIS0_BIT  0x0100U
// Bit 7 is unused.
#define	GM215_STATUS_OUT3_BIT   0x0040U
#define	GM215_STATUS_OUT2_BIT   0x0020U
#define	GM215_STATUS_OUT1_BIT   0x0010U
// Bits 3..2 are unused.
#define	GM215_STATUS_GP1_BIT    0x0002U
#define	GM215_STATUS_GP0_BIT    0x0001U

// A complex of up to four GeckoDrive GM215 motion controllers.
// Controllers should be configured for edit mode, on the appropriate axes.
class RS485;
class GM215
{
public:
	~GM215();

	// Construct a GM215 instance.
	// bus_ is the RS485 bus instance to which the GM215 is connected.
	GM215(RS485* bus_);

	// Configure an axis.
	// axis_ is one of the GM215_AXIS_ constants.
	// tenths_of_amps_ sets the motor phase current (0..70).
	// idle_percentage_ sets the percentage of the phase current used during idle (0..100).
	// idle_tenths_of_seconds sets the time delay before going into idle (0..255).
	// Returns true on success; false on failure.
	bool Configure(uint8_t axis_, uint16_t tenths_of_amps_, uint8_t idle_percentage_, uint8_t idle_tenths_of_seconds_);

	// Set acceleration (0..32767).
	// axis_ is one of the GM215_AXIS_ constants.
	// Returns true on success; false on failure.
	bool Acceleration(uint8_t axis_, uint16_t accel_);

	// Set velocity (0..32767).
	// axis_ is one of the GM215_AXIS_ constants.
	// Returns true on success; false on failure.
	bool Velocity(uint8_t axis_, uint16_t vel_);

	// Set the clockwise step limit (0..0xFFFFFF).
	// axis_ is one of the GM215_AXIS_ constants.
	// Returns true on success; false on failure.
	bool LimitCW(uint8_t axis_, uint32_t steps_);

	// Move to the specified position (0..0xFFFFFF).
	// Movement may be constrained by limits.
	// axis_ is one of the GM215_AXIS_ constants.
	// wait_ is true to wait for completion; false to return immediately.
	// Returns true on success; false on failure (timeout).
	bool MoveAbsolute(uint8_t axis_, uint32_t step_, bool wait_);

	// Move by the specified number of steps (-8,388,607 (0xFF800000) .. +8,388,607 (0x007FFFFF)).
	// Movement may be constrained by limits.  + = CW, - = CCW.
	// axis_ is one of the GM215_AXIS_ constants.
	// step_ must be sign-extended to 32 bits.
	// wait_ is true to wait for completion; false to return immediately.
	// Returns true on success; false on failure.
	bool MoveRelative(uint8_t axis_, int32_t step_, bool wait_);

	// Reset motor position to 0x3FFFFF and CW limit to 0x3FFFFF + its existing value.
	// axis_ is one of the GM215_AXIS_ constants.
	// Returns true on success; false on failure.
	bool ResPos(uint8_t axis_);

	// Wait for completion of a deferred move.
	bool WaitReady(uint8_t axis_, uint32_t ms_);

	// information on one axis from a QUERY LONG operation.
	struct axis_status_t
	{
		// drive status (see GM215_STATUS_ bits)
		uint16_t status;

		// 16-bit program counter
		uint16_t pc;

		// analog input register
		int8_t ana;

		// 24-bit position, in signed 32-bit word
		int32_t pos;

		// 16-bit velocity
		uint16_t vel;
	};

	// the result of a QUERY LONG operation.
	struct query_result_t
	{
		// Bit 1 << X will be set if axes[X] contains valid data.
		uint8_t axes_valid;

		// Information on each axis.
		struct axis_status_t axis[4];
	};

	// Do a QUERY LONG operation.
	// If debug_ is non-zero, an interpretation will be logged under the specified debug code.
	// Returns true on success, with result_ set; false on failure.
	bool QueryLong(query_result_t& result_, debug_t debug_);

	// Do a QUERY SHORT operation.
	// If debug_ is non-zero, an interpretation will be logged under the specified debug code.
	// Returns true on success, with result_ set; false on failure.
	// Note that only the "axes_valid" field in result_, and the "status" and "pc" fields 
	// in the constituent axis_status_t structures, will be set, since this is a short query.
	bool QueryShort(query_result_t& result_, debug_t debug_);

	// Do a BULK ERASE operation.
	bool BulkErase(uint8_t axis_);

	// Pause.
	bool Pause(uint8_t axis);

	// Do a VERSION operation.
	void Version();

	DECLARE_IMPL(GM215)
};

