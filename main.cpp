#include <string.h>
#include <string>
#include "model.h"
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>







int main(int argc, char** argv)
{
	srand(time(NULL));

	std::string script_name;
	std::string script_path;
	std::string savefile_name;
	std::string folder_path;
	std::string home_path;
	uint8_t jop;
	int16_t jval;

	script_t script;
	uint32_t exp_clock = 0;
	//printf("%s\n", argv[1]);
	//printf("%s\n", (getenv ("HOME")));
	//printf("%s\n", script_t.path_.c_str());

/*
	int mode = 0;
	if (strstr(argv[0], "em4-randomdry")) {
		mode = MODE_RANDOMDRY;
	} else if (strstr(argv[0], "em4-chill")) {
		mode = MODE_CHILL;
	} else if (strstr(argv[0], "em4-random")) {
		mode = MODE_RANDOM;
	} else if (strstr(argv[0], "em4-reset")) {
		mode = MODE_RESET;
	} else if (strstr(argv[0], "em4-start")) {
		if (argc <= 1) {
			printf("Must specify script name\n");
			return 1;
		}
*/
	//Main switch statement for mode selection
	int mode = 0;
	if (strstr(argv[1], "reset")) {
		mode = MODE_RESET;
	} else if (strstr(argv[1], "start")) {
		if (argc <= 1) {
			printf("Must specify script name\n");
			return 1;
		}

		// Read the script.
		script_name = argv[2];
		home_path = getenv ("HOME");
		folder_path = home_path + "/Desktop/em4r_scripts/";
		script_path = folder_path + script_name;
		if (!script.read(script_path)) {
			return 1;
		}
		printf("Script: %s\n", script_name.c_str());
		script.dump();
		
		
		//printf("%s\n", script_name.c_str());
		// Determine the restart file name from the script name.
		savefile_name = folder_path + std::string(".") + script_name + std::string(".save");
		//printf("%s\n", savefile_name.c_str());

		// Start at the beginning of the experiment.
		exp_clock = 0;
		mode = MODE_START;
	} else if (strstr(argv[1], "resume")) {
		if (argc <= 1) {
			printf("Must specify script name\n");
			return 1;
		}
		script_name = argv[2];
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
	// Jog specific values
	} else if (strstr(argv[1], "jog")){
		//printf("jogging\n");
		mode = MODE_JOG;
	} else if (strstr(argv[1], "home")){
		mode = MODE_HOME;
	} else if (strstr(argv[1], "em4-hipitch")) {
		mode = MODE_HIPITCH;
	} else if (strstr(argv[1], "em4-maxmove")) {
		mode = MODE_MAXMOVE;
	} else if (strstr(argv[1], "encmon")) {
		mode = MODE_ENCMON;
	} else if (strstr(argv[1], "random")) {
		mode = MODE_RANDOM;
	} else if (strstr(argv[1], "randomdry")) {
		mode = MODE_RANDOMDRY;
	}

//Main block for function calls
	try {
		model em4r("10.0.8.200", 40000);

		printf("Model Initialized\n");
		
		if (mode == MODE_RANDOM) {
			em4r.random_movement(true);
		}
		else if (mode == MODE_RANDOMDRY) {
			em4r.random_movement(false);
		}
		else if (mode == MODE_CHILL) {
			em4r.chill_movement();
		}
		else if (mode == MODE_RESET) {
			em4r.reset();
		}
		else if (mode == MODE_HOME){
			em4r.home();
		}
		else if (mode == MODE_JOG){
			printf("Jogging\n");
			
			//jog function can be called with or without a time argument, given in seconds
			if(argc<5)
			{
				em4r.jog(argv[2], (int16_t)atoi(argv[3]));
			}
			else
			{
				em4r.jog(argv[2], (int16_t)atoi(argv[3]), ((uint32_t)atoi(argv[4])*1000));
			}
		}
		else if (mode == MODE_HIPITCH) {
			printf("test: high pitch\n");
			em4r.high_pitch();
		}
		else if (mode == MODE_MAXMOVE) {
			printf("test: maximum movement\n");
			em4r.maximum_movement();
		}
		else if (mode == MODE_ENCMON) {
			printf("test: encoder monitoring\n");
			em4r.encoder_monitor();
		}
		else if ((mode == MODE_START) || (mode == MODE_RESUME)) {
			em4r.reset();
			em4r.run_script(script, savefile_name, exp_clock);
		}
		else {
			printf("\nno instructions given");
		}
	}
	//error handling
	catch (const std::string& s_) {
		printf("error: %s\n", s_.c_str());
	}
}
