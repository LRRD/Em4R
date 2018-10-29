package com.elmocity.ss.fin;

import java.util.ArrayList;

import com.elmocity.elib.util.Calc;

public class ComboDesc
{
	public enum ComboType
	{
		STRADDLE,
		STRANGLE,
		WIDE,
		;

		// Get a one character code to show in tables or debugging
		public static String getCode(ComboType c)
		{
			switch (c) {
			case STRADDLE:	return "S";
			case STRANGLE:	return "G";
			case WIDE:		return "W";
			}
			return "?";
		}

		public String getCode()
		{
			return getCode(this);
		}

	}

	// ------------------------------------------
	
	// This assumes the ratio is 1 to 1 for each leg, and they are both BUY or SELL (same side)
	
	public ComboType comboType;
	public ArrayList<Security> legs = new ArrayList<>(2);

	
	public double getVirtualStrike()
	{
		// NOTE: we don't handle 4 legged condors etc
		if (legs.size() != 2) {
			return 0.0;
		}

		switch (comboType) {
		case STRADDLE:
			return legs.get(0).option_strike;
		case STRANGLE:
		case WIDE:
			return Calc.round_to_penny((legs.get(0).option_strike + legs.get(1).option_strike) / 2.0);
		}
		return 0.00;
	}

	public ComboDesc()
	{
	}

	// Handling just a few 2-legged combos for now
	public ComboDesc(ComboType comboType, Security leg1, Security leg2)
	{
		this.comboType = comboType;
		legs.add(leg1);
		legs.add(leg2);
	}

	public String debugString()
	{
		String underlying = "XXXX";
		if (legs.size() > 0) {
			underlying = legs.get(0).underlying;
		}
		String s = String.format("%s %s %.02f", underlying, comboType.getCode(), getVirtualStrike());
		return s;
	}
}
