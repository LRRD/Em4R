
#include "preferences.h"

#include <debug.h>

#include <string.h>
#include <errno.h>

#include <SD.h>

// A preference.
struct pref_t;
struct pref_t
{
	~pref_t();
	pref_t();

	char* name;
	char* value;
	struct pref_t* next;
};

pref_t::~pref_t()
{
	if (name) {
		free(name);
	}
	if (value) {
		free(value);
	}
}

pref_t::pref_t() :
	name(nullptr),
	value(nullptr),
	next(nullptr)
{
}

struct Preferences::Impl
{
	// Implement main-class methods.
	~Impl();
	Impl();
	const char* Get(const char* name_, const char* default_);

	// The chain of preferences.
	struct pref_t* head;
};

Preferences::Impl::~Impl()
{
	// Deallocate any preferences.
	while (head) {
		struct pref_t* next = head->next;
		delete head;
		head = next;
	}
}

Preferences::Impl::Impl() :
	head(nullptr)
{
#if 0
	if (!SD.begin(4)) {
		DBG_ERR("SD card not found");
		return;
	} else {
		DBG_ERR("SD card found");
	}
#endif

	// Open the preferences file.
	File f = SD.open("EM4R.TXT");
	if (!f) {
		DBG_ERR("EM4R.TXT not found; default preferences.");
		return;
	}

	// Read all preferences.
	char line[81];
	char c;
	struct pref_t* tail = nullptr;
	uint8_t eof = 0;
	while (!eof) {

		// Read the next line.
		uint8_t eol = 0;
		uint8_t pos = 0;
		uint8_t eq = 0;
		uint8_t skip = 0;
		while (!eol) {
			// Get the next byte.
			c = f.read();

			// Check for EOF (which also terminates a line).
			if (c == (char)-1) {
				eof = 1;
				eol = 1;
				continue;
			}
			if ((c == '\n') || (c == '\r')) {
				eol = 1;
				continue;
			}

			// Skip other non-printables, or characters until EOL if skipping.
			if ((c <= ' ') || skip) {
				continue;
			}

			// Keep track of the position of the equals sign.
			if (c == '=') {
				if (!pos || eq) {
					DBG_ERR("Preference syntax error.");
					skip = 1;
					continue;
				}
				eq = pos;
			}

			// Accumulate in the line buffer, ensuring NUL termination.
			if ((pos + 1) >= sizeof(line)) {
				DBG_ERR("Preference line too long.");
				skip = 1;
				continue;
			}
			line[pos++] = c;
			line[pos] = '\0';
		}

		// Process non-empty lines with an equal sign not the first character.
		if (eol && pos && eq) {

			// Split the line at the = and build up a preference.
			line[eq] = '\0';
			pref_t* p = new pref_t;
			p->name = strdup(line);
			p->value = strdup(line + eq + 1);

			// Add to the chain.
			if (tail) {
				tail->next = p;
			} else {
				head = p;
			}
			tail = p;

//			DBG_ERR("P: %s = %s", p->name, p->value);
		}
	}
}

const char* Preferences::Impl::Get(const char* name_, const char* default_)
{
	// Walk the preferences chain looking for the preference.
	pref_t* p = head;
	while (p) {
		if (!strcmp(p->name, name_)) {
			return p->value;
		}
		p = p->next;
	}

	// Not found; return the default.
	return default_;
}

Preferences::~Preferences()
{
	delete impl;
}


Preferences::Preferences() :
	impl(new Impl)
{
}

const char* Preferences::Get(const char* name_, const char* default_)
{
	return impl->Get(name_, default_);
}

uint16_t Preferences::GetUint16(const char* name_, uint16_t default_)
{
	uint16_t r = default_;
	const char* s = Get(name_, nullptr);
	if (s) {
		char* end = nullptr;
		unsigned long v = strtoul(name_, &end, 0);
		if (!errno && (v <= 0xFFFF)) {
			r = v;
		}
	}

//	DBG_ERR("P: %s = Unsigned 0x%04X", name_, r);
	return r;
}

void Preferences::GetIPAddress(uint8_t* ip_, const char* name_, const uint8_t* default_)
{
	const char* s = Get(name_, nullptr);
	if (!s || (4 != sscanf(s, "%hhu.%hhu.%hhu.%hhu", ip_, ip_ + 1, ip_ + 2, ip_ + 3))) {
		memcpy(ip_, default_, 4);
	}

//	DBG_ERR("P: %s = IP Address %hhu.%hhu.%hhu.%hhu", name_, ip_[0], ip_[1], ip_[2], ip_[3]);
}

void Preferences::GetMACAddress(uint8_t* mac_, const char* name_, const uint8_t* default_)
{
	const char* s = Get(name_, nullptr);
	if (!s || (6 != sscanf(s, "%02hhx:%02hhx:%02hhx:%02hhx:%02hhx:%02hhx", mac_, mac_ + 1, mac_ + 2, mac_ + 3, mac_ + 4, mac_ + 5))) {
		memcpy(mac_, default_, 6);
	}

//	DBG_ERR("P: %s = MAC Address %02hhX:%02hhX:%02hhX:%02hhX:%02hhX:%02hhX", name_, mac_[0], mac_[1], mac_[2], mac_[3], mac_[4], mac_[5]);
}
