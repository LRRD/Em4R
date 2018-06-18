
#include "ads1115.h"

#include <debug.h>
#include <format.h>

#include <Arduino.h>
#include <Wire.h>

// ADS1115 register numbers.
#define	ADS_REG_CONVERSION		0
#define	ADS_REG_CONFIG			1
#define	ADS_REG_LO_THRESH		2
#define	ADS_REG_HI_THRESH		3

// ADS1115 control register.

// Operational status or single-shot conversion start (OS)
#define	ADS_CTRL_OS_SHIFT		15

// Input multiplexer configuration (MUX)
#define	ADS_CTRL_MUX_SHIFT		12
#define	ADS_CTRL_MUX_MASK		0x7
#define	ADS_CTRL_MUX_P_AIN0_N_AIN1	0
#define	ADS_CTRL_MUX_P_AIN0_N_AIN3	1
#define	ADS_CTRL_MUX_P_AIN1_N_AIN3	2
#define	ADS_CTRL_MUX_P_AIN2_N_AIN3	3
#define	ADS_CTRL_MUX_P_AIN0_N_GND	4
#define	ADS_CTRL_MUX_P_AIN1_N_GND	5
#define	ADS_CTRL_MUX_P_AIN2_N_GND	6
#define	ADS_CTRL_MUX_P_AIN3_N_GND	7

// Programmable gain amplifier configuration (PGA)
#define ADS_CTRL_PGA_SHIFT		9	
#define	ADS_CTRL_PGA_MASK		0x7
#define ADS_CTRL_PGA_FSR_6V144		0
#define ADS_CTRL_PGA_FSR_4V096		1
#define ADS_CTRL_PGA_FSR_2V048		2
#define ADS_CTRL_PGA_FSR_1V024		3
#define ADS_CTRL_PGA_FSR_0V512		4
#define ADS_CTRL_PGA_FSR_0V256		5
#define ADS_CTRL_PGA_FSR_0V256_2	6
#define ADS_CTRL_PGA_FSR_0V256_3	7

// Device operating mode (MODE)
#define	ADS_CTRL_MODE_SHIFT		8
#define	ADS_CTRL_MODE_CONTINUOUS	0
#define	ADS_CTRL_MODE_SINGLE_SHOT	1

// Data rate (DR)
#define	ADS_CTRL_DR_SHIFT		5
#define	ADS_CTRL_DR_8_SPS		0
#define	ADS_CTRL_DR_16_SPS		1
#define	ADS_CTRL_DR_32_SPS		2
#define	ADS_CTRL_DR_64_SPS		3
#define	ADS_CTRL_DR_128_SPS		4
#define	ADS_CTRL_DR_250_SPS		5
#define	ADS_CTRL_DR_475_SPS		6
#define	ADS_CTRL_DR_860_SPS		7

// Comparator mode (COMP_MODE)
#define	ADS_CTRL_COMP_MODE_SHIFT	4
#define	ADS_CTRL_COMP_MODE_TRADITIONAL	0
#define	ADS_CTRL_COMP_MODE_WINDOW	5

// Comparator polarity (COMP_POL)
#define	ADS_CTRL_COMP_POL_SHIFT		3
#define	ADS_CTRL_COMP_PIL_ACTIVE_LOW	0
#define	ADS_CTRL_COMP_PIL_ACTIVE_HIGH	0

// Latching comparator (COMP_LAT)
#define	ADS_CTRL_COMP_LAT_SHIFT		2
#define	ADS_CTRL_COMP_LAT_NONLATCHING	0
#define	ADS_CTRL_COMP_LAT_LATCHING	1

// Comparator queue and disable (COMP_QUE)
#define	ADS_CTRL_COMP_QUE_SHIFT		0
#define	ADS_CTRL_COMP_QUE_AFTER_1	0
#define	ADS_CTRL_COMP_QUE_AFTER_2	1
#define	ADS_CTRL_COMP_QUE_AFTER_4	2
#define	ADS_CTRL_COMP_QUE_DISABLE_HI_Z	3

struct ADS1115::Impl
{
	~Impl();

	// Implement main-class methods.
	Impl(uint8_t adc_slave_address_, uint8_t mux_slave_address_, uint8_t mux_port_, bool cont_, int fsr_, int rate_);
	bool Read(uint8_t signal_, uint32_t& val_, uint16_t* raw_);

	// 7-bit I2C slave address of the ADC.
	uint8_t adc_slave_address;

	// true in continuous conversion mode (A0 single ended input only).
	// false in one-shot conversion mode (signal_ argument to Read() selects input).
	bool cont;

	// if nonzero, 7-bit slave address of an intervening TCA9548 multiplexer.
	uint8_t mux_slave_address;

	// if mux_slave_address is nonzero, the port on the TCA9548 multiplexer.
	uint8_t mux_port;

	// current configuration register setting
	uint16_t configuration;

	// Select the correct I2C port, if required.
	void select();

	// Set a register.
	void write(uint8_t reg_, uint16_t val_);

