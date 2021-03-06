
#include <sys/types.h>
#include <sys/socket.h>

#include <netinet/in.h>
#include <arpa/inet.h>

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>

#include <string>
#include <vector>
#include <list>

#define DELIM		",\r\n\t"

#define	OPERATOR_COUNT	4

#define	MIN_PITCH	0	// 0 degrees
#define	MAX_PITCH	35	// 3.5 degrees
#define	MIN_ROLL	-36	// -3.6 degrees
#define	MAX_ROLL	36	// 3.6 degrees
#define	MIN_PIPE	5	// 5mm
#define	MAX_PIPE	100	// 100mm
#define	MIN_FLOW	0	// 0 mL/s
#define	MAX_FLOW	960	// 960 mL/s

#define	MODE_RANDOM	1
#define	MODE_RESET	2	// reset calibration
#define	MODE_START	3	// read and restart script
#define	MODE_RESUME	4	// read and resume script based on save file

#undef FAKE

static void push_u8(std::vector<uint8_t>& v_, uint8_t val_)
{
	v_.push_back(val_);
}

static void push_u16_nbo(std::vector<uint8_t>& v_, uint16_t val_)
{
	v_.push_back((val_ >> 8) & 0xFF);
	v_.push_back(val_ & 0xFF);
}

static void push_i16_nbo(std::vector<uint8_t>& v_, int16_t val_)
{
	push_u16_nbo(v_, (uint16_t)val_);
}

static void push_u32_nbo(std::vector<uint8_t>& v_, uint32_t val_)
{
	v_.push_back((val_ >> 24) & 0xFF);
	v_.push_back((val_ >> 16) & 0xFF);
	v_.push_back((val_ >> 8) & 0xFF);
	v_.push_back(val_ & 0xFF);
}

static void check_length(std::vector<uint8_t>& v_, size_t length_)
{
	if (v_.size() < length_) {
		throw std::string("message too short");
	}
}

static uint8_t pop_u8(std::vector<uint8_t>& v_)
{
	check_length(v_, 1);
	uint8_t val = v_[0];
	v_.erase(v_.begin(), v_.begin() + 1);
	return val;
}

static uint16_t pop_u16_nbo(std::vector<uint8_t>& v_)
{
	check_length(v_, 2);
	uint16_t val = (((uint16_t)v_[0]) << 8) | v_[1];
	v_.erase(v_.begin(), v_.begin() + 2);
	return val;
}

static int16_t pop_i16_nbo(std::vector<uint8_t>& v_)
{
	return (int16_t)pop_u16_nbo(v_);
}

static uint32_t pop_u32_nbo(std::vector<uint8_t>& v_)
{
	check_length(v_, 4);
	uint32_t val = (((uint32_t)v_[0]) << 24) | (((uint32_t)v_[1]) << 16) | (((uint16_t)v_[2]) << 8) | v_[3];
	v_.erase(v_.begin(), v_.begin() + 4);
	return val;
}

static void trim(std::string& s_)
{
	while (!s_.empty() && isspace(s_[0])) {
		s_.erase(0, 1);
	}
	while (!s_.empty() && isspace(s_[s_.size() - 1])) {
		s_.erase(s_.size() - 1);
	}
}

static std::vector<std::string> tokenize(const std::string& s_)
{
	std::vector<std::string> res;

	std::string src = s_;
	trim(src);

	std::string part;
	for (size_t i = 0; i < s_.size(); i++) {
		if (s_[i] == ',') {
			trim(part);
			res.push_back(part);
			part.clear();
		} else {
			part += s_[i];
		}
	}
	trim(part);
	res.push_back(part);
	return res;
}

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

	void process_message();
	void send_message(std::vector<uint8_t>& v_);
	void send_system_nop();
	void send_operator_control_move(uint8_t instance_, int16_t requested_value_, uint32_t relative_ms_);
	void send_operator_control_stop(uint8_t instance_);
	void send_operator_control_stop_all();
	void wait_for_message();
	void wait_all_stopped();

	// Do work.
	void random_movement();
	void run_script(script_t& script_, const std::string& savefile_path_, uint32_t exp_clock_);
};

model::~model()
{
	if (sd >= 0) {
		close(sd);
	}
}

