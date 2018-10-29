package com.elmocity.elib.util;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An all static class with math helper functions, mostly for double comparisons and randoms.
 * <p>
 * Naming this "Math" class is nasty because of the default static import of java.util.Math, so chose "Calc".
 *
 */
public class Calc
{
	private static final Logger logger = LoggerFactory.getLogger(Calc.class);

	// ---------------------------------------------------------------------------------------------------------------

	// As of Java 7, calls to nextInt() on a Random object are threadsafe.  So no need for synchronization here.
	private static final Random seeded = new Random();

	/**
	 * Generate a simple random integer, INCLUSIVE of the min and max.
	 * <p>
	 * Example, a 6-sided dice = rand(1, 6) will return 1, 2, 3, 4, 5, or 6.
	 */
	public static int rand(int min, int max)
	{
		int random = seeded.nextInt((max - min) + 1) + min;
//		logger.trace("rolled random from {} to {} result = {}", min, max, random);
		return random;
	}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Round a double to the nearest penny.
	 * <p>
	 * Only valid for positive numbers, will also break on special values like MAX_VALUE or INFINITY.
	 */
	public static double round_to_penny(double value)
	{
		// NOTE Only legit for positive values.
		double clean = (((int)(value * 100.0 + 0.5)) / 100.0);
		return clean;
	}

	// ---------------------------------------------------------------------------------------------------------------

	private final static double EPSILON = 0.00001;
	
	/**
	 * Test if two double values are the same.
	 * <p>
	 * Should work for special values like MAX_VALUE or INFINITY.
	 * <p>
	 * {@link #EPSILON}
	 */
	public static boolean double_equals(double a, double b)
	{
		// Test first for == to handle special values like MAX or INFINITY
		return a == b ? true : Math.abs(a - b) < EPSILON;
	}

	// -----------------------------------------------------------------------------
	
	public static String unitTest()
	{
		int roll = Calc.rand(1, 20);
		logger.debug("rand 20 = {}", roll);
		
		double a = 1.0001;
		double b = 1.0002;
		boolean eq = Calc.double_equals(a, b);
		logger.debug("does {} equal {}? {}", a, b, eq);

		a = 1.0000001;
		b = 1.0000002;
		eq = Calc.double_equals(a, b);
		logger.debug("does {} equal {}? {}", a, b, eq);
		
		double price = 1.456;
		double rounded = Calc.round_to_penny(price);
		logger.debug("rounded {} to {}", price, rounded);
		
		return "";
	}


}