	// Read a register.
	// Returns true on success; false on failure.
	bool read(uint8_t reg_, uint16_t& val_);
};

ADS1115::Impl::~Impl()
{
}

ADS1115::Impl::Impl(uint8_t adc_slave_address_, uint8_t mux_slave_address_, uint8_t mux_port_, bool cont_, int fsr_, int rate_) :
	adc_slave_address(adc_slave_address_),
	mux_slave_address(mux_slave_address_),
	mux_port(mux_port_),
	cont(cont_)
{
	if (mux_slave_address) {
		DBG_ADC("ADS1115: at mux 0x%02x:%u, address 0x%02X", mux_slave_address, mux_port, adc_slave_address);
	} else {
		DBG_ADC("ADS1115: at address 0x%02X", adc_slave_address);
	}

	// Select the device.
	select();

	// Set up the configuration register.
	configuration =
		(ADS_CTRL_MUX_P_AIN0_N_GND << ADS_CTRL_MUX_SHIFT) |
		(ADS_CTRL_MODE_CONTINUOUS << ADS_CTRL_MODE_SHIFT) |
		(ADS_CTRL_MODE_CONTINUOUS << ADS_CTRL_MODE_SHIFT) |
		(ADS_CTRL_COMP_MODE_TRADITIONAL << ADS_CTRL_COMP_MODE_SHIFT) | 
		(ADS_CTRL_COMP_PIL_ACTIVE_LOW << ADS_CTRL_COMP_POL_SHIFT) |
		(ADS_CTRL_COMP_LAT_NONLATCHING << ADS_CTRL_COMP_LAT_SHIFT) |
		(ADS_CTRL_COMP_QUE_DISABLE_HI_Z	<< ADS_CTRL_COMP_QUE_SHIFT);

	// fsr_ is the full-scale range.
	switch (fsr_) {
	case ADS1115_FSR_6V144:
		configuration |= (ADS_CTRL_PGA_FSR_6V144 << ADS_CTRL_PGA_SHIFT);
		break;
	case ADS1115_FSR_4V096:
		configuration |= (ADS_CTRL_PGA_FSR_4V096 << ADS_CTRL_PGA_SHIFT);
		break;
	case ADS1115_FSR_2V048:
		configuration |= (ADS_CTRL_PGA_FSR_2V048 << ADS_CTRL_PGA_SHIFT);
		break;
	case ADS1115_FSR_1V024:
		configuration |= (ADS_CTRL_PGA_FSR_1V024 << ADS_CTRL_PGA_SHIFT);
		break;
	case ADS1115_FSR_0V512:
		configuration |= (ADS_CTRL_PGA_FSR_0V512 << ADS_CTRL_PGA_SHIFT);
		break;
	case ADS1115_FSR_0V256:
		configuration |= (ADS_CTRL_PGA_FSR_0V256 << ADS_CTRL_PGA_SHIFT);
		break;
	}

	// rate_ is the conversion rate.
	switch (rate_) {
	case ADS1115_RATE_8_SPS:
		configuration |= (ADS_CTRL_DR_8_SPS < ADS_CTRL_DR_SHIFT);
		break;
	case ADS1115_RATE_16_SPS:
		configuration |= (ADS_CTRL_DR_16_SPS < ADS_CTRL_DR_SHIFT);
		break;
	case ADS1115_RATE_32_SPS:
		configuration |= (ADS_CTRL_DR_32_SPS < ADS_CTRL_DR_SHIFT);
		break;
	case ADS1115_RATE_64_SPS:
		configuration |= (ADS_CTRL_DR_64_SPS < ADS_CTRL_DR_SHIFT);
		break;
	case ADS1115_RATE_128_SPS:
		configuration |= (ADS_CTRL_DR_128_SPS < ADS_CTRL_DR_SHIFT);
		break;
	case ADS1115_RATE_250_SPS:
		configuration |= (ADS_CTRL_DR_250_SPS < ADS_CTRL_DR_SHIFT);
		break;
	case ADS1115_RATE_475_SPS:
		configuration |= (ADS_CTRL_DR_475_SPS < ADS_CTRL_DR_SHIFT);
		break;
	case ADS1115_RATE_860_SPS:
		configuration |= (ADS_CTRL_DR_860_SPS < ADS_CTRL_DR_SHIFT);
		break;
	}

	// Initialize the device.
	write(ADS_REG_CONFIG, configuration);
}

