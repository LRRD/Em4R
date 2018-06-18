package com.elmocity.elib.logging.config;

import java.io.IOException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Initialize the SLF4J logging framework specifically for logback implementations.
 * <p>
 * Changing to a different logging engine is "easy", in that you need only adjust the impl jar here in this plugin and in the
 * product definition (so it loads the different logging engine/plugin/jar at startup).  All other plugins and applications should only
 * use "org.slf4j" as an "imported package", not as a required bundle.  And they should obviously never directly reference logback.
 * <p>
 * Basically, this class (and any java classes in this package that extend AppenderBase<ILoggingEvent> should be the only
 * classes in the entire application that directly references ch.qos.logback classes.
 *
 */
public class Activator implements BundleActivator
{
	// NOTE: do not use a Logger logger object here... this class is configuring and initializing the logger framework.
	// Use stdout or other hacks.
	
	private static BundleContext context;
	static BundleContext getContext()
	{
		return context;
	}

	@Override
	public void start(BundleContext context) throws Exception
	{
		System.out.println("logging config activator - start()");		// DEBUG to stdout
		Activator.context = context;
		
		// We can reconfigure the logback from a user specified file, one from another bundle, or fixed file location here.
		
//		configureLogbackInBundle(context.getBundle());

		// Send an initial message
		Logger logger = LoggerFactory.getLogger(this.getClass());
		logger.info("first logger message after init");
	}

	@Override
	public void stop(BundleContext context) throws Exception
	{
		System.out.println("logging config activator - stop()");		// DEBUG to stdout

		// Send an final message
		Logger logger = LoggerFactory.getLogger(this.getClass());
		logger.info("final logger message at shutdown");

		Activator.context = null;
	}

	// ---------------------------------------------------------------
//	public static void reconfigureLogback()
//	{
//		if (context == null) {
//			// Impossible
//			return;
//		}
//		
//		configureLogbackInBundle(context.getBundle());
//	}
	
	private static void configureLogbackInBundle(Bundle bundle)
	{
		// Verify that the correct org.slf4j.impl.StaticLogger class was loaded.
		// If all the correct plumbing is working at this point, the impl should be of ch.qos.logback.classic.
		// If not, we have multiple versions of the impl loaded at runtime or none, and SLF4J has defaulted to something else.
		LoggerContext context;
		try
		{
			context = (LoggerContext) LoggerFactory.getILoggerFactory();
			System.out.println("logging config activator - appears we have a logback implementation loaded");					// DEBUG to stdout
		}
		catch (Exception e)
		{
			// This will almost always be a ClassCastException
			System.out.println("logging config activator - failed to find logback impl, probably have NOP impl installed");		// DEBUG to stdout
			return;
		}

		// Now that we know logback is loaded as the impl, hunt down the logback.xml file and use that to configure it before
		// the first logger message has gone out.
		try
		{
			// Nuke whatever config is hanging around, probably a default one to console only.
			JoranConfigurator jc = new JoranConfigurator();
			jc.setContext(context);
			context.reset();

			// Several choices to hunt down the file.  For now, we simple include the logback.xml file at the root level of
			// the deployed jar for this plugin.  That makes it really easy to find.
			// SEE BELOW for other ways to find the file.

			// Grab the file from the root of the bundle... it won't be found if you place it in a "res" or "src" sub-folder etc.
			URL logbackConfigFileUrl = FileLocator.find(bundle, new Path("logback.xml"), null);
			if (logbackConfigFileUrl == null) {
				System.out.println("logging config activator - missing logback.xml at bundle root");		// DEBUG to stdout
				return;
			}

			// Found a real config file, so feed it to logback.
			// This probably still does not fire up the loggers, I think it waits until someone finally creates/calls a Logger object.
			jc.doConfigure(logbackConfigFileUrl.openStream());

			System.out.println("logging config activator - reconfig complete from " + logbackConfigFileUrl.toString());	// DEBUG to stdout
		}
		catch (Exception e)
		{
			// This could be various exceptions related to the file system and classpaths
			System.out.println("logging config activator - misc exception while locating logback.xml");		// DEBUG to stdout
			System.out.println(e);
		}
		
		// ALTERNATIVES
		
		// Logback supports a command-line variable to the JVM (prop) so we could use that:
		//		java -Dlogback.configurationFile=/path/to/config.xml chapters.configuration.MyApp1
		//		(see https://logback.qos.ch/manual/configuration.html)
		
		// We could use an environment var, like $USER_HOME or a fixed path too, as the override to find the xml.
		// The Joran code will substitute values if you use {$VAR} style references in the XML file 
		//		String logDirProperty = "C:\\TEMP";		// alternative log directory location
		//		context.putProperty("LOG_DIR", logDirProperty);
		
	}
}
