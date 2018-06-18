
#include "em4r.h"
#include "operatorbase.h"
#include "encoderbase.h"
#include "ads1115.h"
#include "rs485.h"
#include "gm215.h"
#include "preferences.h"
#include "udpserver.h"
#include "clock.h"
#include "messages.h"
#include "operator.h"
#include "table.h"
#include "physicalencoder.h"
#include "controllinkmaster.h"
#include "messages.h"
#include "config.h"

#include <debug.h>
#include <crc.h>

#include <Arduino.h>
#include <Wire.h>
#include <Ethernet.h>
#include <IPAddress.h>

// Slave addresses for the ADS1115 ADC.
#define	ADC_I2C_ADDR_GND	0x48
#define	ADC_I2C_ADDR_VDD	0x49
#define	ADC_I2C_ADDR_SDA	0x4A
#define	ADC_I2C_ADDR_SCL	0x4B

// Operator indices.
#define	OPERATOR_PITCH		0
#define	OPERATOR_ROLL		1
#define	OPERATOR_PIPE		2
#define	OPERATOR_PUMP		3
#define	OPERATOR_COUNT		4

// "Stopped by maximum limit switch" flag, used for calibration (see driver).
#define SD6575_FLAG_MAX_LIMIT_STOP      0x01

// Encoder indices (each corresponds to an ADC).
#define	ENCODER_PITCH		0
#define	ENCODER_ROLL		1
#define ENCODER_TILT_PITCH	2
#define ENCODER_TILT_ROLL	3
#define	ENCODER_COUNT		4

// ADC selector numbers (used to select the ADC via the I2C mux).
#define	ADC_PITCH_SELECTOR	0
#define	ADC_ROLL_SELECTOR	1
#define ADC_TILT_X_SELECTOR	5
#define ADC_TILT_Y_SELECTOR	6

// GPIOs.
#define	PIN_FLOW_METER		23

// Lookup table from pitch motor position to pitch angle in hundredths of degrees.
static const Table::entry_t pitch_motor_step_to_angle_array[] =
{
	// TODO
	{ 0, -45 },		// 0 steps -> -0.45 degrees
	{ 12628, 360 },		// 12650 steps -> +3.6 degrees
	END_OF_TABLE
};
static const Table pitch_motor_step_to_angle_table(true, pitch_motor_step_to_angle_array);

// Lookup table from pitch encoder millivolts to pitch angle in hundredths of degrees.
static const Table::entry_t pitch_encoder_mv_to_angle_array[] =
{
	// TODO
	{ 1197, 360 },		// 1197 mV -> +3.6 degrees
	{ 2667, -45 },		// 2667 mV -> -0.45 degrees
	END_OF_TABLE
};
static const Table pitch_encoder_mv_to_angle_table(true, pitch_encoder_mv_to_angle_array);

// Lookup table from roll motor position to roll angle in hundredths of degrees.
static const Table::entry_t roll_motor_step_to_angle_array[] =
{
	// TODO
	{ -8764, -370 },
	{ -8201, -340 },
	{ -7954, -300 },
	{ -6064, -250 },
	{ -5096, -200 },
	{ -4477, -150 },
	{ -2261, -100 },
	{ -1935, -50 },
	{ 0, 0 },
	{ 1001, 50 },
	{ 1518, 100 },
	{ 3836, 150 },
	{ 4162, 200 },
	{ 6266, 250 },
	{ 7020, 300 },
	{ 7369, 340 },
	{ 8809, 370 },
	END_OF_TABLE
};
static const Table roll_motor_step_to_angle_table(true, roll_motor_step_to_angle_array);

// Lookup table from roll encoder millivolts to roll angle in hundredths of degrees.
static const Table::entry_t roll_encoder_mv_to_angle_array[] =
{
	// TODO
	{ 1542, 370 },		// 1.533V -> 3.7 degrees
	{ 1679, 340 },
	{ 1710, 300 },
	{ 1777, 250 },
	{ 1964,	200 },
	{ 1993, 150 },
	{ 2199, 100 },
	{ 2247,	50 },
	{ 2342,  0 },
	{ 2506, -50 },
	{ 2535, -100 },
	{ 2732, -150 },
	{ 2787, -200 },
	{ 2873,	-250 },
	{ 3041, -300 },
	{ 3063,	-340 },
	{ 3109, -370 },		// 3.073V -> -3.7 degrees
	END_OF_TABLE
};
static const Table roll_encoder_mv_to_angle_table(true, roll_encoder_mv_to_angle_array);

