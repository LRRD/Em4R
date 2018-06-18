#include "format.h"

#include <stdio.h>

String Format(const char* format_, ...)
{
	// Unwrap variable arguments.
	va_list args;
	va_start(args, format_);

	// Format into a stack buffer.
	char text[256];
	vsnprintf(text, sizeof(text), format_, args);

	// Construct a String from the text.
	return String(text);
}
