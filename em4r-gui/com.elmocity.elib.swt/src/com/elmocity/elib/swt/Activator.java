package com.elmocity.elib.swt;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.elmocity.elib.swt"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	private final static Logger logger = LoggerFactory.getLogger(Activator.class);

	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		
		logger.info("" + PLUGIN_ID + " start()");
		
		// TODO we could touch the registry classes here, so that the static initialization loads up all the default fonts/colors now.
		// By default, it will lazy load when/if someone uses the class.  Might be nice to see errors at startup.
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
		
		logger.info("" + PLUGIN_ID + " stop()");
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

}
