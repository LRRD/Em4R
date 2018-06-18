
#include "em4r.h"
#include "sd6575.h"
#include "spd315.h"
#include "seriallink.h"
#include "config.h"

#include <debug.h>
#include <crc.h>

#define	MAX_LINK_PAYLOAD_BYTES		32U
#define	COMMAND_BYTES_PER_OPERATOR	5U	// [4/AXIS, 4/CMD] TGTH TGTL INTH INTL
#define	STATUS_BYTES_PER_OPERATOR	3U	// FLAGS POSH POSL
#define	STEPPER_COUNT			3U	// number of stepper motors
#define	SPEEDER_COUNT			1U	// number of speed-controlled motors

struct EM4R::Impl
{
	// Implement main-class functions.
	~Impl();
	Impl();
	void Work();

	// The serial connection to the control processor.
	SerialLink* link;

	// The stepper motor drivers.
	// 0 = pitch, 1 = roll, 2 = standpipe.
	SD6575* stepper[3];

	// The speed-control drivers.
	SPD315* speeder[1];

	// The loop counts, used for maintenance.
	uint32_t loop1000;
	uint32_t loop4000;

#if CONFIG_LINK
	// Handle a command message, if there is one.
	void ProcessCommand();

	// Report status, if the link is ready for it.
	void ReportStatus();
#endif
};

EM4R::Impl::~Impl()
{
	delete link;
	for (uint8_t op = 0; op < STEPPER_COUNT; op++) {
		delete stepper[op];
	}
	for (uint8_t op = 0; op < SPEEDER_COUNT; op++) {
		delete speeder[op];
	}
}

EM4R::Impl::Impl() :
	loop1000(0),
	loop4000(0)
{
	link = nullptr;
	for (uint8_t op = 0; op < STEPPER_COUNT; op++) {
		stepper[op] = nullptr;
	}
	for (uint8_t op = 0; op < SPEEDER_COUNT; op++) {
		speeder[op] = nullptr;
	}

#if CONFIG_LINK
	// USART 2, 100kbps, 32 byte maximum message payload
	link = new SerialLink(2U, 100000U, 32U);
#endif

#if CONFIG_PITCH
	// Pitch stepper
	// position increases with CW rotation
	// Instance 0, EN = 22, DIR = 23, STEP = 24, FAULT = 25, MINLIM = 26, MAXLIM = 27
	// JOG+ = 41, JOG- = 40, when pin 44 = 0.  Jog at 1 step/msec
	stepper[0] = new SD6575(0, false, 22, 23, 24, 25, 26, 27, 41, 40, 44, 0, 4);
#endif

#if CONFIG_ROLL
	// Roll stepper
	// position increases with CW rotation
	// Instance 1, EN = 28, DIR = 29, STEP = 30, FAULT = 31, MINLIM = 32, MAXLIM = 33
	// JOG+ = 42, JOG- = 43, when pin 44 = 0.  Jog at 1 step/msec
	stepper[1] = new SD6575(1, false, 28, 29, 30, 31, 32, 33, 42, 43, 44, 0, 4);
#endif

#if CONFIG_PIPE
	// Downstream  standpipe
	// position increases with CW rotation
	// Instance 2, EN = 34, DIR = 35, STEP = 36, FAULT = 37, MINLIM = 38, MAXLIM = 39
	// JOG+ = 41, JOG- = 40, when pin 44 = 1.  Jog at 1 step/msec
	stepper[2] = new SD6575(2, true, 34, 35, 36, 37, 38, 39, 41, 40, 44, 1, 4);
#endif

#if CONFIG_PUMP
	// Pump
	// Instance 3, EN = 2, PWM = 3, no jog
	// JOG+ = 42, JOG- = 43, when pin 44 = 1.  Jog at 1 step/10 msec
	speeder[0] = new SPD315(3, 2, 3, 42, 43, 44, 1, 40);
#endif
}

