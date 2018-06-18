package com.elmocity.elib.util;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("restriction")
public class SystemInfo
{
	// NOTE: probably need a Concurrent version of this, or local code to synchronize.  Not one in JDK 8, there is one in Guava.
	static final Map<String, String> info = new LinkedHashMap<String, String>(50);

	static final Logger logger = LoggerFactory.getLogger(SystemInfo.class);

	static
	{
		updateFromManagement();
		updateFromJVM();
	}

	public static String getSystemInfo(String key)
	{
		return info.get(key);
	}

	public static Set<String> getKeySet()
	{
		return info.keySet();
	}

	/**
	 * Dump all the system info we know about to the logger framework.
	 */
	public static void debugLogSystemInfo()
	{
		Set<String> keySet = info.keySet();
		for (String key : keySet) {
			String line = String.format("%s = %s", key, info.get(key));
			logger.trace(line);
		}
	}


	// ------------------------------------------------------------------

	/**
	 * Use the restricted Sun interfaces to read some information about CPU load.
	 * <p>
	 * May not work on a Linux host.  Very unlikely to work on a minimal/phone/imbedded JVM.
	 * <p>
	 * Eclipse enforces the access restrictions at compile time, so you need to override that setting:
	 * <br>Project - Properties -> Java Compiler -> Errors / Warnings ->
	 * <br>Enable Project specific settings -> Deprecated and restricted API -> Forbidden reference : Warning
	 * 
	 */
	private static void updateFromManagement()
	{

		// Wrapper interface that should hide the native utility library
		// NOTE: this is "com.sun.management.OperatingSystemMXBean", not the generic java.lang.management one!
		OperatingSystemMXBean osmxb = null;

		// First try to just create the object, and bail if the class is completely missing
		try {
			// The factory and class loader might fail to find it on minimal VM installations
			osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		}
		catch (Exception e)
		{
			logger.warn("OSMXB creation failed", e);
			return;
		}

		// Now that we have the object, still be scared that the restricted methods might explode
		try {
			// Unrestricted methods
			String osName = osmxb.getName(); 
			String osVersion = osmxb.getVersion(); 
			String osArchitecture = osmxb.getArch(); 
			int osProcessors = osmxb.getAvailableProcessors();

			info.put("OS Name", osName);
			info.put("OS Version", osVersion);
			// TODO: this patch level might be more detailed than the Bean Version
			info.put("OS Patch Level", System.getProperty("sun.os.patch.level"));
			info.put("OS Architecture", osArchitecture);
			info.put("OS Processors", "" + osProcessors);

			//			logger.trace("os {}, osversion {}, arch {}, CPUs {}", osName, osVersion, osArchitecture, osProcessors);

			// Restricted methods
			long mem = osmxb.getTotalPhysicalMemorySize();
			String memString = Format.bytesToGBString(mem);

			info.put("OS Physical RAM", memString);

			//			logger.trace("Physical RAM (VM) = " + memString);
		}
		catch (Exception e)
		{
			logger.warn("OSMXB getter failed", e);
			return;
		}

		// Restricted methods - can return -1 if this feature is not supported, or sometime just on a given sample!
		double systemLoad = osmxb.getSystemCpuLoad();
		double javaLoad = osmxb.getProcessCpuLoad();
		long freePhysicalMemory = osmxb.getFreePhysicalMemorySize();

		//		logger.trace("sysload {}, javaload {}, freeMem {}", systemLoad, javaLoad, freePhysicalMemory);
	}

	private static void updateFromJVM()
	{
		// Java related
		info.put("Java Vendor", System.getProperty("java.vendor"));
		info.put("Java Version", System.getProperty("java.version"));

		info.put("JVM Name", System.getProperty("java.vm.name"));
		info.put("JVM Version", System.getProperty("java.vm.version"));

		
		// Monitor related
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gs = ge.getScreenDevices();

		// bit deptch could be BIT_DEPTH_MULTI -1
		// refresh rate could be REFRESH_RATE_UNKNOWN 0 

		for (int i = 0; i < gs.length; i++) {
			DisplayMode dm = gs[i].getDisplayMode();
			String monitorInfo = String.format("%4d x %4d %d-bit %d hz", dm.getWidth(), dm.getHeight(), dm.getBitDepth(), dm.getRefreshRate());
			info.put("Monitor " + (i + 1), monitorInfo);
		}

		// Misc
		info.put("TimeZone", System.getProperty("user.timezone"));
	}
}
