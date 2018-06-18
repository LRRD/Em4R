package com.elmocity.ss.ib;

/**
 * Data container that represents a single historical quote from IB.
 * <p>
 * Any container class or code needs to keep track of what Contract and what Date this is for. 
 */
public class IBHistQuote
{
	public int quote_bar;			// 0 == 09:30:00, 389 == closing 16:00:00 (4pm).  always in eastern time zone EST or EDT.
	
	public double quote_open;		// stored in database as fixed-point to 2 decimal points (pennies)
	public double quote_high;
	public double quote_low;
	public double quote_close;
	public int quote_volume;

	IBHistQuote()
	{
		
	}
	
	public IBHistQuote(int quote_bar, double quote_open, double quote_high, double quote_low, double quote_close, int quote_volume)
	{
		this.quote_bar = quote_bar;
		
		this.quote_open = quote_open;
		this.quote_high = quote_high;
		this.quote_low = quote_low;
		this.quote_close = quote_close;
		this.quote_volume = quote_volume;
	}

	public String debugString()
	{
		return String.format("%3d %.02f %.02f %.02f %.02f %d",
				quote_bar, quote_open, quote_high, quote_low, quote_close, quote_volume);
	}
}


