# Em4R
## Program for communicating with the Em4R Robotic Dual-Tilt Stream table


###Running scripts
You can now call the program from any directory. To run a script, simply type

`$em4r start "script-name"`

Scripts are no longer stored in the working directory, and can instead be found in the folder
 `~/Desktop/em4r_scripts`

###Homing the Table
There is now a home functionality, allowing you to set the pitch, roll, and flow to 0, and the standpipe to 100 using
`$em4r home`

###Jogging to Specific Values
You can set any operator to a specific value using the jog function.

`$em4r jog "operator" "value" "time"`

Operators are pitch, roll, pipe, and flow. Values vary per operator and are listed here.

Multiply any degree values by a factor of ten.

Time is given in seconds, and can be decimals.

The time parameter is optional. If omitted, it defaults to 10 seconds.

- Pitch takes any value from -10 to 36 (-1 degree to 3.6 degrees)
- Roll takes any value from -34 to 34 (-3.4 degrees to 3.4 degrees)
- Pipe takes any value from 10 to 100 (in millimeters)
- Flow takes any value from 0 to 960 (flow only starts after 500 or so)

## Instructions for Building from Source

Clone or download this repository, and navigate into the root folder.

Simply running 

`$make` 

will compile the program into the current directory, and is executable using `./em4r`. To remove, just run 

`make clean`

If you want to install system wide, run

 `$sudo make install`

  and supply admin credentials.
  To uninstall, run
  
   `$make remove`

from the source directory.
