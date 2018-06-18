package com.elmocity.ss.ib;

import java.time.LocalTime;

// NOTE IB doesn't have an internal container class for a Quote.  It just has loose "ticks" for each field.
// So here we just clone the SSQuote, since that has the fields we care about.  This could/should have lots
// more fields, corresponding to all the tick values IB provides, like bid_size, etc
public class IBQuote
{
	public double bid = 0.00;
	public double ask = 0.00;
	public double last = 0.00;
	public double imp_vol = 0.00;
	public boolean halted = false;
	
	public LocalTime last_update;

	public IBQuote()
	{
		last_update = LocalTime.now();
	}
	
	public String debugString()
	{
		String s = String.format("BALIH: %6.02f %6.02f %6.02f %6.04f %b", bid, ask, last, imp_vol, halted);
		return s;
	}
}