model::model(const char* ip_addr_, uint16_t port_) :
	sd(-1),
	model_clock(0),
	tx_seq(0),
	rx_seq(0),
	last_change(0),
	last_op(0)
{
	// Convert the inet.
	peer.sin_family = AF_INET;
	peer.sin_port = htons(port_);
	peer.sin_addr.s_addr = inet_addr(ip_addr_);

	// Set operating ranges.
	mins[0] = MIN_PITCH;
	maxs[0] = MAX_PITCH;
	mins[1] = MIN_ROLL;
	maxs[1] = MAX_ROLL;
	mins[2] = MIN_PIPE;
	maxs[2] = MAX_PIPE;
	mins[3] = MIN_FLOW;
	maxs[3] = MAX_FLOW;

	for (uint8_t slot = 0; slot < 4; slot++) {
		reported_op_valid[slot] = 0;
		reported_enc_valid[slot] = 0;
	}

	sd = socket(AF_INET, SOCK_DGRAM, 0);
	if (sd < 0) {
		throw std::string("error: socket: ") + std::string(strerror(errno));
	}

	int enable = 1;	
	if (0 > setsockopt(sd, SOL_SOCKET, SO_REUSEADDR, &enable, sizeof(enable))) {
		throw std::string("error: setsockopt: ") + std::string(strerror(errno));
		exit(1);
	}

	// Send the initial message.
	send_system_nop();

	// Wait for a reported clock.
	printf("Waiting for response from model...\n");
	wait_for_message();
}

// Format a system control capsule.
void model::push_system_control(std::vector<uint8_t>& v_, uint8_t cmd_)
{
	push_u8(v_, 8);
	push_u8(v_, 1);
	push_u16_nbo(v_, 0x4543);

	push_u8(v_, ++tx_seq);
	push_u8(v_, rx_seq);
	push_u16_nbo(v_, 0);

	push_u32_nbo(v_, model_clock);
}

void model::push_operator_control_move(std::vector<uint8_t>& v_, uint8_t instance_, int16_t requested_value_, uint32_t relative_ms_)
{
	push_u8(v_, 8);
	push_u8(v_, instance_);
	push_u16_nbo(v_, 0x5043);

	push_i16_nbo(v_, requested_value_);
	push_u8(v_, 1);	// move
	push_u8(v_, 0);
	
	push_u32_nbo(v_, relative_ms_);
}

void model::push_operator_control_stop(std::vector<uint8_t>& v_, uint8_t instance_)
{
	push_u8(v_, 8);
	push_u8(v_, instance_);
	push_u16_nbo(v_, 0x5043);

	push_i16_nbo(v_, 0);
	push_u8(v_, 2);	// stop
	push_u8(v_, 0);
	
	push_u32_nbo(v_, 0);
}

void model::process_message()
{
	std::vector<uint8_t> v;
	v.resize(256);
	ssize_t bytes = recv(sd, &v[0], v.size(), MSG_DONTWAIT);
	if (bytes <= 0) {
		return;
	}
	v.resize(bytes);

//	printf("sys: %02X %02X %02X%02X\n", v[0], v[1], v[2], v[3]);
//	printf("     %02X %02X %02X%02X\n", v[4], v[5], v[6], v[7]);
//	printf("     %02X%02X%02X%02X\n", v[8], v[9], v[10], v[11]);

	try {
		// Pop the system status capsule.
		if (8 != pop_u8(v)) {
			throw std::string("wrong rx length");
		}
		if (1 != pop_u8(v)) {
			throw std::string("wrong protocol version");
		}
		if (0x4553 != pop_u16_nbo(v)) {
			throw std::string("bad magic number");
		}

		rx_seq = pop_u8(v);
		uint8_t echo_seq = pop_u8(v);
		if (echo_seq != tx_seq) {
//			printf("warning: got tx_seq %02X, expected %02X\n", echo_seq, tx_seq);
		}
		pop_u16_nbo(v);

		model_clock = pop_u32_nbo(v);
		//printf("model_clock = %u\n", model_clock);

		// Pop any operator status capsules.
		while (v.size()) {
#if 0
			printf("ops: %02X %02X %02X%02X\n", v[0], v[1], v[2], v[3]);
			printf("     %02X%02X %02X%02X\n", v[4], v[5], v[6], v[7]);
			printf("     %02X%02X %02X%02X\n", v[8], v[9], v[10], v[11]);
			printf("     %02X%02X%02X%02X\n", v[12], v[13], v[14], v[15]);
#endif

			uint8_t rx_length = pop_u8(v);
			uint8_t instance = pop_u8(v);
			uint16_t magic = pop_u16_nbo(v);
//			printf("rx_length = %u, magic = %04X %c%c\n", rx_length, magic, (magic >> 8) & 0xFF, magic & 0xFF);
			if ((0x5053 == magic) && (12 == rx_length) && (instance < 4)) {
				int16_t pos = pop_i16_nbo(v);
				uint16_t flags = pop_u16_nbo(v);
				pop_i16_nbo(v);		// req value
				pop_i16_nbo(v);		// dummy
				pop_u32_nbo(v);		// time to achieve
//				printf("status: operator %u is at %d, flags %02X\n", instance, pos, flags);
				reported_op_valid[instance] = 1;
				reported_op_value[instance] = pos;
				reported_op_flags[instance] = flags;
			}
			if ((0x4553 == magic) && (4 == rx_length) && (instance < 4)) {
				int16_t pos = pop_i16_nbo(v);
				int16_t mv = pop_i16_nbo(v);
//				printf("status: encoder %u is at %d\n", instance, pos);
				reported_enc_valid[instance] = 1;
				reported_enc_value[instance] = pos;
				reported_enc_mv[instance] = mv;
			}
		}
	}

	catch (const std::string& s_) {
		printf("error: %s\n", s_.c_str());
	}
}

