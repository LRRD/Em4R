
#include "em4r.h"
#include "config.h"

#include <debug.h>

#include <Arduino.h>

void setup()
{
	// Make LED_BUILTIN an output.
	pinMode(LED_BUILTIN, OUTPUT);

	// Disable motors.
	digitalWrite(22, HIGH);
	digitalWrite(28, HIGH);
	digitalWrite(34, HIGH);
	pinMode(22, OUTPUT);
	pinMode(28, OUTPUT);
	pinMode(34, OUTPUT);

	// Set debug mask.
//	debug_set_mask(DBGC_ERR | DBGC_DRIVER | DBGC_MOTOR | DBGC_JOG);
	debug_set_mask(DBGC_ERR | DBGC_DRIVER | DBGC_JOG);

	// Initialize the debug UART.
	debug_init(&Serial1);
	DBG_ERR("Little River Research & Design EM4R Motor Driver");
	DBG_ERR("Built " __TIME__ " " __DATE__);
        DBG_ERR("Configuration: "
#if CONFIG_LINK
                "LINK "
#endif
#if CONFIG_LOG
                "LOG "
#endif
#if CONFIG_PITCH
		"PITCH "
#endif
#if CONFIG_ROLL
		"ROLL "
#endif
#if CONFIG_PIPE
		"PIPE "
#endif
#if CONFIG_PUMP
		"PUMP "
#endif
	);
}

void loop()
{
	uint32_t loops2000 = 0;

#if CONFIG_SWITCHTEST
	uint8_t pin[12] = { 26, 27, 32, 33, 38, 39, 40, 41, 42, 43, 44, 45 };

	char line1[100];
	char line2[100];
	char word[10];
	for (int x = 0; x < 12; x++) {
		pinMode(pin[x], INPUT_PULLUP);
	}
	for (;;) {
		bool in[12];
		for (int x = 0; x < 12; x++) {
			in[x] = digitalRead(pin[x]) != 0;
		}
		line1[0] = 0;
		line2[0] = 0;
		for (int x = 0; x < 12; x++) {
			sprintf(word, "%u  ", pin[x]);
			strcat(line1, word);
			sprintf(word, "%u   ", in[x] ? 1 : 0);
			strcat(line2, word);
		}
		DBG_ERR("%s", line1);
		DBG_ERR("%s", line2);
		DBG_ERR("");
		
		//delay(100);
	}
#endif

	// The state of the debug LED.
	bool led_on = false;

	// Instantiate the model.
	EM4R model;

	// Process every 250 usec.
	for (;;) {

		// Toggle the LED every 2000 loops (0.5 seconds).
		if (loops2000 == 0) {
			led_on = !led_on;
			digitalWrite(LED_BUILTIN, led_on ? HIGH : LOW);
		}
		if (++loops2000 == 2000) {
			loops2000 = 0;
		}

		// Save the current time.
		unsigned long start_micros = micros();

		// Service the model.
		model.Work();

		// Wait until 250usec has elapsed.
		while ((micros() - start_micros) < 250);
	}

#if 0
	for (uint8_t dir = 0; dir < 20; dir++) {

		// Turn the motor on.
		digitalWrite(22, LOW);
		delay(250);

		// Set direction.
		digitalWrite(23, dir & 1 ? HIGH : LOW);
		delay(250);

		for (uint32_t speed = 20; speed <= 20; speed++) {
			DBG_ERR("Speed = %lu", speed);

			// Toggle the LED once per main loop.
			led_on = !led_on;
			digitalWrite(LED_BUILTIN, led_on ? HIGH : LOW);

			uint32_t steps_at_speed = 0;
			uint32_t skip_steps = 0;

			while (steps_at_speed < 200) {
				if (skip_steps == 0) {
					digitalWrite(24, HIGH);
					digitalWrite(24, LOW);
					steps_at_speed++;
					skip_steps = 100 - speed;
					
				} else {
					skip_steps--;
				}

				// Do one cycle every 250us
				delayMicroseconds(250);
			}
		}

		// Shut down the motor.
		digitalWrite(22, HIGH);

		delay(1000);
		DBG_ERR("Hi");
	}
#endif
}