// Lookup table from standpipe motor position to height in tenths of millimeters.
// This is just a straight linear interpolation that sets the min and max limits.
// The standpipe ranges from 8mm high to 100mm high, between limit switches.
// This corresponds to a range of 2,456 steps, or 26.69 steps per millimeter.
static const Table::entry_t pipe_motor_step_to_mm_array[] =
{
	{ 0, 8 },		// 0 steps -> 8 mm
	{ 2456, 1000 },		// 2456 steps -> 100 mm
	END_OF_TABLE
};
static const Table pipe_motor_step_to_mm_table(true, pipe_motor_step_to_mm_array);

// Lookup table from pump output level (0..255) to flow in tenths of mL/sec.
// This is a piecewise linear interpolation.
static const Table::entry_t pump_speed_to_flow_array[] =
{
	{ 0, 0 },		// speed 0 -> 0 mL/s
	{ 255, 9600 },		// speed 255 -> 960 mL/s
	END_OF_TABLE
};
static const Table pump_speed_to_flow_table(true, pump_speed_to_flow_array);

// Lookup table from tilt pitch encoder millivolts to pitch angle in hundredths of degrees.
static const Table::entry_t tilt_pitch_encoder_mv_to_angle_array[] =
{
	// TODO
	{ 500, 2500 },		// 500 mV -> 25 degrees
	{ 4500, -2500 },	// 4500 mV -> -25 degrees
	END_OF_TABLE
};
static const Table tilt_pitch_encoder_mv_to_angle_table(true, tilt_pitch_encoder_mv_to_angle_array);

// Lookup table from tilt roll encoder millivolts to roll angle in hundredths of degrees.
static const Table::entry_t tilt_roll_encoder_mv_to_angle_array[] =
{
	// TODO
	{ 500, -2500 },		// 500 mV -> -25 degrees
	{ 4500, 2500 },		// 4500 mV -> +25 degrees
	END_OF_TABLE
};
static const Table tilt_roll_encoder_mv_to_angle_table(true, tilt_roll_encoder_mv_to_angle_array);

static const char* format_10ths_of_degrees(int16_t val_)
{
	static char text[20];
	if (val_ < 0) {
		snprintf(text, sizeof(text), "-%d.%01d degrees", -val_ / 10, -val_ % 10);
	} else if (val_ > 0) {
		snprintf(text, sizeof(text), "+%d.%01d degrees", val_ / 10, val_ % 10);
	} else {
		snprintf(text, sizeof(text), "0.0 degrees");
	}
	return text;
}

static const char* format_mm(int16_t val_)
{
	static char text[20];
	if (val_ < 0) {
		snprintf(text, sizeof(text), "-%d mm", -val_);
	} else if (val_ > 0) {
		snprintf(text, sizeof(text), "+%d mm", val_);
	} else {
		snprintf(text, sizeof(text), "0 mm");
	}
	return text;
}

static const char* format_ml_per_s(int16_t val_)
{
	static char text[20];
	if (val_ < 0) {
		snprintf(text, 10, "-%d mL/s", -val_);
	} else if (val_ > 0) {
		snprintf(text, 10, "+%d mL/s", val_);
	} else {
		snprintf(text, 10, "0 mL/s");
	}
	return text;
}

struct EM4R::Impl : public OperatorBase, public EncoderBase, public Clock
{
	// Implement main-class methods.
	virtual ~Impl();
	Impl();
#if CONFIG_LOG
	void Log();
#endif
#if CONFIG_LINK
	void Link();
#endif
	void Work();

	// Implement OperatorBase methods.
	void Control(uint8_t instance_, const operator_control_capsule_data_t& occd_) override;
	bool Status(uint8_t instance_, operator_status_capsule_data_t& oscd_) override;
	uint8_t OperatorCount() override;
	void Initialize();

	// Implement EncoderBase methods.
	bool Status(uint8_t instance_, encoder_status_capsule_data_t& oscd_) override;
	uint8_t EncoderCount() override;

