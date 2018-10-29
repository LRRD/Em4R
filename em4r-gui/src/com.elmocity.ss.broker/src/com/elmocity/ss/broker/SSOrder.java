package com.elmocity.ss.broker;

import com.ib.client.Contract;
import com.ib.client.Order;

public class SSOrder
{
	// Application fields
	public SSOrderIn orderIn;

	// Live order fields, updated from the attached orderManager
	public SSOrderOut orderOut;
	
	// DEBUG / HACK FOR IB Live Order
	// debugging, IB specific YUCK TODO
	public int ibOrderID = 0;
	public Contract ibContract;
	public Order ibOrder;
	
	
	public SSOrder(SSOrderIn orderIn)
	{
		this.orderIn = orderIn;				// This is not a clone, since it contains the KEY value needed to lookup later
		this.orderOut = new SSOrderOut();
	}
	
	public String debugString()
	{
		String s = "" + orderIn.debugString() + " " + orderOut.debugString();
		return s;
	}
	
	public enum SSSide
	{
		UNKNOWN,
		BUY,
		SELL
	}
}
