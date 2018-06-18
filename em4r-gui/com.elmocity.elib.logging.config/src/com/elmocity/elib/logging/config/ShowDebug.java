package com.elmocity.elib.logging.config;

/**
 * This logging plugin probably cannot create main menu commands and handlers, because it gets loaded as the first
 * plugin, so that logging services are configured and available to other plugins.  This is just a stub to have
 * some code in this plugin.
 */
public class ShowDebug
{
	public static String getDebugInfo()
	{
		String info = "TODO debug info about state of SLF4J and logback";
		return info;
	}

	public static void showDebug()
	{
		System.out.println("TODO debug info about state of SLF4J and logback");
	}
}
