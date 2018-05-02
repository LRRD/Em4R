PROGRAM=gcc

em4r: em4r.cpp
	g++ em4r.cpp -o em4r

install:
	g++ em4r.cpp -o /usr/bin/em4r

clean:
	rm -f em4r

remove:
	rm -f /usr/bin/em4r	
	