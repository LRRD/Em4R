//package com.elmocity.elib.logging.config;
//
//import ch.qos.logback.classic.Level;
//import ch.qos.logback.classic.PatternLayout;
//import ch.qos.logback.classic.spi.ILoggingEvent;
//import ch.qos.logback.core.AppenderBase;
//
//import org.eclipse.swt.widgets.Display;
//import org.eclipse.swt.widgets.Text;
//
///**
// * Handler class called FROM the logging framework when the logback.xml is configured correctly.
// * <p>
// * This allows us to get the stream of the log message inside the application, so that we can choose
// * to display ERROR level message in a Text control in the GUI (or popup etc).
// * <br>
// * Be careful with -import- commands, as this code is being called from logback natively, not via the SLF4J layer.
// */
//public class LogbackWidgetAppender extends AppenderBase<ILoggingEvent> 
//{
//	// TODO this does not work for custom created objects like this class.
//	// The logback jar creates us using some rudimentary reflection constructor I think, so we don't get
//	// injections to work, or may not even have a IEclipseContext when initially created.  Dunno.
//	
////	@Inject
////	private static UISynchronize sync;
//	
//	private static PatternLayout patternLayout = null;
//	
//	// TODO would be nice to allow the application to call into us (statically) and provide a desired Level.
//	// But these constants are the logback values, not the slf4j values.  Since the application should never
//	// import logback packages/constants, we need a conversion routine so they can pass in slf4j values. 
//	private static Level filterLevel = Level.DEBUG;
//	
//	// HACK
//	// Some high level application object will have to set our Text widget after the GUI has gone through layout etc.
//	// TODO this should be done with injection or registering an E4 Model object we can lookup as needed. 
//	private static Text appenderWidget;
//	public static void setAppenderWidget(Text appenderWidget)
//	{
//		LogbackWidgetAppender.appenderWidget = appenderWidget;
//		System.out.println("logback appender class now has an attached SWT Text control");
//	}
//	
//	@Override
//	public void start()
//	{
//		// TODO could filter down to the stuff we want.  This could be done with a SifterAppender nested in the XML config,
//		// or we could do the filtering manually here.  (like just a WARN/ERROR level) or only things *without* a robot/order.
//		
//		// TODO the scrolling text area could be set to monospaced font, then we could format with widths.
//		// TODO or we could be sending this stuff to a tableViewer so there are columns and the user could even sort etc...
//		// TODO we could also change colors for each line based on level, markers, MDC, robot/order, etc...
//		
//		if (patternLayout == null) {
//			patternLayout = new PatternLayout();
//			patternLayout.setContext(getContext());
//			patternLayout.setPattern("%d{HH:mm:ss.SSS} %level - %msg%n");
//			patternLayout.start();
//		}
//		
//		super.start();
//		
//		// NOTE we cannot send an initial message to the GUI Text widget at this point, too early in the startup sequence.
//		System.out.println("logback appender class started (by logback.xml we assume)");
//	}
//
//	@Override
//	protected void append(ILoggingEvent event)
//	{
//		// Formata mensagem do log
//
//		if (event.getLevel().isGreaterOrEqual(filterLevel)) {		// NOTE comparing to native logback level, not the slf4j level. eek
//			String formattedMsg = patternLayout.doLayout(event);
//			appendToGUI(formattedMsg);
//		}
//
//	}
//
//	/**
//	 * Send a text line to the GUI log, showing startup parameters or Level filter.
//	 * <br>This is not sent to any FILE or CONSOLE outputs via SLF4J.
//	 * <p>
//	 * This is called by the main application startup code, AFTER it has the logging framework initialized.
//	 */
//	public static void showLevel()
//	{
//		appendToGUI("Showing " + filterLevel +  " or higher level messages only.");
//	}
//
//	
//	// This is where the SLF4J/logback machinery calls to display an in-application console (via the SSWidgetAppender util).
//	// TODO this should be 2 methods, one for callers on the GUI thread, and one for not.  We can test ourselves for now.
//	private static void appendToGUI(String s)
//	{
//		// TODO: this gets called 2 different ways: (A) from the logger framework via SSWidgetAppender and (B) from Job threads.
//		// This creates problems because the logger is not being run in the GUI threads, but the job threads are using asynchExec
//		// so those show up here running on the GUI thread.
//
//		// The logging framework could call us with messages before the GUI is up and running, or during application shutdown as the widgets
//		// are being disposed. It is acceptable to just discard them, since they will be shown in the debugger console and FILE object too.
//		if (appenderWidget == null || appenderWidget.isDisposed()) {
//			return;
//		}
//
//		// TODO not sure how to get a real sync object, but supposedly this is all it does anyway.
//		// https://stackoverflow.com/questions/41997279/how-to-inject-objects-in-custom-object
//		Display.getDefault().asyncExec( () -> appenderWidget.append(s + "\n") );
//	}
//	
//}
