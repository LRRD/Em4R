package com.elmocity.elib.swt;

import com.elmocity.elib.util.Calc;
import java.time.DayOfWeek;
import java.util.Set;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColorCache
{
	// One color registry for the whole application
	static final ColorRegistry colorRegistry = new ColorRegistry();
	
	// Stock colors
	public static final String BLACK = "black";
	public static final String WHITE = "white";
	public static final String ERROR = "error";

	// Graph grid and related
	public static final String GRID_LINES_NORMAL = "grid_lines_normal";
	public static final String GRID_LINES_ZERO = "grid_lines_zero";

	// Graph series 
	public static final String MONDAY = "monday";
	public static final String TUESDAY = "tuesday";
	public static final String WEDNESDAY = "wednesday";
	public static final String THURSDAY = "thursday";
	public static final String FRIDAY = "friday";
	public static final String META = "meta";
	public static final String NO_DOTW = "no_dotw";		// generic
	
	private final static Logger logger = LoggerFactory.getLogger(ColorCache.class);
	
	static
	{
		colorRegistry.put(BLACK,				new RGB(  0,   0,   0));
		colorRegistry.put(WHITE,				new RGB(255, 255, 255));
		colorRegistry.put(ERROR,				new RGB(255,  50,  50));
		
		colorRegistry.put(GRID_LINES_NORMAL,	new RGB(200, 200, 200));
		colorRegistry.put(GRID_LINES_ZERO,		new RGB(150, 150, 150));
		
		colorRegistry.put(MONDAY,				new RGB(180,  80,  80));
		colorRegistry.put(TUESDAY,				new RGB( 80, 180,  80));
		colorRegistry.put(WEDNESDAY,			new RGB( 80,  80, 180));
		colorRegistry.put(THURSDAY,				new RGB(180, 180,  60));
		colorRegistry.put(FRIDAY,				new RGB( 60, 180, 180));
		colorRegistry.put(META,					new RGB(  0,   0,   0));
		colorRegistry.put(NO_DOTW,				new RGB(180,  60, 180));
	}
	
	
	public static Color getColor(String key)
	{
		Color color = colorRegistry.get(key);
		if (color == null) {
			logger.error("color not found in registry, key = {}", key);
		}
		return color;
	}
	
	public static Color getColorByDOTW(DayOfWeek dotw)
	{
		return getColorByDOTW(dotw, 100);	// 100 percent, not dimmed
	}

	public static Color getColorByDOTW(DayOfWeek dotw, int gradient)
	{
		if (dotw == null) {
			return colorRegistry.get(NO_DOTW);
		}
		
		Color c;
		switch (dotw) {
		case MONDAY:	c = colorRegistry.get(MONDAY);		break;
		case TUESDAY:	c = colorRegistry.get(TUESDAY);		break;
		case WEDNESDAY:	c = colorRegistry.get(WEDNESDAY);	break;
		case THURSDAY:	c = colorRegistry.get(THURSDAY);	break;
		case FRIDAY:	c = colorRegistry.get(FRIDAY);		break;
		default:		c = colorRegistry.get(NO_DOTW);		break;
		}
		
		if ((gradient >= 1) && (gradient <= 99)) {
			c = getDimmedColor(c, gradient / 100.0);
		}
		return c;
	}

	public static Color getRandomColor()
	{
		Set<String> keySet = colorRegistry.getKeySet();
		if (keySet == null || keySet.size() == 0) {
			logger.error("color registry empty when caller asked for random color");
			return colorRegistry.get(NO_DOTW);
		}
		
		int chosen = Calc.rand(0, keySet.size() - 1);
		String[] colorKeys = keySet.toArray(new String[0]);
		Color color = colorRegistry.get(colorKeys[chosen]);
		if (color == null) {
			logger.error("color registry toArray() returned null colors. length = {}, chosen = {}", keySet.size(), chosen);
			return colorRegistry.get(NO_DOTW);
		}
		// If we ended up rolling BLACK as the color, pick something else, since the black text becomes unreadable
		if (color.getRed() == 0 && color.getGreen() == 0 && color.getBlue() == 0) {
			color = colorRegistry.get(NO_DOTW);
		}
		
		return color;
	}

	public static Set<String> getColorKeys()
	{
		return colorRegistry.getKeySet();
	}

	//------------------

	static int leaked = 0;
	/**
	 * Given an existing Color, create a new color that is "dimmed" by a percent of all 3 RGB values.
	 * <p>
	 * "dim" is a percent of the key color, from 0.01 to 0.99
	 * TODO LEAKS THE COLOR EVERY TIME
	 */
	public static Color getDimmedColor(Color color, double dim)
	{
		int r = (int) (color.getRed() * dim);
		int g = (int) (color.getGreen() * dim);
		int b = (int) (color.getBlue() * dim);
		
		// TODO Leaking this Color object, since noone will clean it up ever.  Each replot of the graph leaks many.  :-(
		Color dimmed = new Color(Display.getDefault(), r, g, b);
		leaked++;
		if (leaked % 100 == 0) {
			logger.debug("leaking dimmed colors, total so far = {}", leaked);
		}
		return dimmed;
	}


}