void model::send_message(std::vector<uint8_t>& v_)
{
	if (0 >= sendto(sd, &v_[0], v_.size(), 0, (struct sockaddr*)&peer, sizeof(peer))) {
		perror("sendto");
	}
}

void model::send_system_nop()
{
	std::vector<uint8_t> v;
	push_system_control(v, 0);
	send_message(v);
}

void model::send_operator_control_move(uint8_t instance_, int16_t requested_value_, uint32_t relative_ms_)
{
	printf("%u: -> %d in %u msec\n", instance_, requested_value_, relative_ms_);
#ifndef FAKE
	std::vector<uint8_t> v;
	push_system_control(v, 0);
	push_operator_control_move(v, instance_, requested_value_, relative_ms_);
	send_message(v);
#endif
}

void model::send_operator_control_stop(uint8_t instance_)
{
	std::vector<uint8_t> v;
	push_system_control(v, 0);
	push_operator_control_stop(v, instance_);
	send_message(v);
}

void model::send_operator_control_stop_all()
{
	std::vector<uint8_t> v;
	push_system_control(v, 0);
	for (uint8_t inst = 0; inst < OPERATOR_COUNT; inst++) {
		push_operator_control_stop(v, inst);
	}
	send_message(v);
}

void model::wait_for_message()
{
#ifndef FAKE
	uint32_t saved_clock = model_clock;
	do {
		process_message();
		//printf("%u == %u\n", saved_clock, model_clock);
	} while (saved_clock == model_clock);
#endif
}

void model::random_movement()
{
	for (;;) {
		// Time to do something?
		time_t now = time(NULL);

		// Only run often enough to make sure we saw a status update from each operator.
		if ((now - last_change) > 2) {
			last_change = now;
			// See if we can do anything with any of the operators.
			for (uint8_t zop = 0; zop < 4; zop++) {
				// If the operator has not reported a value, or it's moving, skip it.
				if (!reported_op_valid[zop] || (reported_op_flags[zop] & 0x40)) {
					continue;
				}

				// Move/change to a random legal value in a time somewhere between 0 and 10 seconds.
				int16_t val = mins[zop] + (((rand() % 1000) * (maxs[zop] - mins[zop])) / 1000);
				uint32_t ms = rand() % 30000;
				printf("control: telling operator %u to move to %d in %d ms\n", zop, val, ms);
				send_operator_control_move(zop, val, ms);
			}

			// Keep the connection alive.
			send_system_nop();
		}

		// Process any message.
		process_message();
	}
}

