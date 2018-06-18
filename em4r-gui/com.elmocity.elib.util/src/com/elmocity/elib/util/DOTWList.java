package com.elmocity.elib.util;

import java.time.DayOfWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DOTWList
{
	// Contains boolean flags for all 7 days of the week, most likely only will use the 5 weekdays.
	// and the ISO enum is from 1 to 7. not 0 to 6.
	
	// So we need 8 slots, with 0 being unused, 1 == DayOfWeek.MONDAY, thru 7 == DayOfWeek.SUNDAY (matching the enum)
	final static int SLOTS = 8;
	private boolean[] flags = new boolean[SLOTS];
	private double[] prices = new double[SLOTS];
	
	static
	{
		int maxEnum = 0;
		for (DayOfWeek c : DayOfWeek.values()) {
			if (c.getValue() > maxEnum) {
				maxEnum = c.getValue();
			}
		}
		if (maxEnum != (SLOTS - 1)) {
			System.out.println("DOTWList: DayOfWeek enum mismatch!");		// NOTE probably can't call the logging framework at this point
		}
	}

	// -----------------
	// Boolean flags (used for SHOW this date in stuff like max move)
	
	public void setAllFlags(boolean flag)
	{
		for (int i = 0; i < SLOTS; i++) {
			flags[i] = flag;
		}
	}

	public void setFlag(DayOfWeek dotw, boolean flag)
	{
		flags[dotw.getValue()] = flag;
	}

	public boolean getFlag(DayOfWeek dotw)
	{
		return flags[dotw.getValue()];
	}

	// -----------------
	// Price values (used for cap of entry price per day, for instance)
	
	public void setAllPrices(double price)
	{
		for (int i = 0; i < SLOTS; i++) {
			prices[i] = price;
		}
	}

	public void setPrice(DayOfWeek dotw, double price)
	{
		prices[dotw.getValue()] = price;
	}

	public double getPrice(DayOfWeek dotw)
	{
		return prices[dotw.getValue()];
	}
}
