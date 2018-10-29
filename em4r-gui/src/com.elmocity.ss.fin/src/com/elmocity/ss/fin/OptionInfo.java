package com.elmocity.ss.fin;

public class OptionInfo
{
	public String underlyingSymbol;
	public double nativeOptionSpacing;
	public double bestSpread;
	public double worstSpread;
	public double optimalComboOffset;
	public double overnightDrop;
	
	public double strangleDiff;
	public double wideDiff;
	
	public OptionInfo(String underlyingSymbol, double nativeOptionSpacing, double bestSpread, double worstSpread,
			double optimalComboOffset, double overnightDrop, double strangleDiff, double wideDiff)
	{
		this.underlyingSymbol = underlyingSymbol;
		this.nativeOptionSpacing = nativeOptionSpacing;
		this.bestSpread = bestSpread;
		this.worstSpread = worstSpread;
		
		this.optimalComboOffset = optimalComboOffset;
		this.overnightDrop = overnightDrop;
		this.strangleDiff = strangleDiff;
		this.wideDiff = wideDiff;
	}
}

