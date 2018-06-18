
#include "em4r.h"
#include "config.h"
#include "ads1115.h"

#include <debug.h>

#include <Arduino.h>
#include <SD.h>
#include <SPI.h>

// The state of the debug LED.
bool led_on = false;
uint32_t loops = 0;

EM4R* model = nullptr;

void setup()
{
	// Make LED_BUILTIN an output.
	pinMode(LED_BUILTIN, OUTPUT);

	// Initialize the debug UART.
	debug_init(&Serial1);

	debug_set_mask(DBGC_ERR | DBGC_ENCODE);

	DBG_ERR("Little River Research & Design EM4R Control Software");
	DBG_ERR("Built " __TIME__ " " __DATE__);
	DBG_ERR("Configuration: "
#if CONFIG_NET
		"NET "
#endif
#if CONFIG_LINK
		"LINK "
#endif
#if CONFIG_SIM
		"SIM "
#endif
#if CONFIG_LOG
		"LOG "
#endif
#if CONFIG_ENCODERS
		"ENCODERS "
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

	// Initialize SPI for access to the SD card.
	// It's unclear why the digitalWrite is required, but it is.
	pinMode(10, OUTPUT);
	SPI.begin();
	digitalWrite(10, HIGH);

	delay(100);
	if (!SD.begin(4)) {
		DBG_ERR("SD card not found");
	} else {
		DBG_ERR("SD card found");
	}

	// Initialize the EM4R.
	model = new EM4R();
	
	loops = 0;
}

void loop()
{
#if CONFIG_ADCTEST
	// ADC test
	ADS1115* adcs[7];
	for (uint8_t adc = 0; adc < 7; adc++) {
		adcs[adc] = new ADS1115(ADS1115_I2C_ADDR_GND, TCA9548A_I2C_ADDR, adc, true, ADS1115_FSR_4V096, ADS1115_RATE_16_SPS);
	}
	for (;;) {
		for (uint8_t adc = 0; adc < 7; adc++) {
			uint32_t val;
			uint16_t raw;
			if (!adcs[adc]->Read(0, val, &raw)) {
				DBG_ERR("%u: fail", adc);
			} else {
				DBG_ERR("%u: %lu uV, 0x%04X", adc, val, raw);
			}
			delay(100);
		}
	}
#endif

	model->Work();

	// Don't need to run flat-out.
	delay(10);

	// Toggle the LED every 20 times through the main loop.
	if (++loops == 20) {
		led_on = !led_on;
		digitalWrite(LED_BUILTIN, led_on ? HIGH : LOW);
		loops = 0;

#if CONFIG_LOG
		model->Log();
#endif
	}
}
