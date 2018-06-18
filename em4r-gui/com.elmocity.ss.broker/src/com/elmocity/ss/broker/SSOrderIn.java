package com.elmocity.ss.broker;


import java.time.format.DateTimeFormatter;

import com.elmocity.elib.util.Calc;
import com.elmocity.ss.broker.SSOrder.SSSide;
import com.elmocity.ss.fin.ComboDesc;
import com.elmocity.ss.fin.Security;



public class SSOrderIn
{
	private static Integer NEXT_GLOBAL_KEY = 100;
	public final String key;
	
	public String account;
	public SSSide side;
	public ComboDesc combo;
	public String exchange;
	public String reference;
	public int totalQuantity;
	public double price = 0.00;
	
	public int scaleInitialComponentSize = 0;
	public int scaleSubsequentComponentSize = 0;
	public double scalePriceIncrement = 0.00;
	public double scaleAutoAdjustAmount = 0.00;
	public int scaleAutoAdjustSeconds = 0;
	
	
	public SSOrderIn()
	{
		int key_id = 0;
		synchronized (NEXT_GLOBAL_KEY)
		{
			key_id = NEXT_GLOBAL_KEY++;
		}
		key = "" + key_id;
	}
	
	public String debugString()
	{
		String s = String.format("%22s   %s [(%3s) %s %d at %.02f] %s", combo.debugString(), account, key, side.toString(), totalQuantity, price, reference);
		if (!Calc.double_equals(0.00, scaleInitialComponentSize)) {
			s += String.format(" scale: %d %d %.02f %.02f %d", scaleInitialComponentSize, scaleSubsequentComponentSize, scalePriceIncrement, scaleAutoAdjustAmount, scaleAutoAdjustSeconds);
		}
		return s;
	}

	
}

