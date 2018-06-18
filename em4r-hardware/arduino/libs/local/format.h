#pragma once

#include <Arduino.h>

// Create an Arduino string using printf semantics.
String Format(const char* format_, ...) __attribute__((format (printf, 1, 2)));
