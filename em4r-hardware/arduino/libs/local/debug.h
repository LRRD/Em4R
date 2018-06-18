#pragma once

#include <stdint.h>

// Debug message categories.
#define DBGC_ERR	0x00000001U	// Important information
#define	DBGC_ADC	0x00000010U	// ADS1115 ADCs
#define	DBGC_ENCODE	0x00000020U	// Encoder readings
#define	DBGC_RS485	0x00000200U	// RS485 traffic
#define	DBGC_GECKO	0x00000400U	// GM215 motor controllers

#define	DBGC_LINK	0x00001000U	// Verbose ControlLink
#define	DBGC_LINK_ERR	0x00002000U	// ControlLink errors

#define	DBGC_DRIVER	0x00010000U	// Driver-side stuff (OK in realtime)
#define	DBGC_MOTOR	0x00020000U	// Motor control (breaks realtime)
#define	DBGC_JOG	0x00040000U	// Status when jog released (breaks realtime)

// Which categories are compiled into the code (overridable with -D)
#ifndef DBG_COMPILE
#define	DBG_COMPILE	(DBGC_ERR | DBGC_ADC | DBGC_ENCODE | DBGC_LINK | DBGC_LINK_ERR | DBGC_DRIVER | DBGC_MOTOR | DBGC_JOG)
#endif

// Debug bitmap
typedef uint32_t debug_t;

// Initialize the debug console.
class HardwareSerial;
void debug_init(HardwareSerial* serial);

// Set the mask of debug codes which are logged.
void debug_set_mask(debug_t mask_);

// Determine if a given debug code is relevant under the current debug mask.
bool debug_is_relevant(debug_t mask_);

// Global debug function which logs to console.
void debug_printf(debug_t code_, const char* format, ...) __attribute__((format (printf, 2, 3)));

// Hang the system.
void debug_hang();

// ASSERT macro.
#define ASSERT(X) \
	{ \
		if (X); \ 
		else { \
			DBG_ERR("Assertion failed at %s:%d: (%s) is false\n", __FILE__, __LINE__, #X); \
			debug_hang(); \
		} \
	}

// Debug macros, one per category

#if (DBG_COMPILE & DBGC_ERR)
#define	DBG_ERR(args...)	debug_printf(DBGC_ERR, ##args)
#else
#define DBG_ERR(args...)
#endif

#if (DBG_COMPILE & DBGC_ADC)
#define	DBG_ADC(args...)	debug_printf(DBGC_ADC, ##args)
#else
#define DBG_ADC(args...)
#endif

#if (DBG_COMPILE & DBGC_ENCODE)
#define	DBG_ENCODE(args...)	debug_printf(DBGC_ENCODE, ##args)
#else
#define DBG_ENCODE(args...)
#endif

#if (DBG_COMPILE & DBGC_RS485)
#define	DBG_RS485(args...)	debug_printf(DBGC_RS485, ##args)
#else
#define DBG_RS485(args...)
#endif

#if (DBG_COMPILE & DBGC_GECKO)
#define	DBG_GECKO(args...)	debug_printf(DBGC_GECKO, ##args)
#else
#define DBG_GECKO(args...)
#endif

#if (DBG_COMPILE & DBGC_LINK)
#define	DBG_LINK(args...)	debug_printf(DBGC_LINK, ##args)
#else
#define DBG_LINK(args...)
#endif

#if (DBG_COMPILE & DBGC_LINK_ERR)
#define	DBG_LINK_ERR(args...)	debug_printf(DBGC_LINK_ERR, ##args)
#else
#define DBG_LINK_ERR(args...)
#endif

#if (DBG_COMPILE & DBGC_DRIVER)
#define	DBG_DRIVER(args...)	debug_printf(DBGC_DRIVER, ##args)
#else
#define DBG_DRIVER(args...)
#endif

#if (DBG_COMPILE & DBGC_MOTOR)
#define	DBG_MOTOR(args...)	debug_printf(DBGC_MOTOR, ##args)
#else
#define DBG_MOTOR(args...)
#endif

#if (DBG_COMPILE & DBGC_JOG)
#define	DBG_JOG(args...)	debug_printf(DBGC_JOG, ##args)
#else
#define DBG_JOG(args...)
#endif
