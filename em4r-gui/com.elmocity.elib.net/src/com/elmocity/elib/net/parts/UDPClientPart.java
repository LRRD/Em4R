
package com.elmocity.elib.net.parts;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.net.IUDPListener;
import com.elmocity.elib.net.UDPClient;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.SimpleConsole;
import com.elmocity.elib.util.WireMessage;

public class UDPClientPart implements IUDPListener
{
	Text serverAddressText;
	Text serverPortText;
	
	Button startButton;
	Button stopButton;
	Button sendRequestButton;
	
	SimpleConsole console;
	
	private static final Logger logger = LoggerFactory.getLogger(UDPClientPart.class);
	
	UDPClient client = new UDPClient();

	
	@Inject
	UISynchronize sync;

	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// Load any saved prefs from the InstanceScope
		Preferences preferences = InstanceScope.INSTANCE.getNode("com.elmocity.elib.prefs");
		Preferences node = preferences.node("updnode");
		String defaultServerAddress = node.get("address", "localhost");
		String defaultServerPort = node.get("port", "1000");

		// 2 columns, left is input parameters, and right is text output console
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "ELib UDP Server", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			serverAddressText = GridHelpers.addEditorToGrid(inputComposite, "Server Address", defaultServerAddress);
			serverPortText = GridHelpers.addEditorToGrid(inputComposite, "Server Port", defaultServerPort);
			
			startButton = GridHelpers.addButtonToGrid(inputComposite, "Start Socket");
			startButton.addSelectionListener(new StartListener());

			stopButton = GridHelpers.addButtonToGrid(inputComposite, "Stop Socket");
			stopButton.addSelectionListener(new StopListener());

			sendRequestButton = GridHelpers.addButtonToGrid(inputComposite, "Send UDP Request");
			sendRequestButton.addSelectionListener(new SendRequestListener());
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

	class StartListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent event)
		{
			String serverAddress = serverAddressText.getText();
			int serverPort = Integer.parseInt(serverPortText.getText());

			// Save the values the user entered off to the InstanceScope for next time
			Preferences preferences = InstanceScope.INSTANCE.getNode("com.elmocity.elib.prefs");
			Preferences node = preferences.node("updnode");
			node.put("address", serverAddress);
			node.put("port", "" + serverPort);
			try {
				// Forces the application to save the preferences
				preferences.flush();
			}
			catch (BackingStoreException e) {
				logger.warn("UDP prefs save failed");
			}
		
			
			console.append("");
			
			console.append("Starting UDP client socket...");
			
			
			boolean worked = client.startup(serverAddress, serverPort, UDPClientPart.this);
			if (!worked) {
				console.append("Failed.");
//				logger.debug("UDP client failed to startup for {} {}", serverAddress, serverPort);
			}
			
			console.append("Done.");
		}
	}

	class StopListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			
			console.append("Stopping UDP client scoket...");
			
			client.shutdown();

			console.append("Done.");
		}
	}

	class SendRequestListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			
			console.append("Sending UDP request...");
			
			String request = "I want some info from you...";
			console.append("Request = " + request);
			
			byte[] buf = request.getBytes();
			WireMessage message = new WireMessage(buf, buf.length);
			
			client.sendRequest(message);

			console.append("Done.");
		}
	}

	@Override
	public void receiveMessage(WireMessage message)
	{
		sync.asyncExec( () -> {
			String reply = new String(message.getData());
			console.append("Recvd UDP reply...");
			console.append("Response = " + reply);
		});
	}

}