# Arduino SD library settings
#xIB_SRC := /home/user/SdFat-master/SdFat/src
#xLIB_SRC_SUBDIRS := SdSpiCard FatLib
LIB_SRC := $(ARDUINO_ROOT)/libraries/SD/src
LIB_SRC_SUBDIRS := utility
EXTRA_INCLUDES := $(LIB_SPI_SRC)
