#include <sys/types.h>
#include <sys/socket.h>

#include <netinet/in.h>
#include <arpa/inet.h>

#include <vector>
#include <list>
#include <string>

#define	MODE_RANDOM	1
#define	MODE_RESET	2	// reset calibration
#define	MODE_START	3	// read and restart script
#define	MODE_RESUME	4	// read and resume script based on save file
#define	MODE_HIPITCH 5	// high pitch test
#define	MODE_MAXMOVE 6	// maximum movement test
#define	MODE_ENCMON	7	// encoder monitoring
#define	MODE_RANDOMDRY 8	// random w/o pump
#define	MODE_CHILL	9	// random w/o pump, minor movement
#define MODE_JOG	10 //jog to values
#define MODE_HOME	11 //home all operators to 0

struct script_step_t
{
	// Time in seconds
	unsigned int seconds;

	// Value omitted (meaning, continue)
	bool pitch_omitted;
	bool roll_omitted;
	bool pipe_omitted;
	bool flow_omitted;

	// pitch, roll, pipe, flow
	int pitch;
	int roll;
	int pipe;
	int flow;

	void dump();
};

struct script_t
{
	std::list<script_step_t> steps;

	bool read(const std::string& path_);
	void discard_before(unsigned int seconds_);
	void dump();
};



class model
{
public:
	~model();
	model(const char* ip_addr_, uint16_t port_);

	// The IP address of the model.
	struct sockaddr_in peer;

	// The minimum and maximum values for each operator.
	int16_t mins[5];
	int16_t maxs[5];

	// The last reported value for each operator and encoder.
	bool reported_op_valid[4];
	int16_t reported_op_value[4];
	uint16_t reported_op_flags[4];
	bool reported_enc_valid[4];
	int16_t reported_enc_value[4];
	uint16_t reported_enc_mv[4];

	// The socket descriptor.
	int sd;

	// The model clock in milliseconds.
	uint32_t model_clock;

	// The next sequence number to send.
	uint8_t tx_seq;

	// The last sequence number received.
	uint8_t rx_seq;

	// The time we issued a change request to an operator.
	time_t last_change;

	// The operator currently being worked on.
	uint8_t last_op;

	// Format a system control capsule.
	void push_system_control(std::vector<uint8_t>& v_, uint8_t cmd_);


	// Format operator control capsules.
	void push_operator_control_move(std::vector<uint8_t>& v_, uint8_t instance_, int16_t requested_value_, uint32_t relative_ms_);
	void push_operator_control_stop(std::vector<uint8_t>& v_, uint8_t instance_);
	void push_operator_control_reset(std::vector<uint8_t>& v_, uint8_t instance_);

	void process_message(bool debug_ = false);
	void send_message(std::vector<uint8_t>& v_);
	void send_system_nop();
	void send_operator_control_move(uint8_t instance_, int16_t requested_value_, uint32_t relative_ms_);
	void send_operator_control_stop(uint8_t instance_);
	void send_operator_control_stop_all();
	void send_operator_control_reset(uint8_t instance_);
	void wait_for_message();
	void wait_all_stopped();

	// Do work.
	void random_movement(bool pump_);
	void chill_movement();
	void maximum_movement();
	void encoder_monitor();
	void reset();
	void high_pitch();
	void run_script(script_t& script_, const std::string& savefile_path_, uint32_t exp_clock_);
	void jog(const char* opselect, int16_t jval, uint32_t millis = 10000);
	void home();
};