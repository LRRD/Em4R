package com.elmocity.elib.logging.config;


import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;


public class SimpleAppender extends AppenderBase<ILoggingEvent>
{
	@Override
	protected void append(ILoggingEvent arg0)
	{
		// TODO we really dont want to have any SWT dependencies down here... so this needs an interface for
		// callbacks, where we just send the message to some blind interface (which has the SWT Text widget hidden)
		System.out.println("simple appender worked");
	}
}
