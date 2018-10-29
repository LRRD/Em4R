package com.elmocity.elib.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class DateAndTime
{
	// These two methods are for setting GUI calendars to "this week"
	public static LocalDate calcMondayThisWeek(LocalDate d)
	{
	    if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
    		return d;
	    }
	    return d.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
	}
	
	public static LocalDate calcFridayThisWeek(LocalDate d)
	{
	    switch (d.getDayOfWeek()) {
	    case MONDAY:
	    case TUESDAY:
	    case WEDNESDAY:
	    case THURSDAY:
	    	return d.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
	    case FRIDAY:
	    	return d;
//		case SATURDAY:
//		case SUNDAY:
		default:
			// On the weekend, we are still talking about "this week" as last week. so back up
	    	return d.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));
	    }
	}
}
