#include "table.h"
#include "debug.h"

Table::Table(bool interp_, const entry_t* table_) :
	interp(interp_),
	table(table_),
	entries(0)
{
	// Count entries.
	const entry_t* scan = table_;
	while ((scan->x != END_OF_TABLE_XY) || (scan->y != END_OF_TABLE_XY)) {
		entries++;
		scan++;
	}

	// A table must have at least two entries.
	ASSERT(entries > 1);
}

int16_t Table::Interpolate(int16_t vala_, int16_t mina_, int16_t maxa_, int16_t minb_, int16_t maxb_)
{
        // Handle out-of-bounds cases.
        if (vala_ < mina_) {
//		DBG_ERR("Interpolate %d over (%d .. %d) -> %d (min)", vala_, mina_, maxa_, minb_);
                return minb_;
        }
        if (vala_ > maxa_) {
//		DBG_ERR("Interpolate %d over (%d .. %d) -> %d (max)", vala_, mina_, maxa_, maxb_);
                return maxb_;
        }

        // Compute proportion along A scale in range 0..1000.
        int32_t frac = ((int32_t)(vala_ - mina_) * 1000L) / (int32_t)(maxa_ - mina_);

        // Scale into B units.
	int16_t result = minb_ + (int16_t)((frac * (int32_t)(maxb_ - minb_)) / 1000L);
//	DBG_ERR("Interpolate %d over (%d .. %d) -> %d over (%d .. %d)",
//		vala_, mina_, maxa_, result, minb_, maxb_);
	return result;
}

int16_t Table::MapXToY(int16_t v_) const
{
	// Y is in tens.
	return (interp ? MapInterpolate(true, v_) : MapNearest(true, v_)) / 10;
}

int16_t Table::MapYToX(int16_t v_) const
{
	// Y is in tens.
	return interp ? MapInterpolate(false, (v_ * 10)) : MapNearest(false, (v_ * 10));
}

const Table::entry_t& Table::First() const
{
	return table[0];
}

const Table::entry_t& Table::Last() const
{
	return table[entries - 1];
}

int16_t Table::MapInterpolate(bool x_to_y_, int16_t v_) const
{
	// Check for reversed domain.
	int16_t first_v = x_to_y_ ? table[0].x : table[0].y;
	int16_t last_v = x_to_y_ ? table[entries - 1].x : table[entries- 1].y;
	bool forward = (last_v > first_v);
	
	int16_t first_slot = forward ? 0 : entries - 1;
	int16_t last_slot = forward ? entries - 1 : 0;
	int16_t step = forward ? 1 : -1;

	// Check for uninterpolatable cases beyond the table bounds.
	int16_t min_v = x_to_y_ ? table[first_slot].x : table[first_slot].y;
	if (v_ <= min_v) {
		return x_to_y_ ? table[first_slot].y : table[first_slot].x;
	}
	int16_t max_v = x_to_y_ ? table[last_slot].x : table[last_slot].y;
	if (v_ >= max_v) {
		return x_to_y_ ? table[last_slot].y : table[last_slot].x;
	}

	// Find the entries preceding and following the value.
	int16_t preceding_slot_v = 0;
	int16_t following_slot_v = 0;
	int16_t preceding_slot_other_v = 0;
	int16_t following_slot_other_v = 0;
	uint16_t preceding_slot = 0;
	uint16_t following_slot = 0;
	for (uint16_t slot = first_slot; slot != (last_slot + step); slot++) {
		int16_t this_slot_v = x_to_y_ ? table[slot].x : table[slot].y;
		int16_t next_slot_v = x_to_y_ ? table[slot + 1].x : table[slot + 1].y;
		if ((v_ >= this_slot_v) && (v_ <= next_slot_v)) {
			preceding_slot_v = this_slot_v;
			following_slot_v = next_slot_v;
			preceding_slot_other_v = x_to_y_ ? table[slot].y : table[slot].x;
			following_slot_other_v = x_to_y_ ? table[slot + 1].y : table[slot + 1].x;
			break;
		}
	}
	// If this isn't true, the table is corrupt.
	ASSERT(preceding_slot_v != following_slot_v);
	ASSERT(preceding_slot_other_v != following_slot_other_v);

	// Interpolate.
	return Interpolate(v_, preceding_slot_v, following_slot_v, preceding_slot_other_v, following_slot_other_v);
}

int16_t Table::MapNearest(bool x_to_y_, int16_t v_) const
{
	// Find the closest entry in the table.
	// What best_value is initialized do doesn't matter, since we'll always find something
	// with an absolute value better than best_abs_difference, since we're doing signed math.
	uint16_t best_abs_difference = 0xFFFF;
	uint16_t best_value = 0;
	for (uint16_t slot = 0; slot < entries; slot++) {

		// Compute the absolute difference between the reading and the table value.
		int16_t other_v = x_to_y_ ? table[slot].y : table[slot].x;
		uint16_t abs_difference = (v_ > other_v) ?
			v_ - other_v : other_v - v_;

		// If this is greater than the best absolute difference, we're getting worse and we're done.
		if (abs_difference > best_abs_difference) {
			break;
		}

		// This is the new best distance, record it.
		best_abs_difference = abs_difference;
		best_value = other_v;
	}
	return best_value;
}
