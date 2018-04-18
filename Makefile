PROGRAM=gcc

em4rtest: em4rtest.cpp
	g++ em4rtest.cpp -o em4rtest
	ln -s em4rtest em4-start
	ln -s em4rtest em4-resume
	ln -s em4rtest em4-reset

clean:
	rm -f em4rtest
	rm -f em4-start
	rm -f em4-reset
	rm -f em4-resume
	