	// Implement Clock methods.
	uint32_t Milliseconds() override;

	// The model preferences.
	Preferences prefs;

	// The ADS1115s, one per encoder.
	ADS1115* adc[ENCODER_COUNT];

	// The UDP packet handler.
	UDPServer* udpserver;

	// The serial link to the motor driver subsystem.
	ControlLinkMaster* link;

	// The encoder wrappers.
	TableMappedEncoder* pitch_encoder;
	TableMappedEncoder* roll_encoder;
	MagneticFlowMeter* flow_meter;
	TableMappedEncoder* tilt_pitch_encoder;
	TableMappedEncoder* tilt_roll_encoder;

	// Each operator.
	Operator* op[OPERATOR_COUNT];

	// Set while the standpipe is being calibrated; other movement is prohibited.
	enum cal_t {
		UNCALIBRATED,
		CALIBRATING,
		CALIBRATED
	};
	cal_t pitch_calibration;
	cal_t roll_calibration;
	cal_t pipe_calibration;
	cal_t pump_calibration;

	// Service the serial link.
	void ServiceLink();
};

EM4R::Impl::~Impl()
{
	for (uint8_t zadc = 0; zadc < ENCODER_COUNT; zadc++) {
		delete adc[zadc];
	}
	delete udpserver;
	delete link;

	// Delete operators.
	for (uint8_t zop = 0; zop < OPERATOR_COUNT; zop++) {
		delete op[zop];
	}

	// Delete encoders.
	delete pitch_encoder;
	delete roll_encoder;
	delete flow_meter;
	delete tilt_pitch_encoder;
	delete tilt_roll_encoder;
}


