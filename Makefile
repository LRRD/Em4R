PROGRAM=gcc

em4rtest: em4rtest.cpp
	g++ em4rtest.cpp -o em4rtest
	ln -s em4rtest em4-start
	ln -s em4rtest em4-resume
	ln -s em4rtest em4-reset

clean:
	rm -f em4rtest
	rm em4-start
	rm em4-reset
	rm em4-resume
	