void model::run_script(script_t& script_, const std::string& savefile_path_, uint32_t start_exp_clock_)
{
	if (script_.steps.empty()) {
		printf("Script is empty\n");
		return;
	}

	// Stop everything.
	send_operator_control_stop_all();

	// Wait for everything to stop.
	wait_all_stopped();

	// Move everything to the initial condition.
	send_operator_control_move(0, script_.steps.front().pitch, 0);
	send_operator_control_move(1, script_.steps.front().roll, 0);
	send_operator_control_move(2, script_.steps.front().pipe, 0);
	send_operator_control_move(3, script_.steps.front().flow, 0);

	// Wait for a message.
	wait_for_message();

	// Wait for everything to stop.
	wait_all_stopped();

	time_t start_time = time(NULL);
	time_t last_time = start_time;
	for (;;) {
		process_message();

		if (script_.steps.empty()) {
			printf("Reached end of script\n");
			return;
		}

		// Get the current experiment clock time.
		time_t current_time = time(NULL);
		if (current_time == last_time) {
			continue;
		}
		last_time = current_time;
		uint32_t exp_clock = (current_time - start_time) + start_exp_clock_;

		// Keep the connection alive.
		send_system_nop();

		// Get the current step.
		script_step_t ss = script_.steps.front();

		// Is it not time to run this step yet?
		if (exp_clock < ss.seconds) {
			printf("T = %u (no step)\n", exp_clock);
			continue;
		}
		ss.dump();

		// Eat the step.
		script_.steps.pop_front();

		// Write the save file.
		FILE* save = fopen(savefile_path_.c_str(), "wt");
		if (!save) {
			printf("Failed to write save file\n");
			return;
		}
		fprintf(save, "%u,%d,%d,%d,%d\n",
			exp_clock,
			reported_op_value[0],
			reported_op_value[1],
			reported_op_value[2],
			reported_op_value[3]);
		printf("Save: %u,%d,%d,%d,%d\n",
			exp_clock,
			reported_op_value[0],
			reported_op_value[1],
			reported_op_value[2],
			reported_op_value[3]);
		fclose(save);
		
		uint32_t scan;

		// Is this the last step?
		if (script_.steps.empty() == 1) {
			continue;
		}

		// Move to the next explicitly specified value for pitch.
		if (!ss.pitch_omitted) {
			script_step_t* later_step = NULL;
			for (std::list<script_step_t>::iterator scan = script_.steps.begin(); scan != script_.steps.end(); scan++) {
				if (!scan->pitch_omitted) {
					later_step = &*scan;
					break;
				}
			}
			if (!later_step) {
				printf("Internal error--no pitch specified after %u seconds\n", ss.seconds);
				return;
			}
			printf("From %u to %u seconds, changing pitch from %1.1f to %1.1f\n",
				ss.seconds, later_step->seconds, (float)ss.pitch / 10.f, (float)later_step->pitch / 10.f);
			send_operator_control_move(0, later_step->pitch, 1000 * (later_step->seconds - ss.seconds));
		}

		// Move to the next explicitly specified value for roll.
		if (!ss.roll_omitted) {
			script_step_t* later_step = NULL;
			for (std::list<script_step_t>::iterator scan = script_.steps.begin(); scan != script_.steps.end(); scan++) {
				if (!scan->roll_omitted) {
					later_step = &*scan;
					break;
				}
			}
			if (!later_step) {
				printf("Internal error--no roll specified after %u seconds\n", ss.seconds);
				return;
			}
			printf("From %u to %u seconds, changing roll from %1.1f to %1.1f\n",
				ss.seconds, later_step->seconds, (float)ss.roll / 10.f, (float)later_step->roll / 10.f);
			send_operator_control_move(1, later_step->roll, 1000 * (later_step->seconds - ss.seconds));
		}

		// Move to the next explicitly specified value for pipe.
		if (!ss.pipe_omitted) {
			script_step_t* later_step = NULL;
			for (std::list<script_step_t>::iterator scan = script_.steps.begin(); scan != script_.steps.end(); scan++) {
				if (!scan->pipe_omitted) {
					later_step = &*scan;
					break;
				}
			}
			if (!later_step) {
				printf("Internal error--no standpipe height specified after %u seconds\n", ss.seconds);
				return;
			}
			printf("From %u to %u seconds, changing standpipe height from %u to %u\n",
				ss.seconds, later_step->seconds, ss.pipe, later_step->pipe);
			send_operator_control_move(2, later_step->pipe, 1000 * (later_step->seconds - ss.seconds));
		}

		// Move to the next explicitly specified value for flow.
		if (!ss.flow_omitted) {
			script_step_t* later_step = NULL;
			for (std::list<script_step_t>::iterator scan = script_.steps.begin(); scan != script_.steps.end(); scan++) {
				if (!scan->flow_omitted) {
					later_step = &*scan;
					break;
				}
			}
			if (!later_step) {
				printf("Internal error--no flow specified after %u seconds\n", ss.seconds);
				return;
			}
			printf("From %u to %u seconds, changing flow from %u to %u\n",
				ss.seconds, later_step->seconds, ss.flow, later_step->flow);
			send_operator_control_move(3, later_step->flow, 1000 * (later_step->seconds - ss.seconds));
		}
	}
}

