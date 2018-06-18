#pragma once

#include <stdint.h>

// Compute a CRC value on the specified buffer.
uint8_t crc8(const uint8_t* buf_, uint16_t count_);
