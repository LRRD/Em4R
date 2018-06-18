
# Path to the Arduino IDE and tools, which can be locally overridden.
ifeq (,$(ARDUINO_ROOT))
  ARDUINO_ROOT := $(shell ls /opt | grep "^arduino-" | tail -1)
  ifeq (,$(ARDUINO_ROOT))
    $(error ARDUINO_ROOT not set, and no /opt/arduino-... installation found)
  else
    ARDUINO_ROOT := /opt/$(ARDUINO_ROOT)
    $(info ARDUINO_ROOT not set; using $(ARDUINO_ROOT))
  endif
endif

# Paths within the Arduino installation.
AVR_HW_BIN=$(ARDUINO_ROOT)/hardware/tools/avr/bin
AVR_CC=$(AVR_HW_BIN)/avr-gcc
AVR_CXX=$(AVR_HW_BIN)/avr-g++
# Special ar (gcc-ar) that supports LTO
AVR_AR=$(AVR_HW_BIN)/avr-gcc-ar
AVR_RANLIB=$(AVR_HW_BIN)/avr-gcc-ranlib
AVR_OBJCOPY=$(AVR_HW_BIN)/avr-objcopy
AVR_DUDE=$(AVR_HW_BIN)/avrdude

# Arduino core library directories.
LIB_CORE_SRC=$(ARDUINO_ROOT)/hardware/arduino/avr/cores/arduino
LIB_VAR_SRC=$(ARDUINO_ROOT)/hardware/arduino/avr/variants/mega

# Other libraries of interest which we don't build, but may need to include.
LIB_ETHERNET_SRC=$(ARDUINO_ROOT)/libraries/Ethernet/src
LIB_SPI_SRC=$(ARDUINO_ROOT)/hardware/arduino/avr/libraries/SPI/src
LIB_WIRE_SRC=$(ARDUINO_ROOT)/hardware/arduino/avr/libraries/Wire/src

