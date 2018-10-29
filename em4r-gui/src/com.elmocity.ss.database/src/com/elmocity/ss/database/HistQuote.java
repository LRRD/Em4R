package com.elmocity.ss.database;

import java.time.LocalDate;

/**
 * Data container that represents a single historical quote.
 * <p>
 * Original implementation is for downlaoded data from IB at the 1-min granularity.<br>
 * Next will be to use IQFeed full tick data, to either create our own or use their historical quotes. 
 */
public class HistQuote
{
	// NOTE: all fields should be in lower case to match the column names in the matching database table.
	// Some SQL implementations maintain camelcase, or force all UPPER etc... what a mess.
	
	// NOTE: additionally, the Java reflection field lookup call is not case-sensitive (like it should be)
	
	public int id;					// database unique ID
	public String security_key;		// 32 char max

	public LocalDate quote_date;	// on the SQL side, time portion set to zero (midnight)
	public int quote_bar;			// 0 == 09:30:00, 389 == closing 16:00:00 (4pm).  always in eastern time zone EST or EDT.
	
	public double quote_open;		// stored in database as fixed-point to 2 decimal points (pennies)
	public double quote_high;
	public double quote_low;
	public double quote_close;
	public int quote_volume;

	HistQuote()
	{
		
	}
	
	public HistQuote(int id, String security_key,
			LocalDate quote_date, int quote_bar,
						double quote_open, double quote_high, double quote_low, double quote_close, int quote_volume)
	{
		this.id = id;
		this.security_key = security_key;
		
		this.quote_date = quote_date;
		this.quote_bar = quote_bar;
		
		this.quote_open = quote_open;
		this.quote_high = quote_high;
		this.quote_low = quote_low;
		this.quote_close = quote_close;
		this.quote_volume = quote_volume;
	}

	public String debugString()
	{
		return String.format("%d %s %s %3d %.02f %.02f %.02f %.02f %d",
				id, security_key, quote_date.toString(), quote_bar,
				quote_open, quote_high, quote_low, quote_close, quote_volume);
	}
}


