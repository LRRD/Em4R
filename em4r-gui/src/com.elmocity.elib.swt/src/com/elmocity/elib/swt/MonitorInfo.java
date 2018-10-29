package com.elmocity.elib.swt;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class MonitorInfo
{
	private static final Logger logger = LoggerFactory.getLogger(MonitorInfo.class);
	
	public static String debugMonitors()
	{
		String result = "";
		
		// Just one Display() object
		Display display = Display.getDefault();
		Monitor[] monitors = display.getMonitors();
		result += "Found " + monitors.length + " monitors.\n";
		
		for (int i = 0; i < monitors.length; i++) {
			String s = String.format("   %d, x = %5d, y = %5d", i, monitors[i].getBounds().x, monitors[i].getBounds().y);
			logger.debug("monitor " + s);
			result += s + "\n";
		}
		
		return result;
	}
	
	/*
	 * Ask for the DEBUG shell window to be moved to monitor X (zero-based).
	 * <p>
	 * If there are fewer monitors, then the main shell will not be moved or resized.
	 */
	public static void moveDebugShellToMonitor(int desiredMonitor)
	{
		// Just one Display() object
		Display display = Display.getDefault();

		Monitor[] monitors = display.getMonitors();
		// Select a monitor - the X, Y bounds of all the monitors are in one big coordinate space (for Windows) acting like one giant monitor.
		if (monitors.length > desiredMonitor) {
			logger.debug("moving shell to monitor {} of {}", desiredMonitor, monitors.length);
			
			final String debugShellName = "com.elmocity.elib.main.window.debug";
			Shell[] shells = Display.getDefault().getShells();
			for (int i = 0; i < shells.length; i++) {
				if (shells[i].getText().contains("Debug")) {
					shells[i].setLocation(monitors[desiredMonitor].getBounds().x + 20, monitors[desiredMonitor].getBounds().y + 20);
					logger.debug("moved shell");
				}
			}

//			
//			Shell shell = Display.getDefault().getActiveShell();
//			shell.setLocation(monitors[desiredMonitor].getBounds().x, monitors[desiredMonitor].getBounds().y);
			//shell.setMaximized(true);
		}
	}
}
