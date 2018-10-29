
package com.elmocity.elib.net.parts;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.net.IUDPServerGuts;
import com.elmocity.elib.net.UDPServer;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.SimpleConsole;
import com.elmocity.elib.util.WireMessage;

public class UDPServerPart implements IUDPServerGuts
{
	Text portText;
	Button startButton;
	Button stopButton;

	SimpleConsole console;
	
	private static final Logger logger = LoggerFactory.getLogger(UDPServerPart.class);
	

	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is text output console
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "ELib UDP Server", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			portText = GridHelpers.addEditorToGrid(inputComposite, "Listen on port", "4445");
			
			startButton = GridHelpers.addButtonToGrid(inputComposite, "Start UDP Server");
			startButton.addSelectionListener(new StartListener());

			stopButton = GridHelpers.addButtonToGrid(inputComposite, "Stop UDP Server");
			stopButton.addSelectionListener(new StopListener());
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

//	@Inject Shell shell;
	
	@Inject
	private static UISynchronize sync;
	
	UDPServer server = null;
	
	class StartListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			if (server != null) {
				console.append("Rejecting start.. already running.");
				return;
			}
			
			int port = Integer.parseInt(portText.getText());

			console.append("Starting UDP server on port " + port + "...");
			
			server = new UDPServer();
			server.start(port, UDPServerPart.this);

			console.append("Done.");
		}
	}

	class StopListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			if (server == null) {
				console.append("Rejecting stop.. nothing running.");
				return;
			}
			
			console.append("Stopping UDP server...");
			
			server.stop();
			server = null;

			console.append("Done.");
		}
	}
	
	/**
	 * Given a UDP packet payload, decide what the appropriate reply is and have the server send that back to the requester.
	 */
	@Override
	public WireMessage responseToPacket(WireMessage request)
	{
		// WARNING not running on UI thread
		
		sync.asyncExec( () -> {
			console.append("Request rcvd = " + request);
		});
//		logger.debug("UDP server got request '{}'", request);
		
		String responseString = "I don't know.";
		
		sync.asyncExec( () -> {
			console.append("Response sent = " + responseString);
		});
		
//		logger.debug("UDP server sending response '{}'", responseString);

		byte[] buf = responseString.getBytes();
		WireMessage response = new WireMessage(buf, buf.length);
		return response;
	}
}