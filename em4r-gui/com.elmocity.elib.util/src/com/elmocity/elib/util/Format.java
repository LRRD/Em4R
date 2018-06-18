package com.elmocity.elib.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Format
{
	private static final Logger logger = LoggerFactory.getLogger(Format.class);

	// ----------------------------------------------------------
	
	// Could also be 1000 ^ 3
	public final static long bytesPerGB = 1024 * 1024 * 1024;

	/**
	 * Generate string representing an amount of RAM in GB, given the amount in bytes.
	 * <p>
	 * NOTE: make sure you use a -long- for all your computations, not an -int- before sending in the value.
	 * <br>
	 * Intended to show a human readable approximation of "3.25 GB" instead of "3,251,123,345 bytes"
	 */
	public static String bytesToGBString(long bytes)
	{
		// Check for error values etc
		if (bytes <= 0) {
			bytes = 0;
		}
		
		double inGB = ((double) bytes) / bytesPerGB;
		return String.format("%.02f GB", inGB);
	}
	
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Generate string representing the double, assuming the value is a price (penny) value.
	 * <p>
	 * Pads with zeros to 2 decimal points.  Useful for daily IV as well, if you multiple by 100 before calling this. 
	 */
	public static String price(double price)
	{
		return String.format("%.02f", price);
	}

	// ----------------------------------------------------------

	// All use of date and time should be in the Java 8 classes of LocalDate, LocalTime and LocalDateTime.
	
	
	// DateTimeFormatter is threadsafe, no need for synchronization
	private static final DateTimeFormatter utilTimeFormatter =
			DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
							.withLocale(Locale.getDefault())
							.withZone(ZoneId.systemDefault());
	
	/**
	 * Generate string representing the give LocalTime object.
	 * <p>
	 * Useful for debugging, logging or messages that humans need to read, reusing a consistent format.
	 * <br>
	 * Uses the local machine timezone and locale.
	 */
	public static String time(LocalTime time)
	{
		return time.format(utilTimeFormatter);
	}

	// TODO LocalDate version and maybe a LocalDateTime version combined.
	
	// DateTimeFormatter is threadsafe, no need for synchronization
	private static DateTimeFormatter utilDateFormatter =
			DateTimeFormatter.ofPattern("MM/dd/yyyy");
			// TODO locale?  dont need timezone I guess
	
	/**
	 * Generate string representing the give LocalDate object.
	 * <p>
	 * Useful for debugging, logging or messages that humans need to read, reusing a consistent format.
	 */
	public static String date(LocalDate date)
	{
		return date.format(utilDateFormatter);
	}

	
	/**
	 * Convert a user-provided string that is meant to be a calendar date into a LocalDate value.
	 * <p>
	 * Generally should be using a real Calendar GUI picker, not a text field input for dates.
	 * @return null if parsing error
	 */
	public static LocalDate parseInputDate(String input)
	{
		LocalDate result = null;

		try {
			result = LocalDate.parse(input);
		}
		catch (DateTimeParseException e) {
			logger.debug("cannot parse date input '{}', reason = {}", input, e.getMessage());	// not really an error if user input, they will retry
		}

		return result;
	}

	// ----------------------------------------------------------

	public static String unitTest()
	{
		String time = Format.time(LocalTime.now());
		logger.debug("standard time format {}", time);
		String date = Format.date(LocalDate.now());
		logger.debug("standard date format {}", date);
		
		return "";
	}
}
