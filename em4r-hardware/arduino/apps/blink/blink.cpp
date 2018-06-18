
#include <Arduino.h>

void setup()
{
	// Make LED_BUILTIN an output.
	pinMode(LED_BUILTIN, OUTPUT);
}

void loop()
{
	// Turn LED on.
	digitalWrite(LED_BUILTIN, HIGH);

	// Wait a while.
	delay(250);

	// Turn LED off.
	digitalWrite(LED_BUILTIN, LOW);

	// Wait a while.
	delay(250);
}
