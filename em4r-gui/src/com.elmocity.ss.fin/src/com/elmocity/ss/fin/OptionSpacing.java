package com.elmocity.ss.fin;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.util.Calc;
import com.elmocity.ss.fin.ComboDesc.ComboType;


public class OptionSpacing
{
	private static final Logger logger = LoggerFactory.getLogger(OptionSpacing.class);


	static ArrayList<OptionInfo> optionInfo = new ArrayList<OptionInfo>();
	static
	{
		// underlyingSymbol, nativeOptionSpacing, bestSpread, worstSpread, optimalComboOffset, overnightDrop, strangleDiff, wideDiff

		optionInfo.add(new OptionInfo("TSLA" , 2.50, 0.75, 1.50, 0.00, 0.50, 	1.10, 2.20));
		optionInfo.add(new OptionInfo("NFLX" , 2.50, 0.35, 0.70, 0.00, 0.25, 	1.10, 2.20));
		optionInfo.add(new OptionInfo("AMZN" , 2.50, 0.75, 1.50, 0.00, 0.75, 	1.10, 2.20));
		optionInfo.add(new OptionInfo("GOOGL", 2.50, 1.00, 2.00, 0.00, 0.75, 	1.10, 2.20));
		optionInfo.add(new OptionInfo("AAPL" , 2.50, 0.10, 0.25, 0.00, 0.10, 	1.10, 2.20));
		optionInfo.add(new OptionInfo("QQQ"  , 0.50, 0.15, 0.30, 0.00, 0.10, 	0.25, 0.50));
		optionInfo.add(new OptionInfo("AGN"  , 2.50, 0.50, 1.25, 0.00, 0.50, 	1.10, 2.20));
	}
	
	// -----------------------------------------------------------------------------------
	
	public static OptionInfo getOptionInfo(String underlyingSymbol)
	{
		for (int i = 0; i < optionInfo.size(); i++) {
			if (optionInfo.get(i).underlyingSymbol.equals(underlyingSymbol)) {
				return optionInfo.get(i);
			}
		}
		logger.error("missing OptionInfo for symbol {}", underlyingSymbol);
		return null;
	}
	
	public static double getNativeOptionSpacing(String underlyingSymbol)
	{
		OptionInfo optionInfo = getOptionInfo(underlyingSymbol);
		if (optionInfo == null) {
			logger.error("faking 2.50 nos for symbol {}", underlyingSymbol);
			return 2.50;	// DEBUG TODO HACK - makes boeing or others work, since most stocks we are likely to look at are $250+ stocks?
		}
		return optionInfo.nativeOptionSpacing;
	}
	
	public static ArrayList<String> getSymbolList()
	{
		ArrayList<String> list = new ArrayList<String>();
		
		for (int i = 0; i < optionInfo.size(); i++) {
			list.add(optionInfo.get(i).underlyingSymbol);
		}
		return list;
	}
	
	// -------------------------------------------------------------------------------------
	
	// Given the current stock price, find the nearest combo (defaults to nos straddles only)
	// This is used by Robots to combo a center area to place orders, not by database backtesting code.
	// See DBLookup.getNearestStrike or getBestCombo?  very similar
	public static double getNearestStrike(String underlyingSymbol, double price)
	{
		return getNearestStrike(underlyingSymbol, price, false);
	}
	
	public static double getNearestStrike(String underlyingSymbol, double price, boolean allowStrangles)
	{
		double nos = getNativeOptionSpacing(underlyingSymbol);
		if (Calc.double_equals(nos,  0.00)) {
			logger.error("nos failure for symbol {}", underlyingSymbol);
			return 0.00;	// TODO assert(nos != 0.00) could cause divide by zero exceptions upstream
		}

		// Virtual Option Spacing
		double vos = nos;
		if (allowStrangles) {
			vos = nos / 2.0;
		}
		
		double x = price + (vos / 2.0);
		x = ((int)(x / vos)) * vos;
		return Calc.round_to_penny(x);
	}

	/*
	 * Given a stock symbol and a desired combo strike, test to see what type of combo it would be
	 * <p>
	 * Basically tests to see if the strike is a native Straddle, else assume it is a Strangle.  Never returns Wide.
	 */
	public static ComboType getComboType(String underlyingSymbol, double comboStrike)
	{
		double nos = OptionSpacing.getNativeOptionSpacing(underlyingSymbol);
		if (Calc.double_equals(nos,  0.00)) {
			logger.error("nos failure for symbol {}", underlyingSymbol);
			return null;
		}
		
		double rounded = ((int)(comboStrike / nos)) * nos;
		if (Calc.double_equals(rounded, comboStrike)) {
			return ComboType.STRADDLE;
		}
		else {
			return ComboType.STRANGLE;
		}
	}
	
	
//	// Historical data, find the best fit STRADDLE, or STRANGLE if allowed.
//	public static ComboDesc getBestCombo(Security underlyingSecurity, LocalDate date, int bar, boolean allowStrangles)
//	{
//		String underlyingSymbol = underlyingSecurity.underlying;
//		
//		// Read the stock price at that moment
//		double stockPrice = IBQuote.getStockQuote(underlyingSecurity, date, bar);
//		
//		// Determine the combo, then read the two legs, assembling into a combo.
//		ComboDesc comboDesc = new ComboDesc();
//	
//		// Straddle legs have the same strikes as the overall virtual strike, so easy first case
//		double comboStrike = OptionSpacing.getNearestStrike(underlyingSymbol, stockPrice, allowStrangles);
//		// Set the type
//		comboDesc.comboType = getComboType(underlyingSymbol, comboStrike);
//		
//		// TODO fix to alow thursday expiration when fri holidays
//		LocalDate expiry = date;
//		if (date.getDayOfWeek() != DayOfWeek.FRIDAY) {
//			expiry = date.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
//		}
//		
//		if (comboDesc.comboType == ComboType.STRADDLE) {
//			Security leg1 = new Security(SecurityType.OPTION, underlyingSymbol, expiry, comboStrike, SecurityRight.PUT);
//			comboDesc.legs.add(leg1);
//		
//			Security leg2 = new Security(SecurityType.OPTION, underlyingSymbol, expiry, comboStrike, SecurityRight.CALL);
//			comboDesc.legs.add(leg2);
//		}
//		else if (comboDesc.comboType == ComboType.STRANGLE) {
//			double vos = OptionSpacing.getNativeOptionSpacing(underlyingSymbol) / 2.0;
//			Security leg1 = new Security(SecurityType.OPTION, underlyingSymbol, expiry, comboStrike - vos, SecurityRight.PUT);
//			comboDesc.legs.add(leg1);
//		
//			Security leg2 = new Security(SecurityType.OPTION, underlyingSymbol, expiry, comboStrike + vos, SecurityRight.CALL);
//			comboDesc.legs.add(leg2);
//		}
//		else {
//			// fail
//			SS.log("getBestCombo failed: unimplemented comboType = " + comboDesc.comboType.toString());
//			return null;
//		}
//			
//		return comboDesc;
//	}
	

}