void model::wait_all_stopped()
{
	uint32_t stops = 0;

	// Stay in this loop until everything has stopped moving.
	printf("Waiting for operators to stop...\n");
	while (stops < 4) {
		// Process any message.
		process_message();

		// Count the number of stopped operators.
		stops = 0;
		for (uint8_t zop = 0; zop < 4; zop++) {
			if (reported_op_valid[zop] && !(reported_op_flags[zop] & 0x40)) {
				//printf("%u", zop);
				stops++;
			}
		}
		//printf("\n");
	}
}

void script_step_t::dump()
{
	char c_pitch[20] = { 0 };
	char c_roll[20] = { 0 };
	char c_pipe[20] = { 0 };
	char c_flow[20] = { 0 };

	if (!pitch_omitted) {
		sprintf(c_pitch, "%s%1.1f", (pitch == 0) ? "" : ((pitch > 0) ? "+" : ""), float(pitch) / 10);
	} else {
		strcpy(c_pitch, "---");
	}
	if (!roll_omitted) {
		sprintf(c_roll, "%s%1.1f", (roll == 0) ? "" : ((roll > 0) ? "+" : ""), float(roll) / 10);
	} else {
		strcpy(c_roll, "---");
	}
	if (!pipe_omitted) {
		sprintf(c_pipe, "%3u", pipe);
	} else {
		strcpy(c_pipe, "---");
	}
	if (!flow_omitted) {
		sprintf(c_flow, "%3u", flow);
	} else {
		strcpy(c_flow, "---");
	}
	printf("%6u %10s %10s %10s %10s\n", seconds, c_pitch, c_roll, c_pipe, c_flow);
};

bool script_t::read(const std::string& path_)
{
	FILE* script = fopen(path_.c_str(), "rt");
	if (!script) {
		printf("Could not find script file \"%s\"\n", path_.c_str());
		return false;
	}

	char line[100];	
	int line_num = 0;
	while (NULL != (fgets(line, 100, script))) {
		line_num++;

		// Ignore comments
		if (line[0] == '#') {
			continue;
		}

		std::vector<std::string> tokens = tokenize(std::string(line));
		if ((tokens.size() != 5) || (tokens[0].empty())) {
			printf("\"%s\" line %u: syntax error\n", path_.c_str(), line_num);
			return false;
		}
		for (int i = 0; i < 5; i++) {
			printf("%d: \"%s\"\n", i, tokens[i].c_str());
		}

		script_step_t step;
		step.seconds = (unsigned int)atoi(tokens[0].c_str());
		step.pitch_omitted = (tokens[1].empty());
		if (!step.pitch_omitted) {
			step.pitch = (int)(atof(tokens[1].c_str()) * 10.);
			printf("pitch = %d\n", step.pitch);
		}
		step.roll_omitted = (tokens[2].empty());
		if (!step.roll_omitted) {
			step.roll = (int)(atof(tokens[2].c_str()) * 10.);
			printf("roll = %d\n", step.roll);
		}
		step.pipe_omitted = (tokens[3].empty());
		if (!step.pipe_omitted) {
			step.pipe = (int)atoi(tokens[3].c_str());
			printf("pipe = %d\n", step.pipe);
		}
		step.flow_omitted = (tokens[4].empty());
		if (!step.flow_omitted) {
			step.flow = (int)atoi(tokens[4].c_str());
			printf("flow = %d\n", step.pipe);
		}

		// Bounds checks
		if (!steps.empty() && (step.seconds <= steps.back().seconds)) {
			printf("\"%s\" line %u: time is less than that of previous line\n", path_.c_str(), line_num);
			return false;
		}
		if (steps.empty() && step.pitch_omitted) {
			printf("\"%s\" line %u: pitch cannot be omitted on first line\n", path_.c_str(), line_num);
		}
		if (!step.pitch_omitted && ((step.pitch < MIN_PITCH) || (step.pitch > MAX_PITCH))) {
			printf("\"%s\" line %u: pitch must be between %.1f and %.1f (degrees)\n", path_.c_str(), line_num,
				((float)MIN_PITCH) / 10.0f, ((float)MAX_PITCH) / 10.0f);
			return false;
		}
		if (steps.empty() && step.roll_omitted) {
			printf("\"%s\" line %u: roll cannot be omitted on first line\n", path_.c_str(), line_num);
		}
		if (!step.roll_omitted && ((step.roll < MIN_ROLL) || (step.pitch > MAX_ROLL))) {
			printf("\"%s\" line %u: roll must be between %.1f and %.1f (degrees)\n", path_.c_str(), line_num,
				((float)MIN_ROLL) / 10.0f, ((float)MAX_ROLL) / 10.0f);
			return false;
		}
		if (steps.empty() && step.pipe_omitted) {
			printf("\"%s\" line %u: standpipe height cannot be omitted on first line\n", path_.c_str(), line_num);
		}
		if (!step.pipe_omitted && ((step.pipe < MIN_PIPE) || (step.pipe > MAX_PIPE))) {
			printf("\"%s\" line %u: standpipe height must be between %u and %u (mm)\n", path_.c_str(), line_num,
				MIN_PIPE, MAX_PIPE);
			return false;
		}
		if (steps.empty() && step.flow_omitted) {
			printf("\"%s\" line %u: flow cannot be omitted on first line\n", path_.c_str(), line_num);
		}
		if (!step.flow_omitted && ((step.flow < MIN_FLOW) || (step.pipe > MAX_FLOW))) {
			printf("\"%s\" line %u: flow must be between %u and %u (mL/s)\n", path_.c_str(), line_num,
				MIN_FLOW, MAX_FLOW);
			return false;
		}

		// Add to step list.
		steps.push_back(step);
	}

	// Cannot have any dangling omitted values on last line.
	if (!steps.empty()) {
		script_step_t& ss = steps.back();
		if (ss.pitch_omitted || ss.roll_omitted || ss.pipe_omitted || ss.flow_omitted) {
			printf("\"%s\": last line must completely specify all values\n", path_.c_str());
			return false;
		}
	}

	return true;
}

