package com.elmocity.elib.swt;

/*
 * TODO need injection service that allows any code in the application to send a text message (line) to the user GUI.
 * <p>
 * This could be a status line at the bottom of the application main frame, it could be a scrolling Text "console" view... dunno.
 */
public interface IStatusMessage
{
	/*
	 * Take a single line of text, and display it to the user somehow, or discard if no GUI object is available/set.
	 */
	// NOTE: really don't want to name this "log" like everything else.
	// TODO: this could include a severity (to show different colors maybe) or error code too
	void send(String s);
}
