package com.elmocity.ss.broker;

import java.time.LocalTime;

public class SSQuote
{
	public double bid = 0.00;
	public double ask = 0.00;
	public double last = 0.00;
	public double imp_vol = 0.00;
	public boolean halted = false;
	public LocalTime last_update;

	public SSQuote()
	{
		last_update = LocalTime.now();
	}

	public SSQuote(double bid, double ask, double last, double imp_vol, boolean halted, LocalTime last_update)
	{
		this.bid = bid;
		this.ask = ask;
		this.last = last;
		this.imp_vol = imp_vol;
		this.halted = halted;
		this.last_update = last_update;
	}

	public String debugString()
	{
		String s = String.format("BALIH: %6.02f %6.02f %6.02f %6.04f %b", bid, ask, last, imp_vol, halted);
		return s;
	}
}

