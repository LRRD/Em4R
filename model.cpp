#include "model.h"
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>


#include <string>
#include <list>


#define	MIN_PITCH	-30	// -15 degrees
#define	MAX_PITCH	35	// 3.5 degrees
#define	MIN_ROLL	-36	// -3.6 degrees
#define	MAX_ROLL	36	// 3.6 degrees
#define	MIN_PIPE	5	// 5mm
#define	MAX_PIPE	100	// 100mm
#define	MIN_FLOW	0	// 0 mL/s
#define	MAX_FLOW	960	// 960 mL/s

#define	OPERATOR_COUNT	4

#define COLOR_RESET   "\x1b[0m"
#define COLOR_RED     "\x1b[31m"





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
	//printf("got it");
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

void model::push_operator_control_reset(std::vector<uint8_t>& v_, uint8_t instance_)
{
	push_u8(v_, 8);
	push_u8(v_, instance_);
	push_u16_nbo(v_, 0x5043);

	push_i16_nbo(v_, 0);
	push_u8(v_, 3);	// reset
	push_u8(v_, 0);
	
	push_u32_nbo(v_, 0);
}

void model::process_message(bool debug_)
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
//				if (debug_) {
//					printf("status: encoder %u is at %d\n", instance, pos);
//				}
				reported_enc_valid[instance] = 1;
				reported_enc_value[instance] = pos;
				reported_enc_mv[instance] = mv;
			}
		}

		// Print updates.
		if (debug_) {
			printf("encoders: ");
			for (int enc = 0; enc < 4; enc++) {
				if (!reported_enc_valid[enc]) {
					printf("-------- ");
				} else {
					printf("%8d ", reported_enc_value[enc]);
				}
			}
			printf("\n");
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
	//printf("\n%u: -> %d in %u msec\n", instance_, requested_value_, relative_ms_);
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

void model::send_operator_control_reset(uint8_t instance_)
{
	std::vector<uint8_t> v;
	push_system_control(v, 0);
	push_operator_control_reset(v, instance_);
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
	time_t start = time(NULL);
	time_t last = start;

	uint32_t saved_clock = model_clock;
	do {
		process_message();
		if ((time(NULL) - last) > 2) {
			last = time(NULL);
			send_system_nop();
		}

		//printf("%u == %u\n", saved_clock, model_clock);
	} while (saved_clock == model_clock);
#endif
}

void model::random_movement(bool pump_)
{
	printf("Random movement with%s pump\n", pump_ ? "" : "out");
	for (;;) {
		// Time to do something?
		time_t now = time(NULL);

		// Only run often enough to make sure we saw a status update from each operator.
		if ((now - last_change) > 2) {
			last_change = now;
			// See if we can do anything with any of the operators.
			for (uint8_t zop = 0; zop < (pump_ ? 4 : 2); zop++) {
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

void model::chill_movement()
{
	printf("Low movement without pump\n");
	for (;;) {
		// Time to do something?
		time_t now = time(NULL);

		// Only run often enough to make sure we saw a status update from each operator.
		if ((now - last_change) > 2) {
			last_change = now;
			// See if we can do anything with any of the operators.
			for (uint8_t zop = 0; zop < 2; zop++) {
				// If the operator has not reported a value, or it's moving, skip it.
				if (!reported_op_valid[zop] || (reported_op_flags[zop] & 0x40)) {
					continue;
				}

				// Move/change to a random legal value in a time somewhere between 0 and 10 seconds.
				int16_t avg = (maxs[zop] + mins[zop]) / 2;
				int16_t spread = (maxs[zop] - mins[zop]) / 4;
				int16_t val = avg - (spread / 2) + (((rand() % 1000) * spread) / 1000);
				uint32_t ms = 2000 + (rand() % 30000);
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

void model::maximum_movement()
{
	bool max_pitch = false;
	bool max_roll = false;
	for (;;) {
		// Time to do something?
		time_t now = time(NULL);

		// Only run often enough to make sure we saw a status update from each operator.
		if ((now - last_change) > 2) {
			last_change = now;
			// See if we can do anything with any of the operators.
			for (uint8_t zop = 0; zop < 2; zop++) {
				// If the operator has not reported a value, or it's moving, skip it.
				if (!reported_op_valid[zop] || (reported_op_flags[zop] & 0x40)) {
					continue;
				}

				// Move/change to min or max value in zero time.
				int16_t val;
				if (zop == 0) {
					val = max_pitch ? MAX_PITCH : MIN_PITCH;
					max_pitch = !max_pitch;
				} else {
					val = max_roll ? MAX_ROLL : MIN_ROLL;
					max_roll = !max_roll;
				}
				printf("control: telling operator %u to move to %d\n", zop, val);
				send_operator_control_move(zop, val, 0);
			}

			// Keep the connection alive.
			send_system_nop();
		}

		// Process any message.
		process_message();
	}
}

void model::reset()
{
	for (uint8_t zop = 0; zop < 4; zop++) {
		//printf("control: telling operator %u to reset\n", zop);
		send_operator_control_reset(zop);
	}
	printf("Operators reset\n");
}

void model::encoder_monitor()
{
	for (;;) {
		// Time to do something?
		time_t now = time(NULL);

		// Only run often enough to make sure we saw a status update from each operator.
		if ((now - last_change) > 1) {
			last_change = now;

			// Keep the connection alive.
			send_system_nop();
		}

		// Process any message.
		process_message(true);
	}
}

void model::high_pitch()
{
	bool decrease_pitch = true;
	int loop = 0;

	for (;;) {
		// Keep the connection alive.
		send_system_nop();

		usleep(100000);

		time_t now = time(NULL);
		if (now != last_change) {
			last_change = now;

			// If the operator has not reported a value, or it's moving, skip it.
			if (!reported_op_valid[0] || (reported_op_flags[0] & 0x40)) {
				continue;
			}

			// How long should it take to do the move?
			if (loop > 9) {
				loop = 0;
			}
			int msec = 4000 + (loop * 1000);
			loop++;
			// override;

			send_operator_control_move(0, decrease_pitch ? 25 : 35, msec);
			decrease_pitch = !decrease_pitch;
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
	//printf("did we stop?\n");

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
	printf("\n\n   ______________    ____  ______   _____ __________  ________  ______\n  / ___/_  __/   |  / __ \\/_  __/  / ___// ____/ __ \\/  _/ __ \\/_  __/\n  \\__ \\ / / / /| | / /_/ / / /     \\__ \\/ /   / /_/ // // /_/ / / /\n ___/ // / / ___ |/ _, _/ / /     ___/ / /___/ _, _// // ____/ / /\n ____//_/ /_/  |_/_/ |_| /_/     /____/\\____/_/ |_/___/_/     /_/\n\n\n");
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
			printf(COLOR_RED "\rTime = %u" COLOR_RESET, exp_clock);
	
			fflush(stdout);
			continue;
		}
		//printf("\r");
		//ss.dump();

		// Eat the step.
		//printf("\n");
		script_.steps.pop_front();
		// Write the save file.
		FILE* save = fopen(savefile_path_.c_str(), "wt");
		if (!save) {
			printf("Failed to write save file\n");
			return;
		}
		
		fprintf(save, "%u,%d,%d,%d,%d",
			exp_clock,
			reported_op_value[0],
			reported_op_value[1],
			reported_op_value[2],
			reported_op_value[3]);
		printf("\rSave: %u,%d,%d,%d,%d",
			exp_clock,
			reported_op_value[0],
			reported_op_value[1],
			reported_op_value[2],
			reported_op_value[3]);
		
		fclose(save);
		printf("\n--------------------\n");
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
	printf("\n");
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
	//printf("test path:\n");
	//printf("%s\n", path_.c_str());
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
			printf("\"%s\" line %u: syntax error in script\n", path_.c_str(), line_num);
			return false;
		}
		for (int i = 0; i < 5; i++) { 
			//printf("%d: \"%s\"\n", i, tokens[i].c_str());
		}

		script_step_t step;
		step.seconds = (unsigned int)atoi(tokens[0].c_str());
		step.pitch_omitted = (tokens[1].empty());
		if (!step.pitch_omitted) {
			step.pitch = (int)(atof(tokens[1].c_str()) * 10.);
			//printf("pitch = %d\n", step.pitch);
		}
		step.roll_omitted = (tokens[2].empty());
		if (!step.roll_omitted) {
			step.roll = (int)(atof(tokens[2].c_str()) * 10.);
			//printf("roll = %d\n", step.roll);
		}
		step.pipe_omitted = (tokens[3].empty());
		if (!step.pipe_omitted) {
			step.pipe = (int)atoi(tokens[3].c_str());
			//printf("pipe = %d\n", step.pipe);
		}
		step.flow_omitted = (tokens[4].empty());
		if (!step.flow_omitted) {
			step.flow = (int)atoi(tokens[4].c_str());
			//printf("flow = %d\n", step.pipe);
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

void model::jog(const char* opselect, int16_t jval, uint32_t millis)
{
	uint8_t jop = 0;
	//get command line arguments for operator selection
	try {
		if (strstr(opselect, "pitch")){
			jop = 0;
		}
		else if (strstr(opselect, "roll")){
			jop = 1;
		}
		else if (strstr(opselect, "pipe")){
			jop = 2;
		}
		else if (strstr(opselect, "flow")){
			jop = 3;
		}
		else 
		{
			throw("Invalid Input");
		}
	
	}

	catch(char const* err)
	{
		printf("\n%s is not an operator\n\nExiting...\n", opselect);
		exit(1);
	}

	printf("\nJogging %s to %u\n", opselect, jval);
	
	//send values to model
	send_operator_control_move(jop, jval, millis);
}

void model::home()
{	
	//home all operators
	send_operator_control_move(0, 0, 10000);
	send_operator_control_move(1, -1, 10000);
	send_operator_control_move(3, 0, 10000);
	send_operator_control_move(2, 100, 10000);
	printf("Operators homed\n");
}