EM4R::Impl::Impl() :
	pitch_calibration(UNCALIBRATED),
	roll_calibration(UNCALIBRATED),
	pipe_calibration(UNCALIBRATED),
	pump_calibration(UNCALIBRATED)
{
	// Now that the preferences have been instantiated, force the SD card off.
	digitalWrite(10, HIGH);
	pinMode(10, OUTPUT);

	// Clear operator pointers.
	for (uint8_t zop = 0; zop < OPERATOR_COUNT; zop++) {
		op[zop] = nullptr;
	}

	// Clear ADC pointers.
	for (uint8_t zadc = 0; zadc < ENCODER_COUNT; zadc++) {
		adc[zadc] = nullptr;
	}

	// Clear encoder pointers.
	pitch_encoder = nullptr;
	roll_encoder = nullptr;
	flow_meter = nullptr;

	// Initialize I2C as master.
//	Wire.setClock(100000);
	Wire.setClock(20000);
	Wire.begin();
	DBG_ERR("Initialized I2C");

	// Initialize ADCs.
#if CONFIG_ENCODERS && CONFIG_PITCH
	// Mux port 0 is the pitch encoder input.
	adc[ENCODER_PITCH] = new ADS1115(ADS1115_I2C_ADDR_GND, TCA9548A_I2C_ADDR, ADC_PITCH_SELECTOR, true, ADS1115_FSR_4V096, ADS1115_RATE_16_SPS);
#endif

#if CONFIG_ENCODERS && CONFIG_ROLL
	// Mux port 1 is the roll encoder input.
	adc[ENCODER_ROLL] = new ADS1115(ADS1115_I2C_ADDR_GND, TCA9548A_I2C_ADDR, ADC_ROLL_SELECTOR, true, ADS1115_FSR_4V096, ADS1115_RATE_16_SPS);
#endif

	// Mux port 2 is the downstream standpipe encoder (currently not used).
	// Mux port 3 is the upstream standpipe encoder (currently not used).
	// Mux port 4 is not connected.

#if CONFIG_ENCODERS && CONFIG_TILT
	// Mux ports 5 and 6 are the tilt sensor X and Y axes, respectively.
	adc[ENCODER_TILT_PITCH] = new ADS1115(ADS1115_I2C_ADDR_GND, TCA9548A_I2C_ADDR, ADC_TILT_Y_SELECTOR, true, ADS1115_FSR_6V144, ADS1115_RATE_16_SPS);
	adc[ENCODER_TILT_ROLL] = new ADS1115(ADS1115_I2C_ADDR_GND, TCA9548A_I2C_ADDR, ADC_TILT_X_SELECTOR, true, ADS1115_FSR_6V144, ADS1115_RATE_16_SPS);
#endif

#if CONFIG_NET
	// Instantiate the UDP server.
	udpserver = new UDPServer(prefs, this, this);
#endif

#if CONFIG_LINK
	// Instantiate the serial link.
	link = new ControlLinkMaster(Serial2, 100000, 32);
#endif

	// Create encoders and operators.

#if CONFIG_PITCH
#if CONFIG_ENCODERS
	pitch_encoder = new TableMappedEncoder("pitch", format_10ths_of_degrees, adc[ENCODER_PITCH], &pitch_encoder_mv_to_angle_table);
#endif
	TableMappedOperator* pitch_operator = new TableMappedOperator(OPERATOR_PITCH, "pitch", link, *this, prefs,
		pitch_encoder, format_10ths_of_degrees,
		&pitch_motor_step_to_angle_table);
	pitch_operator->SetMinMaxTicksPerStep(
		1,				// 4,000 steps/sec
		0xFFFF				// 16 secs/step
	);
	op[OPERATOR_PITCH] = pitch_operator;
#endif

#if CONFIG_ROLL
#if CONFIG_ENCODERS
	roll_encoder = new TableMappedEncoder("roll", format_10ths_of_degrees, adc[ENCODER_ROLL], &roll_encoder_mv_to_angle_table);
#endif
	TableMappedOperator* roll_operator = new TableMappedOperator(OPERATOR_ROLL, "roll", link, *this, prefs,
		roll_encoder, format_10ths_of_degrees,
		&roll_motor_step_to_angle_table);
	roll_operator->SetMinMaxTicksPerStep(
		1,				// 4,000 steps/sec
		0xFFFF				// 16 secs/step
	);
	op[OPERATOR_ROLL] = roll_operator;
#endif

#if CONFIG_PIPE
	TableMappedOperator* pipe_operator = new TableMappedOperator(OPERATOR_PIPE, "pipe", link, *this, prefs,
		nullptr, format_mm,
		&pipe_motor_step_to_mm_table);
	pipe_operator->SetMinMaxTicksPerStep(
		1,				// 4,000 steps/sec
		0xFFFF				// 16 secs/step
	);
	op[OPERATOR_PIPE] = pipe_operator;
#endif

#if CONFIG_PUMP
#if CONFIG_ENCODERS
	flow_meter = new MagneticFlowMeter("flow", format_ml_per_s, PIN_FLOW_METER);
#endif
	TableMappedOperator* pump_operator = new TableMappedOperator(OPERATOR_PUMP, "pump", link, *this, prefs,
		flow_meter, format_ml_per_s,
		&pump_speed_to_flow_table);
	pump_operator->SetMinMaxTicksPerStep(
		16,				// 250 steps/sec
		0xFFFF				// 16 secs/step
	);
	op[OPERATOR_PUMP] = pump_operator;
#endif

#if CONFIG_ENCODERS && CONFIG_TILT
	tilt_pitch_encoder = new TableMappedEncoder("tilt-pitch", format_10ths_of_degrees, adc[ENCODER_TILT_PITCH], &tilt_pitch_encoder_mv_to_angle_table);
	tilt_roll_encoder = new TableMappedEncoder("tilt-roll", format_10ths_of_degrees, adc[ENCODER_TILT_ROLL], &tilt_roll_encoder_mv_to_angle_table);
#endif
}

#if CONFIG_LOG
void EM4R::Impl::Log()
{
	if (pitch_encoder) {
		pitch_encoder->Log();
	}
	if (roll_encoder) {
		roll_encoder->Log();
	}
	if (flow_meter) {
		flow_meter->Log();
	}
	if (tilt_pitch_encoder) {
		tilt_pitch_encoder->Log();
	}
	if (tilt_roll_encoder) {
		tilt_roll_encoder->Log();
	}
}
#endif


