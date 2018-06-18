package com.elmocity.ss.ib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ib.controller.ApiConnection.ILogger;

/**
 * Class for wrappering the required IB specific loggers, used by the controller.  Typically create 2 of these objects, one for IN and one for OUT.
 * <p>
 * These allow trace output of the raw TCP socket connection between this app (via APIController) and the TWS instance. 
 *
 */
public class IBLogger implements ILogger
{
	Logger logger = LoggerFactory.getLogger(this.getClass());

	boolean suppress = true;
	
	// TODO add a constructor that takes a string name, like IN and OUT to show in the logger
	
	// IB can call us with errors or alerts or trace, no way to tell the log level of the messages it sends us.
	@Override
	public void log(String valueOf)
	{
		// TODO this needs to be formatted and filtered somewhat, or be a TRACE output type.
		// TODO programmatically toggle the suppress flag so we could turn it on temporarily for a section of code.
		if (!suppress) {
			logger.trace(valueOf);				// TODO: could parse the string to see if it is obviously an error/warn/info.  IB Error codes?
		}
	}
}
