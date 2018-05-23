PROGRAM=gcc

em4r: main.cpp
	g++ main.cpp model.cpp -o em4r

install:
	g++ main.cpp model.cpp -o /usr/bin/em4r
	cp em4r_menu /usr/bin/em4r_menu
	chmod 755 /usr/bin/em4r
	chmod 755 /usr/bin/em4r_menu
	mkdir -p ~/Desktop/em4r_scripts
clean:
	rm -f em4r

remove:
	rm -f /usr/bin/em4r	
	