bool ADS1115::Impl::Read(uint8_t signal_, uint32_t& val_, uint16_t* raw_)
{
	// Do we need to select an input and do a manual conversion?
	if (!cont) {

		// Determine the multiplexor field value for the specified signal.
		uint8_t mux;
		switch(signal_) {
		case 0:
			mux = ADS_CTRL_MUX_P_AIN0_N_GND;
			break;
		case 1:
			mux = ADS_CTRL_MUX_P_AIN1_N_GND;
			break;
		case 2:
			mux = ADS_CTRL_MUX_P_AIN2_N_GND;
			break;
		case 3:
			mux = ADS_CTRL_MUX_P_AIN3_N_GND;
			break;
		default:
			return false;
		}

		// Set up the input multiplexer to select the specific input.
		configuration = (configuration & ~(ADS_CTRL_MUX_MASK << ADS_CTRL_MUX_SHIFT)) | (mux << ADS_CTRL_MUX_SHIFT);

		// Wait for the signal to stabilize?

		// Initiate a conversion.
		write(ADS_REG_CONFIG, configuration | (1U << ADS_CTRL_OS_SHIFT));

		uint16_t status = 0;
		for (;;) {
			// Read the status register.
			if (!read(ADS_REG_CONFIG, status)) {

				// No response.
				return false;
			}

			// Check the conversion-complete bit.
			if (status & (1U << ADS_CTRL_OS_SHIFT)) {
				break;
			}

			// Delay
			delay(5);
		}
	}

	// Read the raw result.
	uint16_t raw;
	if (!read(ADS_REG_CONVERSION, raw)) {
		return false;
	}

	// No negative results allowed; treat as 0.
	if (raw & 0x8000) {
		raw = 0;
	}

	switch ((configuration >> ADS_CTRL_PGA_SHIFT) & ADS_CTRL_PGA_MASK) {
	case ADS_CTRL_PGA_FSR_6V144:
		// 187.5 uV/LSB
		val_ = (((uint32_t)raw * 1875UL) + 5UL) / 10UL;
		break;
	case ADS_CTRL_PGA_FSR_4V096:
		// 125 uV/LSB
		val_ = ((uint32_t)raw * 125UL);
		break;
	case ADS_CTRL_PGA_FSR_2V048:
		// 62.5 uV/LSB
		val_ = (((uint32_t)raw * 625UL) + 5UL) / 10UL;
		break;
	case ADS_CTRL_PGA_FSR_1V024:
		// 31.25 uV/LSB
		val_ = (((uint32_t)raw * 3125UL) + 50UL) / 100UL;
		break;
	case ADS_CTRL_PGA_FSR_0V512:
		// 15.625 uV/LSB
		val_ = (((uint32_t)raw * 15625UL) + 500UL) / 1000UL;
		break;
	case ADS_CTRL_PGA_FSR_0V256:
	case ADS_CTRL_PGA_FSR_0V256_2:
	case ADS_CTRL_PGA_FSR_0V256_3:
		// 7.8125 uV/LSB
		// This won't wrap because the positive result is really only 15 bits.
		val_ = (((uint32_t)raw * 78125UL) + 5000UL) / 10000UL;
		break;
	}

	// Save the raw value.
	if (raw_) {
		*raw_ = raw;
	}
	return true;
}

void ADS1115::Impl::select()
{
	// If there is no multiplexer involved, we do not need to do anything.
	if (!mux_slave_address) {
		return;
	}

	// Start communication with the mux.
	Wire.beginTransmission(mux_slave_address);

	// Select the port.
	uint8_t sel = 1U << mux_port;
	Wire.write(sel);

	// Finish the transfer.
	Wire.endTransmission();
}

void ADS1115::Impl::write(uint8_t reg_, uint16_t val_)
{
	DBG_ADC("ADS1115 (%02X): %04X -> R%u", adc_slave_address, val_, reg_);

	// Select the I2C port, if required.
	select();

	// Start
	Wire.beginTransmission(adc_slave_address);

	// Write to the address pointer register to select the register to write.
	Wire.write(reg_);

	// Write the value, MSB first.
	Wire.write((val_ >> 8) & 0xFF);
	Wire.write(val_ & 0xFF);

	// Stop
	Wire.endTransmission();
}

bool ADS1115::Impl::read(uint8_t reg_, uint16_t& val_)
{
	// Select the I2C port, if required.
	select();

	// Start
	Wire.beginTransmission(adc_slave_address);

	// Write to the address pointer register to select the register to read.
	Wire.write(reg_);

	// Stop
	Wire.endTransmission();

	// Read the 2-byte register, sending a STOP afterwards.
	Wire.requestFrom((int)adc_slave_address, 2);
	if (2 <= Wire.available()) {
		val_ = (uint16_t)Wire.read() << 8;
		val_ |= Wire.read();
		DBG_ADC("ADS1115 (%02X): %04X <- R%u", adc_slave_address, val_, reg_);
		return true;
	}

	DBG_ERR("ADS1115 (%02X): no response to read", adc_slave_address);
	return false;
}

ADS1115::~ADS1115()
{
	delete impl;
}

ADS1115::ADS1115(uint8_t adc_slave_address_, uint8_t mux_slave_address_, uint8_t mux_port_, bool cont_, int fsr_, int rate_) :
	impl(new Impl(adc_slave_address_, mux_slave_address_, mux_port_, cont_, fsr_, rate_))
{
}

bool ADS1115::Read(uint8_t signal_, uint32_t& val_, uint16_t* raw_)
{
	return impl->Read(signal_, val_, raw_);
}