void EM4R::Impl::Work()
{
#if 0
	// Poll the inputs from the ADCs.
	for (uint8_t chip = 0; chip < 1; chip++) {
		for (uint8_t line = 0; line < 2; line++) {
			uint32_t val;
			uint16_t raw;
			if (adc[chip]->Read(line, val, &raw)) {
				DBG_ERR("ADC %u.%u: %04X %lu.%06luV", chip, line, raw, val / 1000000UL, val % 1000000UL);
			}
		}
	}
#endif

#if CONFIG_NET
	// Service the Ethernet interface.
	if (udpserver) {
		udpserver->Work();
	}
#endif

#if CONFIG_LINK
	// Service the link to the driver subsystem.
	ServiceLink();
#endif

#if CONFIG_SIM
	// Simulate movement of operators.
	for (uint8_t inst = 0; inst < OPERATOR_COUNT; inst++) {
		if (op[inst]) {
			op[inst]->Work();
		}
	}
#endif

	if (op[OPERATOR_PIPE]) {
		// If the standpipe needs to be calibrated, tell it to move to its highest position.
		if (pipe_calibration == UNCALIBRATED) {

			// Stop.
			op[OPERATOR_PIPE]->PostControlStop();

			// Reset to 0.
			op[OPERATOR_PIPE]->PostControlResetPos(0);

			// Go to the maximum value (we will stop when we hit the limit).
			op[OPERATOR_PIPE]->PostControlMoveImmediate(0x7FFF);

			// Go into "calibrating" state (waiting for limit).
			DBG_ERR("Moving standpipe to maximum position");
			pipe_calibration = CALIBRATING;
		}

		else if (pipe_calibration == CALIBRATING) {

			// Waiting to hit the limit.
			uint8_t flags = op[OPERATOR_PIPE]->DriverFlags();
//			DBG_ERR("PIPE FLAGS = %02X", flags);
			if (flags & SD6575_FLAG_MAX_LIMIT_STOP) {

				// Reset the motor position to the maximum step value.
				int16_t step = (pipe_motor_step_to_mm_table.Last().x > pipe_motor_step_to_mm_table.First().x) ?
					pipe_motor_step_to_mm_table.Last().x :
					pipe_motor_step_to_mm_table.First().x;
				op[OPERATOR_PIPE]->PostControlResetPos(step);

				// Now calibrated.
				DBG_ERR("Standpipe calibrated at step %d", step);
				pipe_calibration = CALIBRATED;
			}
		}
	}

	if (op[OPERATOR_PITCH]) {
		// If the pitch stepper needs to be calibrated, read the encoder position.
		if (pitch_calibration == UNCALIBRATED) {

			// Stop.
			op[OPERATOR_PITCH]->PostControlStop();

			// Read the raw value from the pitch encoder.
			int16_t angle;
			if (pitch_encoder && pitch_encoder->GetValuePU(angle)) {

				// Map to a motor step.
				int16_t step = pitch_motor_step_to_angle_table.MapYToX(angle);

				// Reset the motor position to the specified step.
				op[OPERATOR_PITCH]->PostControlResetPos(step);

				// Now calibrated.
				DBG_ERR("Pitch calibrated at step %d (%s)", step, format_10ths_of_degrees(angle));
				pitch_calibration = CALIBRATED;
			}
		}
	}

	if (op[OPERATOR_ROLL]) {
		// If the roll stepper needs to be calibrated, read the encoder position.
		if (roll_calibration == UNCALIBRATED) {

			// Stop.
			op[OPERATOR_ROLL]->PostControlStop();

			// Read the raw value from the pitch encoder.
			int16_t angle;
			if (roll_encoder && roll_encoder->GetValuePU(angle)) {

				// Map to a motor step.
				int16_t step = roll_motor_step_to_angle_table.MapYToX(angle);

				// Reset the motor position to the specified step.
				op[OPERATOR_ROLL]->PostControlResetPos(step);

				// Now calibrated.
				DBG_ERR("Roll calibrated at step %d (%s)", step, format_10ths_of_degrees(angle));
				roll_calibration = CALIBRATED;
			}
		}
	}

	if (op[OPERATOR_PUMP]) {
		if (pump_calibration == UNCALIBRATED) {

			// Stop.
			op[OPERATOR_PUMP]->PostControlStop();
			pump_calibration = CALIBRATED;
		}
	}
}

