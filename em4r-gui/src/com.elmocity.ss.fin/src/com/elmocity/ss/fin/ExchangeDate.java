package com.elmocity.ss.fin;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class ExchangeDate
{
	// HOLIDAYS: Markets closed, no data for these days at all
	static final LocalDate[] fullHolidays = new LocalDate[] {
		LocalDate.of(2016,  1, 01),		// New Years Day
		LocalDate.of(2016,  1, 18),		// MLK
		LocalDate.of(2016,  2, 15),		// Presidents Day
		LocalDate.of(2016,  3, 25),		// Good Friday
		LocalDate.of(2016,  5, 30),		// Memorial Day
		LocalDate.of(2016,  7, 04),		// Independence
		LocalDate.of(2016,  9, 05),		// Labor Day
		LocalDate.of(2016, 11, 24),		// Thanksgiving
		LocalDate.of(2016, 12, 26),		// Christmas
		
		LocalDate.of(2017,  1, 02),		// New Years Day
		LocalDate.of(2017,  1, 16),		// MLK
		LocalDate.of(2017,  2, 20),		// Presidents Day
		LocalDate.of(2017,  4, 14),		// Good Friday
		LocalDate.of(2017,  5, 29),		// Memorial Day
		LocalDate.of(2017,  7, 04),		// Independence
		LocalDate.of(2017,  9, 04),		// Labor Day
		LocalDate.of(2017, 11, 23),		// Thanksgiving
		LocalDate.of(2017, 12, 25),		// Christmas
	};
	
	// Markets close 3 hours early (1pm) - screws up the Projected openings for the next day
	static final LocalDate[] halfHolidays = new LocalDate[] {
		LocalDate.of(2016, 11, 25),		// Thanksgiving	- day after, terrible. had to leave the day in, since the options expired that day.
		
		LocalDate.of(2017,  7, 03),		// Independence - day before
		LocalDate.of(2017, 11, 24),		// Thanksgiving - day after
	};
	
	

	
	// Take in a date string, add one day (or more) until we find the next trading day.
	public static LocalDate computeNextTradingDay(LocalDate date)
	{
		return computeNextTradingDay(date, 1);
	}

	// Take in a date string, add/subtract one day (or more) until we find the next trading day.
	public static LocalDate computeNextTradingDay(LocalDate date, int direction)
	{
		// ASSERT direction is +1 or -1

		// Add/subtract 1 day
		LocalDate testDate = date.plusDays(direction);

		// Test and increment/decrement the testobject (one day at a time) until we get a real trading day
		while (true) {
			if (isTradingDay(testDate)) {
				return testDate;
			}
			testDate = testDate.plusDays(direction);
		}
	}
	
	// Take in a date and see if it is a legit trading day.
	public static boolean isTradingDay(LocalDate date)
	{
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
			return false;
		}
		else if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return false;
		}
		else if (isHoliday(date)) {
			return false;
		}

		// This date looks good... not a holiday or weekend, so should be a real trading day
		return true;
	}

	public static boolean isHoliday(LocalDate date)
	{
		for (int i = 0; i < fullHolidays.length; i++) {
			if (date.compareTo(fullHolidays[i]) == 0) {
				return true;
			}
		}
		// TODO 1/2 days? yikes
		return false;
	}
	
	// TODO hack, doesnt handle all situations
	public static LocalDate getWeeklyExpiry(LocalDate date)
	{
		// If today is friday, and its a legit trading date, just use it.
		if (date.getDayOfWeek() == DayOfWeek.FRIDAY && isTradingDay(date)) {
			return date;
		}
		// Otherwise, look to "next" Friday, and back up to Thursday if its a holiday.
		LocalDate expiry = date.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		if (!isTradingDay(expiry)) {
			expiry = computeNextTradingDay(expiry, -1);
		}
		return expiry;
	}

//	// These two methods are for setting GUI calendars to "this week"... TODO should this handle holidys or not?
//	public static LocalDate calcMondayThisWeek(LocalDate d)
//	{
//	    if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
//    		return d;
//	    }
//	    return d.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
//	}
//	public static LocalDate calcFridayThisWeek(LocalDate d)
//	{
//	    switch (d.getDayOfWeek()) {
//	    case MONDAY:
//	    case TUESDAY:
//	    case WEDNESDAY:
//	    case THURSDAY:
//	    	return d.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
//	    case FRIDAY:
//	    	return d;
//	    }
////	    case SATURDAY:
////	    case SUNDAY:
//	    	// On the weekend, we are still talking about "this week" as last week. so back up
//	    	return d.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));
//	}
	
//	public static boolean isDateValid(String date, int format)
//	{
//		// Did we even get a valid string and format?
//		if ((date == null) || (date.length() <= 0)) {
//			return false;
//		}
//		if (format == df1) { }
//		else if (format == df2) { }
//		else {
//			return false;
//		}
//
//		// Do some real sanity checks
//		if (date.length() != dateformat1.length()) {
//			System.out.println("bad date string length");
//			return false;
//		}
//		
//		return true;
//	}
	
	
	// TODO do these work OK with friday holidays?
	public static int computeDaysUntilExpiration(LocalDate testDate, LocalDate expiry)
	{
		// really simple implementation, that assumes the trading_date and expiry_date are the same week, and that
		// the expiry_date has already been adjusted for Friday holidays.
		int days = (int)ChronoUnit.DAYS.between(testDate, expiry) + 1;		// if today is thursday, expiry is friday, then result = 2 days... all of thurs and all of fri.
		
//		int trading_dotw = computeDOTW(trading_date, trading_format);
//		int expiry_dotw = computeDOTW(expiry_date, expiry_format);
//		int days_left = expiry_dotw - trading_dotw + 1;
//		
////		SS.log("  days left = " + days_left + " " + trading_date + " -> " + expiry_date);
		return days;
	}

	public static double computeTimeUntilExpiration(int whole_days, int bar)
	{
		// Bar 0 is the first bar of the day, so that computes to 0.0 loss.
		// Bar 389 is the final bar of the day, that should compute very close to 1.0 (lost the entire day)
		double fraction = 1 - ((390.00 - bar) / 390.00);
//		double time_until_expiration = whole_days - fraction;
		double time_until_expiration = whole_days - (fraction / 3.0);	// DEBUG, cut the decay rate in 1/2, assuming 1/2 during the day and 1/2 overnight.
		return time_until_expiration / 252.0;	// annualize
	}

	public static double computeBlackScholesTimeLeft(LocalDate testDate, LocalDate expiry, int entryBar)
	{
		int days = computeDaysUntilExpiration(testDate, expiry);
		double timeLeft = computeTimeUntilExpiration(days, entryBar);
		return timeLeft;
	}
}
