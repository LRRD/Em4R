package com.elmocity.ss.ib;

import java.time.LocalTime;

import com.elmocity.elib.util.Format;
import com.ib.client.OrderStatus;

public class IBOrderOut
{
	// Live order fields, updated from the attached orderManager
	public OrderStatus status = OrderStatus.Unknown;
	public int filled = 0;
	public int remaining = 0;
	public double averageFillPrice = 0.00;
	public double lastFillPrice = 0.00;
	public LocalTime lastUpdateTime;
	
	public String debugString()
	{
		String s = String.format("[%12s : filled = %3d remaining = %3d]", status.toString(), filled, remaining);
		if (filled > 0) {
			s += " (avgFillPrice " + Format.price(averageFillPrice) + ", lastFillPrice " + Format.price(lastFillPrice) + ")";
		}
		return s;
	}

}
