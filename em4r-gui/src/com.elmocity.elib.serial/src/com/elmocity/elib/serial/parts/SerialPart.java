
package com.elmocity.elib.serial.parts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ListDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.serial.ISerialIOListener;
import com.elmocity.elib.serial.SerialIO;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.SimpleConsole;
import com.elmocity.elib.util.WireMessage;

public class SerialPart
{
	private static final Logger logger = LoggerFactory.getLogger(SerialPart.class);
	
	Button selectPortButton;
	Button loopbackButton;

	SimpleConsole console;
	
	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is text output console
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "ELib Serial Part", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			selectPortButton = GridHelpers.addButtonToGrid(inputComposite, "Select Port Test");
			selectPortButton.addSelectionListener(new SelectPortListener());

			loopbackButton = GridHelpers.addButtonToGrid(inputComposite, "Loopback Test");
			loopbackButton.addSelectionListener(new LoopBackListener());
		}

		// RIGHT
		Composite consoleComposite = GridHelpers.addGridPanel(c, 1);
		{
			// Right side is a console text area, since the lines of text are short, but numerous
			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			consoleComposite.setLayoutData(gridData);

			console = new SimpleConsole(consoleComposite, SWT.NONE, 10);
		}
	}
	
	@PreDestroy
	public void preDestroy()
	{
	}

	@Focus
	public void onFocus()
	{
		console.setFocus();
	}
	
	// ----------------------------------------------------------------------------------------------------

	@Inject Shell shell;
	
	class SelectPortListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Display model list dialog with known serial ports...");
			
			// Get the list of names for known serial ports, to present to the user
			List<String> known = SerialIO.getKnownSerialPorts();
			if (known == null || known.size() == 0) {
				// TODO LINUX
				MessageDialog.openInformation(shell, "Comm Ports", "There appear to be no serial communications ports configured for this computer or Java VM.  Check the Windows Device Manager to verify Ports are enabled.");
				return;
			}
			
			// Current Selection.  TODO just pre-select the first element in the list.  Should read this back from the running app/document.
			List<String> current = new ArrayList<String>();
			current.add(known.get(0));
			
			// Create the dialog
			ListDialog ld = new ListDialog(shell);
			ld.setTitle("Comm Ports");
			ld.setHelpAvailable(false);
			ld.setMessage("Select a serial comm port that is connected to the table:");
			ld.setAddCancelButton(true);
	
			// Trivial content
			ld.setContentProvider(new ArrayContentProvider());
			ld.setLabelProvider(new LabelProvider());
			ld.setInput(known.toArray());
	
			// Pre-select the current value(s) for the user
			ld.setInitialElementSelections(current);
	
			// Fire off the modal dialog
			int ret = ld.open();
			if (ret == Window.OK) {
				Object[] woof = ld.getResult();
				if (woof != null && woof.length >= 1) {
					// Just going to take the first selection (dialog should not be set to multi-select)
					String portName = (String)(woof[0]);
					logger.debug("Comm Port selection dialog set to {}", portName);
					console.append("User selected " + portName);
				}
			}
			else {
				console.append("Dialog closed without making selection.");
			}

			console.append("Done.");
		}
	}
	
	class LoopBackListener extends SelectionAdapter implements ISerialIOListener
	{
		// We fire off a message, and then sit around waiting for the echo.  Since the SerialIO classes uses and expects the same EOM character on
		// the request message as the response message, a loopback cable should work fine.
		volatile boolean gotResponse;
		volatile String responseMessage;
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// TODO user inputs
			final String commPortName = "COM4";
			final String requestMessage = "The quick brown fox.";

			// Reset the signaler, since we are attached to a GUI button the user can run several times in a row
			// TODO: grey out our button so they can't run again until we timeout and enable the button again.
			gotResponse = false;
			responseMessage = "";
			
			console.append("");
			console.append("Attempting connection to " + commPortName + "...");

			// See if we can snag this serial comm port
			SerialIO connection = new SerialIO();
			if (!connection.startup(commPortName, this)) {
				console.append("Connection failed.");
				connection = null;
			}
			else {
				console.append("Connection succeeded.");
			}
			
			// Send a text string out the port.
			// NOTE the prototype implementation uses '\n' as the EOM marker, so don't let the user enter \n in manual testing.
			// The SerialIO class can escape EOM markers, or make it a long GUI etc
			if (connection != null) {
				console.append("Sending request message...");
				console.append("Payload = '" + requestMessage + "'.");
				
				byte[] buf = requestMessage.getBytes();
				int len = buf.length;
				WireMessage message = new WireMessage(buf, len);
				connection.write(message);
				
				logger.debug("sent length {}", len);
				console.append("Sent.");
				
				// TODO this has to move to a Job so we don't lockup the SWT GUI thread.
				
				// Sit around waiting for the response (TODO signal wake from response callback)
				final long msTimeout = 1000;	// wait for up to 1 second?! in case the Arduino is slammed.
				final long msEachSleep = 1;		// snooze for a few ms to poll the completion.
				for (int i = 0; i < (msTimeout / msEachSleep); i++) {
					if (gotResponse) {
						long approximateWait = i * msEachSleep;
						console.append("Received response message after " + approximateWait + " ms.");
						console.append("Payload = '" + responseMessage + "'.");
						break;
					}
					
					try {
						TimeUnit.MILLISECONDS.sleep(msEachSleep);
					}
					catch (InterruptedException e1) {
						break;
					}
				}
				if (!gotResponse) {
					console.append("Timedout waiting for response message, after " + msTimeout + " ms.");
				}
			}
			
			// Cleanup
			if (connection != null) {
				connection.shutdown();
				connection = null;
				console.append("Connection closed.");
			}
			console.append("Done.");
		}

		@Override
		public void receiveMessage(WireMessage message)
		{
			// This hack handler just assumes one shot send and recv of message, so don't overwrite the first
			// response with subsequent (or spurious zero length) payloads etc.
			if (gotResponse) {
				return;
			}

//			// String constructor only takes a byte array without a length param... so we have to copy the byte array to a perfectly sized array first.
//			byte[] buffer = new byte[len];
//			System.arraycopy(buf, 0, buffer, 0, len);
			
			responseMessage = new String(message.getData());
			if (responseMessage != null) {
				logger.debug("rcvd length {} = {}", responseMessage.length(), responseMessage);
			}
			gotResponse = true;
		}
	}
}