void EM4R::Impl::Control(uint8_t instance_, const operator_control_capsule_data_t& occd_)
{
	DBG_ERR("Model got control message for instance %u", instance_);

	// Reset is special.
	if (occd_.command == OPERATOR_CONTROL_CMD_RESET) {
		switch (instance_) {
		case OPERATOR_PITCH:
			pitch_calibration = UNCALIBRATED;
			break;
		case OPERATOR_ROLL:
			roll_calibration = UNCALIBRATED;
			break;
		case OPERATOR_PIPE:
			pipe_calibration = UNCALIBRATED;
			break;
		case OPERATOR_PUMP:
			pump_calibration = UNCALIBRATED;
			break;
		}
		return;
	}

	// If this is for the standpipe and it isn't calibrated yet, ignore it.
	if ((instance_ == OPERATOR_PITCH) && (pitch_calibration != CALIBRATED)) {
		return;
	}
	if ((instance_ == OPERATOR_ROLL) && (roll_calibration != CALIBRATED)) {
		return;
	}
	if ((instance_ == OPERATOR_PIPE) && (pipe_calibration != CALIBRATED)) {
		return;
	}
	if ((instance_ == OPERATOR_PUMP) && (pump_calibration != CALIBRATED)) {
		return;
	}

	// Find the matching instance and let it handle the message.
	for (uint8_t iop = 0; iop < OPERATOR_COUNT; iop++) {
		if(op[iop] && (op[iop]->GetInstanceID() == instance_)) {
			op[iop]->Control(occd_);
			return;
		}
	}
}

bool EM4R::Impl::Status(uint8_t instance_, operator_status_capsule_data_t& oscd_)
{
	// Find the matching instance and get its status.
	for (uint8_t iop = 0; iop < OPERATOR_COUNT; iop++) {
		if (op[iop] && (op[iop]->GetInstanceID() == instance_)) {
			op[iop]->Status(oscd_);
			return true;
		}
	}
	return false;
}

uint8_t EM4R::Impl::OperatorCount()
{
	return OPERATOR_COUNT;
}

void EM4R::Impl::Initialize()
{
	// All we do to reinitialize is un-calibrate everything.
	pitch_calibration = UNCALIBRATED;
	roll_calibration = UNCALIBRATED;
	pipe_calibration = UNCALIBRATED;
}

bool EM4R::Impl::Status(uint8_t instance_, encoder_status_capsule_data_t& oscd_)
{
	// Report status for the corresponding encoder.
	switch (instance_) {
	case 0:
		return pitch_encoder ? pitch_encoder->Status(oscd_) : false;
	case 1:
		return roll_encoder ? roll_encoder->Status(oscd_) : false;
	case 2:
		return tilt_pitch_encoder ? tilt_pitch_encoder->Status(oscd_) : false;
	case 3:
		return tilt_roll_encoder ? tilt_roll_encoder->Status(oscd_) : false;
	}
	return false;
}

uint8_t EM4R::Impl::EncoderCount()
{
	return ENCODER_COUNT;
}

uint32_t EM4R::Impl::Milliseconds() 
{
	return udpserver ? udpserver->ActiveMilliseconds() : 0;
}

#if CONFIG_LINK
void EM4R::Impl::ServiceLink()
{
	if (!link) {
		return;
	}
	uint8_t msg[32];
	uint16_t rx;
	while (0 != (rx = link->Receive(msg, sizeof(msg)))) {

		// Start at the beginning.
		const uint8_t* next = msg;

		// Driver status messages should be 4 bytes (instance, flags, and 2 position bytes).
		while (rx >= 4) {

			// Get the instance value (must be valid).
			uint8_t inst = *next++;
			rx--;
			if (inst >= OPERATOR_COUNT) {
				// Bad operator index
				rx = 0;
				break;
			}

			// Process the status information.
			if (op[inst]) {
				op[inst]->DriverStatus(next, 3);
				next += 3;
				rx -= 3;
			}
		}
	}
}
#endif

EM4R::~EM4R()
{
	delete impl;
}

EM4R::EM4R() :
	impl(new Impl)
{
}

#if CONFIG_LOG
void EM4R::Log()
{
	impl->Log();
}
#endif

void EM4R::Work()
{
	impl->Work();
}
