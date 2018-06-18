#pragma once

// A generic message header.
#define	SYSTEM_CONTROL_MAGIC		0x4543	// 'SC'
#define	SYSTEM_STATUS_MAGIC		0x4553	// 'SS'
#define	OPERATOR_CONTROL_MAGIC		0x5043	// 'OC'
#define	OPERATOR_STATUS_MAGIC		0x5053	// 'OS'
#define	ENCODER_STATUS_MAGIC		0x4553	// 'ES'

// System control commands.
#define	SYSTEM_CONTROL_CMD_NOP		0	// currently the only one
		
#define	OPERATOR_CONTROL_CMD_NOP	0	// Do nothing
#define	OPERATOR_CONTROL_CMD_MOVE	1	// Take time_to_achieve ms to move to requested_value (0 = ASAP).
#define	OPERATOR_CONTROL_CMD_STOP	2	// Stop moving, leaving operator at its current position.
#define	OPERATOR_CONTROL_CMD_RESET	3	// Reset the current position to requested_value without moving the motor.

#pragma pack(push, 1);

// the first word of any capsule
struct capsule_header_t
{
	// the number of bytes of data in this header, following the first.
	// by convention this must be a multiple of 4.
	uint8_t bytes_after;

	// for a message header, the version of the protocol used to generate this header
	uint8_t instance;

	// a magic number
	uint16_t magic;
};

// the data in a system status capsule, following the capsule header
struct system_status_capsule_data_t
{
	// an incrementing sequence number
	uint8_t seq;

	// the sequence number of the last control message received
	uint8_t rx_seq;

	// the 16-bit system ID
	uint16_t system_id;

	// The current time, in milliseconds.
	uint32_t ms;
};

// the data in a system control capsule, following the capsule header
struct system_control_capsule_data_t
{
	// an incrementing sequence number
	uint8_t seq;

	// the sequence number of the last status message received
	uint8_t rx_seq;

	// See SYSTEM_CONTROL_CMD_ constants.
	uint8_t command;

	// unused
	uint8_t dummy2;

	// the time, in milliseconds, at which this message was sent.
	// Ignored for now; may be used for time synchronization later.
	uint32_t ms;
};

// values for positioner_status_capsule_t::flags
#define OPERATOR_STATUS_FLAG_EQUIPPED		0x8000	// positioner exists
#define OPERATOR_STATUS_FLAG_FAULT_MOTOR	0x4000	// motor reporting a failure
#define OPERATOR_STATUS_FLAG_FAULT_MIN_LIMIT	0x2000	// hit minimum limit switch
#define OPERATOR_STATUS_FLAG_FAULT_MAX_LIMIT	0x1000	// hit maximum limit switch

// the data in an operator status capsule, following the capsule header
struct operator_status_capsule_data_t
{
	// the current position/value, in the smallest resolvable unit
	int16_t current_value;

	// flags (see OPERATOR_STATUS_FLAG_ constants.)
	uint16_t flags;

	// the requested value, in the smallest resolvable unit.
	// for positioners, an angle in tenths of degrees.
	// for pumps, a flow rate in tenths of gallons per minute.
	int16_t requested_value;

	// unused
	uint16_t dummy1;

	// the number of milliseconds in which requested_value should be achieved
	uint32_t time_to_achieve;
};

// the data in an operator control capsule, following the capsule header
struct operator_control_capsule_data_t
{
	// the requested value, in the smallest resolvable unit.
	// for positioners, an angle in tenths of degrees.
	// for pumps, a flow rate in tenths of gallons per minute.
	int16_t requested_value;

	// See OPERATOR_CONTROL_CMD_ constants.
	uint8_t command;

	// unused
	uint8_t dummy1;

	// the number of milliseconds in which which requested_value should be achieved
	uint32_t time_to_achieve;
};

// the data in an encoder status capsule, following the capsule header
struct encoder_status_capsule_data_t
{
	// the current position/value, in the smallest resolvable unit
	int16_t current_value;

	// value in millvolts (for debugging)
	uint16_t mv;
};

// A structure which can hold any control capsule (for sizing only).
struct generic_control_capsule_t
{
	// the capsule header
	// h.magic is SYSTEM_CONTROL_MAGIC, OPERATOR_CONTROL_MAGIC, etc.
	// For SYSTEM_CONTROL_MAGIC, h.instance is the protocol version.
	// For OPERATOR_CONTROL_MAGIC, h.instance is the zero-based index of the positioner.
	struct capsule_header_t cap;

	union {
		system_control_capsule_data_t sccd;
		operator_control_capsule_data_t occd;
	};
};

#pragma pack(pop)

