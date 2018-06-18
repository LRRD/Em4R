
#include "debug.h"
#include "uptime.h"

#include <Arduino.h>

#define __GNU_SOURCE
#include <stdio.h>

static debug_t debug_mask = DBG_COMPILE;

// The device to which we are logging.
static HardwareSerial* serial = nullptr;

void debug_hang()
{
	// Make the LED flash in a distinctive pattern.
	for (;;) {
		for (uint8_t loop = 0; loop < 3; loop++) {
			digitalWrite(LED_BUILTIN, HIGH);
			delay(50);
			digitalWrite(LED_BUILTIN, LOW);
			delay(50);
		}
		delay(500);
	}
}

void debug_init(HardwareSerial* serial_)
{
	serial = serial_;
	if (serial) {
		serial->begin(115200, SERIAL_8N1);
	}
}

void debug_set_mask(debug_t mask_)
{
	debug_mask = mask_;
}

bool debug_is_relevant(debug_t code_)
{
	// The code is relevant if we have a console, and the specified debug bit is set in the debug mask.
	return serial && (debug_mask & code_);
}

static char timestamp[40];
static char text[100];

void debug_printf(debug_t code_, const char* format_, ...)
{
//	char timestamp[40];
//	char text[100];

        // Don't bother formatting the output if we aren't going to do anything with it.
        if (!debug_is_relevant(code_)) {
                return;
        }

        // Process the variable argument list.
        va_list args;
        va_start(args, format_);

        // Format the message.
        if (0 > vsnprintf(text, sizeof(text), format_, args)) {
                // Could not format; just ignore.
                return;
        }

	// Format the timestamp.
	uptime_t ut = uptime();
	snprintf(timestamp, sizeof(timestamp), "%4lu:%02u:%02u.%03lu ", ut.hours, ut.minutes, ut.seconds, ut.milliseconds);
//	snprintf(timestamp, sizeof(timestamp), "%u %u %u %u %u ", (int)millis(), (int)ut.hours, (int)ut.minutes, (int)ut.seconds, (int)ut.milliseconds);

	// Output the timestamp.
	serial->print(timestamp);

        // Output the message with an appended newline.
        serial->println(text);
	
	// Flush console output.
	serial->flush();
}
