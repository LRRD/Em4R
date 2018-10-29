package com.elmocity.ss.fin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Security
{
	public enum SecurityType
	{
		STOCK(10),
		OPTION(20);

		private int value;
		private SecurityType(int value) {
			this.value = value;
		}
		public int getValue() {
			return value;
		}
	};

	public enum SecurityRight
	{
		PUT('P'),
		CALL('C');

		private char value;
		private SecurityRight(char value) {
			this.value = value;
		}
		public char getValue() {
			return value;
		}
	};

	public SecurityType type;
	
	// For all types
	public String underlying;				// the underlying stock symbol (AAPL, GOOGL, MO, etc)
	
	// For OPTION
	public LocalDate option_expiry;			// date (no time) of expiration
	public double option_strike;			// strike price, to the penny
	public SecurityRight option_right;		// Put or Call

	
	static Logger logger = LoggerFactory.getLogger(Security.class);

	// -------------------------------------------------------------------------
	
	public Security()
	{
	}

	// For STOCK type
	public Security(SecurityType type, String underlying)
	{
		this.type = type;
		this.underlying = underlying;
	}
	
	// for OPTION type
	public Security(SecurityType type, String underlying, LocalDate option_expiry, double option_strike, SecurityRight option_right)
	{
		this.type = type;
		this.underlying = underlying;
		this.option_expiry = option_expiry;
		this.option_strike = option_strike;
		this.option_right = option_right;
	}

	/**
	 *  Human readable, show in monospace font for column lineup
	 */
	public String debugString()
	{
		String s = String.format("%6s", underlying);
		if (type == SecurityType.STOCK) {
			s += String.format("%18s", " ");
		}
		else if (type == SecurityType.OPTION) {
			String expiry= option_expiry.format(DateTimeFormatter.BASIC_ISO_DATE);
			s += String.format(" %s %6.02f %c", expiry, option_strike, option_right.getValue());
		}
		return s;
	}

	
	/**
	 * Unique string key, used in the database and various application caches to identify a native security (stock or option)
	 * <p>
	 * Example:  TSLA20170224247.50P
	 */
	public String getDBKey()
	{
		String key = underlying;
		
		if (type == SecurityType.STOCK) {
			// Nothing to add
		}
		else if (type == SecurityType.OPTION) {
			String expiry= option_expiry.format(DateTimeFormatter.BASIC_ISO_DATE);
			key += String.format("%s%.02f%c", expiry, option_strike, option_right.getValue());
		}
		else {
			logger.error("caller asked for DBKey for an unknown security type {}", type);
			return null;	// TODO scary that the caller might use this on a database insert etc
		}
		
		return key;
	}
	
	// Example:  TSLA20170224247.50P
	
	public static Security getSecurityFromDBKey(String key)
	{
		String symbolPattern = "([A-Z]+)";						// all caps				// TODO limit of 6 chars?
		String datePattern = "([0-9]{8})";						// exactly 8 digits, like 20161231
		String floatPattern = "(\\+|-)?([0-9]+(\\.[0-9]+))";	// a float, we never have a leading + or - char
		String rightPattern = "(P|C)";							// single char for Put or Call

		Pattern p = Pattern.compile(symbolPattern + "|" + datePattern + "|" + floatPattern + "|" + rightPattern);
				
		Matcher m = p.matcher(key);
		ArrayList<String> allMatches = new ArrayList<>();
		while (m.find()) {
		    allMatches.add(m.group());
		}
		
		if (allMatches.size() == 0) {
			// failed? caller will blow up
			logger.error("caller tried to create Security from bad DBKey {}", key);
			return null;
		}
		else if (allMatches.size() == 1) {
			// Just one string? must be a stock
			Security security = new Security();
			security.type = SecurityType.STOCK;
			security.underlying = allMatches.get(0);
			return security;
		}
		else if (allMatches.size() == 4) {
			// Option
			Security security = new Security();
			security.type = SecurityType.OPTION;
			
			// Group 0 = stock
			security.underlying = allMatches.get(0);

			// Group 1 = expiry
			LocalDate date = LocalDate.parse(allMatches.get(1), DateTimeFormatter.BASIC_ISO_DATE);
			security.option_expiry = date;

			// Group 2 = strike
			security.option_strike = Double.parseDouble(allMatches.get(2));
			
			// Group 3 = right
			if (allMatches.get(3).startsWith("P")) {
				security.option_right = SecurityRight.PUT;
			}
			else if (allMatches.get(3).startsWith("C")) {
				security.option_right = SecurityRight.CALL;
			}
			else {
				return null;
			}

			return security;
		}

		logger.error("caller tried to create Security from malformed DBKey {}, matches = {}", key, allMatches.size());
		return null;
	}
	
	public boolean same(Security test)
	{
		if (getDBKey().equals(test.getDBKey())) {
			return true;
		}
		return false;
	}
}
