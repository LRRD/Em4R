
DIRS := arduino/libs/core \
	arduino/libs/wire \
	arduino/libs/spi \
	arduino/libs/ethernet \
	arduino/libs/sd \
	arduino/libs/local \
	arduino/apps/blink \
	arduino/apps/em4rctrl \
	arduino/apps/em4rdriv \

all:
	$(foreach dir, $(DIRS), make -C $(dir) all;)

clean:
	$(foreach dir, $(DIRS), make -C $(dir) clean;)
