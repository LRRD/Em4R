package com.elmocity.elib.swt;

import java.util.Set;

import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FontCache
{
	// One font registry for the whole application
	static final FontRegistry fontRegistry = new FontRegistry();
	
	// Work Unit related
	public static final String WU_TITLE = "wu_title";					// main title string of a WU
	public static final String WU_GROUP = "wu_group";					// group widget titles
	
	public static final String WU_PRESET_BUTTON = "preset_buttons";		// preset buttons that load values into other controls
	public static final String WU_EXECUTE_BUTTON = "execute_buttons";	// main "Run" or "Compute" buttons, should be larger

	public static final String EMRIVER_HUGE_DEVICE = "emriver_huge_device";
	public static final String EMRIVER_HUGE_TARGET = "emriver_huge_target";
	public static final String EMRIVER_HUGE_ACTUAL = "emriver_huge_actual";
	
	// Table related
	public static final String CODE = "code";							// monospaced

	
	private final static Logger logger = LoggerFactory.getLogger(FontCache.class);
	
	static
	{
		// Load the default system font, and adjust everything from there.
		// This should work well if someone has their system font larger than normal, for instance.
		checkSystemFont();
		FontData fd = Display.getDefault().getSystemFont().getFontData()[0];
		
		fontRegistry.put(WU_TITLE,			new FontData[]{new FontData(fd.getName(), fd.getHeight() + 6, SWT.BOLD)});
		fontRegistry.put(WU_GROUP,			new FontData[]{new FontData(fd.getName(), fd.getHeight()    , SWT.BOLD)});

		fontRegistry.put(WU_PRESET_BUTTON,	new FontData[]{new FontData(fd.getName(), fd.getHeight() - 1, SWT.NORMAL)});
		fontRegistry.put(WU_EXECUTE_BUTTON,	new FontData[]{new FontData(fd.getName(), fd.getHeight() + 3, SWT.NORMAL)});

		fontRegistry.put(EMRIVER_HUGE_DEVICE,	new FontData[]{new FontData(fd.getName(), fd.getHeight() + 16, SWT.NORMAL)});
		fontRegistry.put(EMRIVER_HUGE_TARGET,	new FontData[]{new FontData(fd.getName(), fd.getHeight() + 32, SWT.NORMAL)});
		fontRegistry.put(EMRIVER_HUGE_ACTUAL,	new FontData[]{new FontData(fd.getName(), fd.getHeight() + 64, SWT.NORMAL)});

		fontRegistry.put(CODE,				new FontData[]{new FontData("Courier New", 10, SWT.NORMAL)});
	}
	
	// NOTE: if the key is missing from the registry, the internal code just returns the system default font, rather than an error or null.

	
	public static Font getFont(String key)
	{
		return getFont(key, SWT.NONE);			// default to the normal style of the given font
	}
	
	// Three choices, for the same font in the registry.  Means we don't have to pre-make Bold and Italic versions of all fonts. 
	// TODO could handle a request for BOTH a bold and italic?
	public static Font getFont(String key, int style)
	{
		Font font;

		if (style == SWT.NONE) {
			font = fontRegistry.get(key);		// same as calling getFont(key) with no style
		}
		else if ((style & SWT.BOLD) != 0) {
			font = fontRegistry.getBold(key);
		}
		else if ((style & SWT.ITALIC) != 0) {
			font = fontRegistry.getItalic(key);
		}
		else {
			logger.warn("request for font {} failed, unknown style {}", key, style);
			font = fontRegistry.get(key);		// NOTE: this just returns the default system font
		}
		return font;
	}

	public static Set<String> getFontKeys()
	{
		return fontRegistry.getKeySet();
	}

	public static void checkSystemFont()
	{
		Display display = Display.getDefault();
		Font font = display.getSystemFont();
		FontData data = font.getFontData()[0];
		
		logger.info("system font = {} {}", data.getName(), data.getHeight());
		int count = fontRegistry.getKeySet().size();
		logger.info("font registry size = {}", count);
	}
	
}
