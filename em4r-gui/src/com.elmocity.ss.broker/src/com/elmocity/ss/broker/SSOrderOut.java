package com.elmocity.ss.broker;



import java.time.LocalTime;

import com.elmocity.elib.util.Format;


public class SSOrderOut
{
	// Status of the order, filled in from the callbacks in the data provider
	public SSOrderStatus status = SSOrderStatus.UNKNOWN;
//	public double price = 0.00;
	public int filled = 0;
	public int remaining = 0;
	public double averageFillPrice = 0.00;
	public double lastFillPrice = 0.00;
	public LocalTime lastUpdateTime;


	public SSOrderOut()
	{
	}

	public SSOrderOut(SSOrderStatus status, int filled, int remaining, double averageFillPrice, double lastFillPrice, LocalTime lastUpdateTime)
	{
		this.status = status;
		this.filled = filled;
		this.remaining = remaining;
		this.averageFillPrice = averageFillPrice;
		this.lastFillPrice = lastFillPrice;
		this.lastUpdateTime = lastUpdateTime;
	}


	public String debugString()
	{
//		String s = String.format("BUY %d at %.02f, filled = %d", totalQuantity, price, filled);
		String s = String.format("[%12s  : filled = %3d remaining = %3d]", status.toString(), filled, remaining);
		if (filled > 0) {
			s += " (avgFillPrice " + Format.price(averageFillPrice) + ", lastFillPrice " + Format.price(lastFillPrice) + ")";
		}
		return s;
	}

}
