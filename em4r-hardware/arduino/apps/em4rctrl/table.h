#pragma once

#include <stdint.h>

// Initialize the "end of table" entry.
#define	END_OF_TABLE_XY	((int16_t)0x8000)
#define	END_OF_TABLE	{ END_OF_TABLE_XY, END_OF_TABLE_XY }

class Table
{
public:
	// One entry.
	struct entry_t
	{
		int16_t x;
		int16_t y;
	};

	// Construct from a table and a number of entries.
	// interp_ is true to do piecewise linear interpolation.
	// interp_ is false to do nearest-value matching.
	Table(bool interp_, const entry_t* table_);

	// Map from an X value to a Y value.
	int16_t MapXToY(int16_t x_) const;

	// Map from a Y value to an X value.
	int16_t MapYToX(int16_t y_) const;

	// Get the first entry.
	const entry_t& First() const;
	const entry_t& Last() const;

	// Do a straight linear interpolation.
	static int16_t Interpolate(int16_t vala_, int16_t mina_, int16_t maxa_, int16_t minb_, int16_t maxb_);
	
private:
	bool interp;
	const struct entry_t* table;
	uint16_t entries;

	// Interpolate a value.
	int16_t MapInterpolate(bool x_to_y_, int16_t v_) const;

	// Find the nearest value.
	int16_t MapNearest(bool x_to_y_, int16_t v_) const;
};
