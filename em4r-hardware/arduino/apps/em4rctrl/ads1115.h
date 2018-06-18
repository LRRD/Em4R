#pragma once

#include <impl.h>

#include <stdint.h>

// slave addresses for the ADS1115 ADC
#define ADS1115_I2C_ADDR_GND        0x48
#define ADS1115_I2C_ADDR_VDD        0x49
#define ADS1115_I2C_ADDR_SDA        0x4A
#define ADS1115_I2C_ADDR_SCL        0x4B

// slave address for the TCA9548A MUX with all AD pins grounded
#define	TCA9548A_I2C_ADDR           0x70

// full-scale range choices
enum {
	ADS1115_FSR_6V144,
	ADS1115_FSR_4V096,
	ADS1115_FSR_2V048,
	ADS1115_FSR_1V024,
	ADS1115_FSR_0V512,
	ADS1115_FSR_0V256,
};

// conversion rate choices
enum {
	ADS1115_RATE_8_SPS,
	ADS1115_RATE_16_SPS,
	ADS1115_RATE_32_SPS,
	ADS1115_RATE_64_SPS,
	ADS1115_RATE_128_SPS,
	ADS1115_RATE_250_SPS,
	ADS1115_RATE_475_SPS,
	ADS1115_RATE_860_SPS,
};

// ADS1115 Analog-to-Digital Converter, in single-ended constant-conversion mode.
class ADS1115
{
public:
	~ADS1115();

	// Construct an ADS wrapper instance.
	// adc_slave_address_ is the 7-bit I2C address of the ADS1115.
	// If nonzero, mux_slave_address_ is the 7-bit I2C address of a TCS9548 multiplexer to which the ADC is connected.
	// If mux_slave_address_ is nonzero, mux_port_ is the port number (0..7) to which the ADC is connected.
	// cont_ is true for continuous conversion using input 0; false for one-shot conversion of any input.
	// fsr_ is the full-scale range of the programmable gain amplifier (see ADS1115_FSR_ enumeration).
	// sps_ is the conversion rate (see ADS1115_RATE_ enumeration).
	ADS1115(uint8_t adc_slave_address_, uint8_t mux_slave_address_, uint8_t mux_port_, bool cont_, int fsr_, int rate_);

	// Read a value in microvolts.
	// If the cont_ argument passed to the constructor was false, signal_ specifies the single-ended signal (0..3).
	// If the cont_ argument passed to the constructor was true, signal_ is ignored and the A0 input is always read.
	// Readings less than zero will return 0.
	// Returns true if val_ has been set; false on error (e.g., timeout).
	// If raw_ is non-NULL, on a successful return *raw_ is set to the 16-bit ADC value.
	bool Read(uint8_t signal_, uint32_t& val_, uint16_t* raw_ = nullptr);

	DECLARE_IMPL(ADS1115)
};