#if CONFIG_LINK
void EM4R::Impl::ProcessCommand()
{
	// See if we have a command to process.
	uint8_t msg[MAX_LINK_PAYLOAD_BYTES];
	uint8_t rx = link->Receive(msg, MAX_LINK_PAYLOAD_BYTES);
	if (!rx) {
		return;
	}

	// Check the checksum.
	uint8_t sum = msg[rx - 1];
	uint8_t calc = crc8(msg, rx - 1);
	if (sum != calc) {
		// Checksum error.
		DBG_LINK_ERR("Bad message checksum");
		return;
	}
	rx--;

	uint8_t op = (msg[0] >> 4) & 0x0F;
	uint8_t cmd = msg[0] & 0x0F;
	if ((op < STEPPER_COUNT) && stepper[op]) {
		switch (cmd) {
		case 1:
			// Move.
			if (rx == 5) {
				int16_t target = (int16_t)(((uint16_t)msg[1] << 8) | msg[2]);
				uint16_t interval = ((uint16_t)msg[3] << 8) | msg[4];
				DBG_MOTOR("Telling operator %u to move to %d (interval %u)", op, target, interval);
				stepper[op]->Move(target, interval);
			}
			break;
		case 2:
			// Stop.
			DBG_MOTOR("Stopping operator %u", op);
			stepper[op]->Stop();
			break;
		case 3:
			// Reset position.
			if (rx == 3) {
				int16_t pos = (int16_t)(((uint16_t)msg[1] << 8) | msg[2]);
				DBG_MOTOR("Resetting operator %u position to step %d", op, pos);
				stepper[op]->ResetPosition(pos);
			}
			break;
		}
	} else if (((op - STEPPER_COUNT) < SPEEDER_COUNT) && speeder[op - STEPPER_COUNT]) {
		switch (cmd) {
		case 1:
			// Change position.
			{
				int16_t target = (int16_t)(((uint16_t)msg[1] << 8) | msg[2]);
				uint16_t interval = ((uint16_t)msg[3] << 8) | msg[4];
				DBG_MOTOR("Telling operator %u to change speed to %d (interval %u)", op, target, interval);
				speeder[op - STEPPER_COUNT]->ChangeSpeed(target, interval);
			}
			break;
		case 2:
			// Hold at current level.
			DBG_MOTOR("Telling operator %u to hold speed");
			speeder[op - STEPPER_COUNT]->HoldSpeed();
			break;
		}
	}
}

void EM4R::Impl::ReportStatus()
{
	// See if we can report status.
	if (!link->ClearToSend()) {
		return;
	}

	uint8_t status[MAX_LINK_PAYLOAD_BYTES];
	uint8_t* next = status;

	// Report stepper status.
	for (uint8_t op = 0; op < STEPPER_COUNT; op++) {
		if (!stepper[op]) {
			continue;
		}

		// Write operator.
		*next++ = op;

		// Write flag byte.
		*next++ = stepper[op]->GetFlags();

		// Write position word.
		int16_t pos = stepper[op]->GetPos();
		*next++ = (uint8_t)((pos >> 8) & 0xFF);
		*next++ = (uint8_t)(pos & 0xFF);
	}

	// Report pump status.
	for (uint8_t op = 0; op < SPEEDER_COUNT; op++) {
		if (!speeder[op]) {
			continue;
		}

		// Write operator.
		*next++ = STEPPER_COUNT + op;

		// Write flag byte.
		*next++ = speeder[op]->GetFlags();

		// Write speed word.
		int16_t pos = speeder[op]->GetSpeed();
		*next++ = (uint8_t)((pos >> 8) & 0xFF);
		*next++ = (uint8_t)(pos & 0xFF);
	}

	// Compute and append the checksum.
	if ((next - status) < 1) {
		return;
	}
	uint8_t crc = crc8(status, next - status);
	*next++ = crc;

	// Send the message.
	link->Send(status, next - status);
}
#endif	// CONFIG_LINK

void EM4R::Impl::Work()
{
	// Service the stepper.
	for (uint8_t op = 0; op < STEPPER_COUNT; op++) {
		if (stepper[op]) {
			stepper[op]->Work();
		}
	}

	// Service the speed-controlled motors.
	for (uint8_t op = 0; op < SPEEDER_COUNT; op++) {
		if (speeder[op]) {
			speeder[op]->Work();
		}
	}

#if CONFIG_LINK
	// Service the serial link.
	link->Work();
	ProcessCommand();
#endif

	// Every 1000 loops (250 milliseconds), report status.
	if (++loop1000 == 1000) {
#if CONFIG_LINK
		ReportStatus();
#endif
		loop1000 = 0;
	}

#if CONFIG_LOG
	// Every 4000 milliseconds (1 second), report status.
	if (++loop4000 == 4000) {
		for (uint8_t op = 0; op < STEPPER_COUNT; op++) {
			if (stepper[op]) {
				stepper[op]->Log();
			}
		}
		for (uint8_t op = 0; op < SPEEDER_COUNT; op++) {
			if (speeder[op]) {
				speeder[op]->Log();
			}
		}

		loop4000 = 0;
	}
#endif
}

EM4R::~EM4R()
{
	delete impl;
}

EM4R::EM4R() : impl(new Impl)
{
}

void EM4R::Work()
{
	impl->Work();
}
