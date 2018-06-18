
#include "physicalencoder.h"
#include "ads1115.h"
#include "debug.h"
#include "table.h"
#include "messages.h"

#include <Arduino.h>

PhysicalEncoder::~PhysicalEncoder()
{
}

PhysicalEncoder::PhysicalEncoder(const char* name_, const char* (*format_)(int16_t)) :
	name(name_),
	format(format_)
{
}

void PhysicalEncoder::Log()
{
	int16_t val;
	uint16_t mv;
	if (GetValuePU(val, &mv, nullptr)) {
		DBG_ENCODE("Encoder [%s] reporting %s (%u mV)", name, (*format)(val));
	} else {
		DBG_ENCODE("Encoder [%s] not reporting", name);
	}
}

struct TableMappedEncoder::Impl
{
	// Implement main-class methods.
	Impl(ADS1115* adc_, const Table* table_);
	bool GetValuePU(int16_t& val_, uint16_t* mv_, uint16_t* raw_);

	// The ADC from which we get our readings.
	ADS1115* adc;

	// The look-up table from millivolts to physical units.
	const Table* table;
};

TableMappedEncoder::Impl::Impl(ADS1115* adc_, const Table* table_) :
	adc(adc_),
	table(table_)
{
	ASSERT(adc);
	ASSERT(table);
}

bool TableMappedEncoder::Impl::GetValuePU(int16_t& val_, uint16_t* mv_, uint16_t* raw_)
{
	uint32_t uv;
	if (!adc->Read(0, uv, raw_)) {
		return false;
	}

	// Convert to millivolts.
	uint16_t mv = (uint16_t)((uv + 500UL) / 1000UL);
	if (mv_) {
		*mv_ = mv;
	}

	// Use the table to do the conversion.
	val_ = table->MapXToY((int16_t)mv);
	return true;
}

TableMappedEncoder::~TableMappedEncoder()
{
	delete impl;
}

TableMappedEncoder::TableMappedEncoder(const char* name_, const char* (*format_)(int16_t),
	ADS1115* adc_, const Table* table_) :
	PhysicalEncoder(name_, format_),
	impl(new Impl(adc_, table_))
{
}

bool TableMappedEncoder::GetValuePU(int16_t& val_, uint16_t* mv_, uint16_t* raw_)
{
	return impl->GetValuePU(val_, mv_, raw_);
}

bool TableMappedEncoder::Status(encoder_status_capsule_data_t& ecsd_)
{
        if (!GetValuePU(ecsd_.current_value, &ecsd_.mv)) {
		return false;
	}
//	DBG_ERR("Encoder [%s]: %s (%u mV)", name, (*format)(ecsd_.current_value), ecsd_.mv);
	return true;
}

struct MagneticFlowMeter::Impl
{
	// Implement main-class methods.
	Impl(uint8_t gpi_);
	bool GetValuePU(int16_t& val_, uint16_t* mv_, uint16_t* raw_);

	// The digital input pin used for PWM measurements.
	uint8_t gpi;
};

MagneticFlowMeter::Impl::Impl(uint8_t gpi_) :
	gpi(gpi_)
{
	// Set the specified pin as an input.
	pinMode(gpi, INPUT);
}

bool MagneticFlowMeter::Impl::GetValuePU(int16_t& val_, uint16_t* mv_, uint16_t* raw_)
{
	// NOTE: Based on code from Steve Gough, 2017/10/29, converted to integer math.
	// Read the pulse period in microseconds.
	// Timeout is currently 10ms; need to make sure this isn't too short.
	uint32_t low_usecs = pulseIn(gpi, LOW, 10000);
//	DBG_ERR("Flow: low for %lu usec", low_usecs);
	if (low_usecs == 0) {
		// No signal
		return false;
	}
	if (mv_) {
		*mv_ = (low_usecs > 0xFFFFUL) ? (uint16_t)0xFFFFU : (uint16_t)low_usecs;
	}
	if (raw_) {
		*raw_ = 0;
	}

	// Convert to frequency in Hz.
	uint32_t hz = 500000UL / low_usecs;

	// Apply the magic flow rate formula.
	uint32_t flow_rate = ((295 * hz) - 1700) / 100;

	// Convert to 16 bits and return.
	val_ = (flow_rate > 0x7FFF) ? (int16_t)0x7FFF : (int16_t)flow_rate;
	return true;
}

MagneticFlowMeter::~MagneticFlowMeter()
{
	delete impl;
}

MagneticFlowMeter::MagneticFlowMeter(const char* name_, const char* (*format_)(int16_t),
	uint8_t gpi_) :
	PhysicalEncoder(name_, format_),
	impl(new Impl(gpi_))
{
}

bool MagneticFlowMeter::GetValuePU(int16_t& val_, uint16_t* mv_, uint16_t* raw_)
{
	return impl->GetValuePU(val_, mv_, raw_);
}

bool MagneticFlowMeter::Status(encoder_status_capsule_data_t& ecsd_)
{
	uint16_t mv;
        if (!GetValuePU(ecsd_.current_value, &ecsd_.mv)) {
		return false;
	}
	DBG_ERR("Encoder [%s]: %s (%u mV)", name, (*format)(ecsd_.current_value), ecsd_.mv);
	return true;
}
