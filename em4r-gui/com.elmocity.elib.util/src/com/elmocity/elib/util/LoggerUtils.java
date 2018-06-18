package com.elmocity.elib.util;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;


// This class assumes we are using the SLF4J interface to use a standard logging package, specifically "logback"
//
// MARKERS are used for specific actions that should be taken on a LOG message, which is not mapped to the LEVEL.
// For example, one common marker could be "EMAIL_TO_ADMIN", attached to specific log messages, even if not ERROR or WARN level etc.
// For SS, we don't really use markers.  TODO possibly use to mark BUY and SELL executions to easily collect/mail a daily trade summary.
//
// MDC are used to mark a log line with an entity variable (like a user or session) to make tracking the progress of one entity easier.
// For SS, we will use two MDC keys: (A) Robot ID and (B) Order ID.
// This allows to then slice/dice the log file to filter on just one specific robot (usually run 3-8 robots) or just one order.
//

// ERROR			bad stuff, deployment is bad, asserts, bugs
// WARN				possibly bad stuff, but could be ok to not find a file or resource, especially if from user input
// INFO				stuff useful for a support engineer, like filenames, counts of return data, application state changes
// DEBUG			stuff only useful to a developer, generally needs access to the source code to see what that message means
// TRACE			data in loops, possibly too much to run in production

public class LoggerUtils
{
	// Two well-known MDC used in the running robots.
	private static final String MDC_ROBOT_KEY = "robot";
	private static final String MDC_ORDER_KEY = "order";
	
// MARKERS ARE NOT USED CURRENTLY
//	// TODO this should have a fixed set of markers (enum) without hopefully short (1 character?) names
//	//		for instance, EMAIL, ABORT, ... actions that should be done by a log watcher
//	public static org.slf4j.Marker getMarker(String markerName)
//	{
//		return MarkerFactory.getMarker(markerName);
//	}

	public static void setRobot(String value)
	{
		MDC.put(MDC_ROBOT_KEY, value);
	}
	public static void setOrder(String value)
	{
		MDC.put(MDC_ORDER_KEY, value);
	}
	// NOTE: i think the caller can each MDC value to null or to "" is the same as clear().
	public static void clear()
	{
		MDC.clear();
	}

	// ----
	
//	// Let the user change the level on an existing logger.
//	public static void setLevel(Logger logger, Level level)
//	{
//		if (isLogbackClassic(logger)) {
//			ch.qos.logback.classic.Logger classicLogger = (ch.qos.logback.classic.Logger) logger;
//			
//			// Have to convert from the SLF4J Level enum to the logback Level enum.
//			ch.qos.logback.classic.Level classicLevel = ch.qos.logback.classic.Level.OFF;
//			switch (level) {
//				case TRACE:		classicLevel = ch.qos.logback.classic.Level.TRACE;		break;
//				case DEBUG:		classicLevel = ch.qos.logback.classic.Level.DEBUG;		break;
//				case INFO:		classicLevel = ch.qos.logback.classic.Level.INFO;		break;
//				case WARN:		classicLevel = ch.qos.logback.classic.Level.WARN;		break;
//				case ERROR:		classicLevel = ch.qos.logback.classic.Level.ERROR;		break;
//			}
//			
//			classicLogger.setLevel(classicLevel);
//		}
//	}
//	
//	private static boolean isLogbackClassic(Logger logger)
//	{
//		if (logger instanceof ch.qos.logback.classic.Logger) {
//			return true;
//		}
//		return false;
//	}
//
//	/**
//	 * Set an environment var that the logback.xml file will read when it gets loaded, which points to the HIVE.
//	 */
//	public static void setLogbackEnvironment()
//	{
//		// The command line path is provided like "C:\SS HIVE"
//		String s = SSDeploy.getHivePath();
//		// But the logback.xml parser expects the other slash, and needs to be escaped too
//		s = s.replace("\\", "//");
//		// Set the env var
//		System.setProperty("SSHIVEPATH", s);	// no trailing slash
//	}

}
