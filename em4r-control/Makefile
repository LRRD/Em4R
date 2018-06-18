PROGRAM=gcc

em4r: main.cpp
	g++ main.cpp model.cpp -o em4r

install:
	g++ main.cpp model.cpp -o /usr/bin/em4r
	cp em4r_menu /usr/bin/em4r_menu
	cp etch-a-sketch /usr/bin/etch-a-sketch
	chmod 755 /usr/bin/em4r
	chmod 755 /usr/bin/em4r_menu
	chmod 755 /usr/bin/etch-a-sketch
	mkdir -p ~/Desktop/em4r_scripts
clean:
	rm -f em4r

remove:
	rm -f /usr/bin/em4r	
	rm -f /usr/bin/em4r_menu
	rm -f /usr/bin/etch-a-sketch
	