void script_t::discard_before(uint32_t seconds_)
{
	while (!steps.empty() && (steps.front().seconds < seconds_)) {
		steps.pop_front();
	}
}

void script_t::dump()
{
	for (std::list<script_step_t>::iterator i = steps.begin(); i != steps.end(); i++) {
		i->dump();
	}
}

int main(int argc, char** argv)
{
	srand(time(NULL));

	std::string script_name;
	std::string savefile_name;
	script_t script;
	uint32_t exp_clock = 0;
	printf("%s\n", argv[0]);

	int mode = 0;
	if (strstr(argv[0], "em4-random")) {
		mode = MODE_RANDOM;
	} else if (strstr(argv[0], "em4-reset")) {
		mode = MODE_RESET;
	} else if (strstr(argv[0], "em4-start")) {
		if (argc <= 1) {
			printf("Must specify script name\n");
			return 1;
		}

		// Read the script.
		script_name = argv[1];
		if (!script.read(script_name)) {
			return 1;
		}
		printf("Script:\n");
		script.dump();

		// Determine the restart file name from the script name.
		savefile_name = std::string(".") + script_name + std::string(".save");

		// Start at the beginning of the experiment.
		exp_clock = 0;

		mode = MODE_START;
	} else if (strstr(argv[0], "em4-resume")) {
		if (argc <= 1) {
			printf("Must specify script name\n");
			return 1;
		}
		script_name = argv[1];
		if (!script.read(script_name)) {
			return 1;
		}

		// Read the initial conditions from the restart file.
		script_step_t restart_step;
		savefile_name = std::string(".") + script_name + std::string(".save");
		FILE* savefile = fopen(savefile_name.c_str(), "rt");
		if (!savefile) {
			printf("Could not find save file; try em4-start instead\n");
			return 1;
		}
		if (5 != fscanf(savefile, "%u,%d,%d,%d,%d", &restart_step.seconds,
				&restart_step.pitch, &restart_step.roll,
				&restart_step.pipe, &restart_step.flow)) {
			printf("Error in save file; try em4-start instead\n");
			return 1;
		}
		fclose(savefile);

		// Remove any steps from the model before the restart step.
		script.discard_before(restart_step.seconds);
		exp_clock = restart_step.seconds;

		// Push the initial condition onto the front of the script.
		printf("Restore condition:\n");
		restart_step.dump();
		script.steps.push_front(restart_step);

		printf("Updated script:\n");
		script.dump();
		mode = MODE_RESUME;
	}

	try {
		model em4r("10.0.8.200", 40000);

		if (mode == MODE_RANDOM) {
			em4r.random_movement();
		}
		else if ((mode == MODE_START) || (mode == MODE_RESUME)) {
			em4r.run_script(script, savefile_name, exp_clock);
		}
	}

	catch (const std::string& s_) {
		printf("error: %s\n", s_.c_str());
	}
}
