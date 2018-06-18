#pragma once

#include <impl.h>

#include <stdint.h>

class ADS1115;
class Table;

// Virtual base class (interface) for all encoders that report physical units.
class encoder_status_capsule_data_t;
class PhysicalEncoder
{
public:
	virtual ~PhysicalEncoder();
	PhysicalEncoder(const char* name_, const char* (*format_)(int16_t));

	// Get the current value in physical units.
	virtual bool GetValuePU(int16_t& val_, uint16_t* mv_ = nullptr, uint16_t* raw_ = nullptr) = 0;

	// Log information to debug console.
	virtual void Log();

	// Report status.
	virtual bool Status(encoder_status_capsule_data_t& ocsd_) = 0;

protected:
	// The instance name for logging.
	const char* name;

        // The function which formats a signed value in the native units.
        const char* (*format)(int16_t);
};

// A linear encoder connected to an ADS1115 ADC, which uses a calibration table to map encoder voltage to physical units.
class TableMappedEncoder : public PhysicalEncoder
{
public:
	virtual ~TableMappedEncoder();

	// Construct with access to an ADC, and a look-up table.
	// table_ maps millivolts to physical units.
        // format_ formats a signed value in physical units.
	TableMappedEncoder(const char* name_, const char* (*format_)(int16_t), ADS1115* adc_, const Table* table_);

	// PhysicalEncoder overrides.
	virtual bool GetValuePU(int16_t& val_, uint16_t* mv_ = nullptr, uint16_t* raw_ = nullptr) override;
	virtual bool Status(encoder_status_capsule_data_t& ocsd_) override;

	DECLARE_IMPL(TableMappedEncoder);
};

// A PWM-input magnetic flow meter.
class MagneticFlowMeter : public PhysicalEncoder
{
public:
	~MagneticFlowMeter();

	// Construct with the input pin used to measure the PWM signal from the flow meter.
	MagneticFlowMeter(const char* name_, const char* (*format_)(int16_t), uint8_t gpi_);

	// PhysicalEncoder overrides.
	virtual bool GetValuePU(int16_t& val_, uint16_t* mv_ = nullptr, uint16_t* raw_ = nullptr) override;
	virtual bool Status(encoder_status_capsule_data_t& ocsd_) override;

	DECLARE_IMPL(MagneticFlowMeter);
};
