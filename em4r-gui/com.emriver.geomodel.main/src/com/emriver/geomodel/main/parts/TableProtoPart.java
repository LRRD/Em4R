
package com.emriver.geomodel.main.parts;

import java.awt.Font;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer;
import org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainerElement;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.swt.ColorCache;
import com.elmocity.elib.swt.FontCache;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.ImageCache;
import com.elmocity.elib.util.Calc;
import com.emriver.geomodel.table.Device;
import com.emriver.geomodel.table.EthernetTableConnection;
import com.emriver.geomodel.table.Request;
import com.emriver.geomodel.table.TableController;
import com.emriver.geomodel.table.parts.TableDevicePart;

/**
 * Full application for Table prototype.  This contains fixed location parts, likely to be 1 script management part and 5 device display parts
 */
public class TableProtoPart
{
	// TODO load from config file or hard code to match the flash config on the table
	private final String serverIPAddress = "10.0.8.200";
	private final int serverPort = 40000;

	//SerialTableConnection connection;
	private EthernetTableConnection connection;
	private TableController controller;
	
	public final String globalControllerID = "com.emriver.geomodel.globalcontroller";

	// Server
	Text serverAddressText;
	Text serverPortText;
	Button connectButton;
	
	// Script filename (full path and filename)
	String scriptFilename = "";

	// Timer clock
	Text timerText;
	
	private final String timerZeroTime = "0:00:00";
	
	// List of the 5 device parts that got loaded
	private List<MPart> deviceParts = new ArrayList<MPart>(10);


	
	private static final Logger logger = LoggerFactory.getLogger(TableProtoPart.class);

	
	@Inject UISynchronize sync;

	@PostConstruct
	public void postConstruct(Composite parent, EModelService modelService, MApplication app, EPartService partService, MPart part, MWindow window)
	{
		Composite c = GridHelpers.addGridPanel(parent, 8);
	
		Button selectSciptFileButton = GridHelpers.addButtonToGrid(c, "Open Script File...");
		selectSciptFileButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				FileDialog fd = new FileDialog(Display.getCurrent().getActiveShell(), SWT.OPEN);
				fd.setText("Open");
				fd.setFilterPath("C:/");
				String[] filterExt = { "*.txt", "*.script", "*.*" };
				fd.setFilterExtensions(filterExt);
				String selected = fd.open();
				if (selected != null && selected.length() > 0) {
					scriptFilename = selected;
					logger.debug("user selected script file to load {}", scriptFilename);
				}
			}
		});

		final int bSize = 40;
		
		Button resetScriptButton = new Button(c, SWT.PUSH);
		resetScriptButton.setImage(ImageCache.getScaled(ImageCache.NAV_RESET_ICON, bSize, bSize));
		resetScriptButton.addSelectionListener(new ButtonListener("reset"));

		Button pauseScriptButton = new Button(c, SWT.PUSH);
		pauseScriptButton.setImage(ImageCache.getScaled(ImageCache.NAV_PAUSE_ICON, bSize, bSize));
		pauseScriptButton.addSelectionListener(new ButtonListener("pause"));

		Button runScriptButton = new Button(c, SWT.PUSH);
		runScriptButton.setImage(ImageCache.getScaled(ImageCache.NAV_RUN_ICON, bSize, bSize));
		runScriptButton.addSelectionListener(new ButtonListener("run"));

		Label padding = new Label(c, SWT.NONE);
		GridData gridData = new GridData();
		gridData.widthHint = 500;
		padding.setLayoutData(gridData);
		padding.setText(" ");
		
		timerText = new Text(c, SWT.NONE);
		timerText.setEditable(false);
		timerText.setText(timerZeroTime);
		timerText.setFont(FontCache.getFont(FontCache.EMRIVER_HUGE_TARGET, Font.BOLD));
		gridData = new GridData();
		gridData.horizontalAlignment = SWT.END;
		timerText.setLayoutData(gridData);
		
		
//		// SERVER GROUP
//		{
//			Group serverGroup = new Group(c, SWT.NONE);
//			serverGroup.setText("Server");
//
//			gridData = new GridData();
//			gridData.horizontalAlignment  = GridData.FILL;
//			gridData.horizontalSpan  = 2;
//			serverGroup.setLayoutData(gridData);
//
//			GridLayout layout = new GridLayout(2, false);
//			serverGroup.setLayout(layout);
//		
//			serverAddressText = GridHelpers.addEditorToGrid(serverGroup, "IP Address", serverIPAddress);
//			serverPortText = GridHelpers.addEditorToGrid(serverGroup, "Port", serverPort);
//			
//			connectButton = GridHelpers.addButtonToGrid(serverGroup, "Connect");
//			connectButton.addSelectionListener(new ConnectListener());
//		}

//		// Make a list of all the device parts that exist
//	    MPartSashContainer container = (MPartSashContainer) modelService.find("com.emriver.geomodel.main.partsashcontainer.proto", window);
//		List<MPartSashContainerElement> allParts = container.getChildren();
//		for (MPartSashContainerElement e : allParts)
//		{
////			MPart p = (MPart) e;
//			TableProtoPart p = (TableProtoPart)((MPart) e).getObject();
//			logger.info("port {}", p.serverPort);
//			
////			if (p.getObject() instanceof TableProtoPart) {
////				TableProtoPart woof = (TableProtoPart) p.getObject();
////				logger.info("port {}", woof.serverPort);
////			}
////			else if (p.getObject() instanceof TableDevicePart) {
////				TableDevicePart woof = (TableDevicePart) p.getObject();
////				logger.info("restarting chart {}", woof.getDevice());
////				woof.restartChart();
////			}
//		}

		// This collects the device parts so we can call methods on them all at once.
		// TODO absolutely should be done with command and handlers, but don't understand how to differential between
		// several parts of the same class with the same handlers.  Need to send params to the command handler or something.
		MPart p;
		p = (MPart) modelService.find("com.emriver.geomodel.main.part.pitch", window);
		deviceParts.add(p);
		p = (MPart) modelService.find("com.emriver.geomodel.main.part.roll", window);
		deviceParts.add(p);
		p = (MPart) modelService.find("com.emriver.geomodel.main.part.uppipe", window);
		deviceParts.add(p);
		p = (MPart) modelService.find("com.emriver.geomodel.main.part.downpipe", window);
		deviceParts.add(p);
		p = (MPart) modelService.find("com.emriver.geomodel.main.part.pump", window);
		deviceParts.add(p);
		
		autoConnect();
		
		// Publish any objects we want into the eclipse context (unique for each new part).
		// This allows sharing with handlers instead of needing callbacks into this class.
		IEclipseContext context = app.getContext();
		context.set(globalControllerID, controller);
		context.declareModifiable(globalControllerID);

	}
	
	private class ConnectListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			String serverAddress = serverAddressText.getText();
			int serverPort = Integer.parseInt(serverPortText.getText());

			connection = new EthernetTableConnection(serverAddress, serverPort);
			//connection = new SerialTableConnection();
			controller = new TableController(connection);	// autoconnects?
		}
	}
	
	void autoConnect()
	{
//		String serverAddress = serverAddressText.getText();
//		int serverPort = Integer.parseInt(serverPortText.getText());

		connection = new EthernetTableConnection(serverIPAddress, serverPort);
		//connection = new SerialTableConnection();
		controller = new TableController(connection);	// autoconnects?
	}

	private static String formatTimer(LocalTime startTime, LocalTime now)
	{
		// elapsed seconds
		long es = ChronoUnit.SECONDS.between(startTime, now);
		return String.format("%d:%02d:%02d", es / 3600, (es % 3600) / 60, (es % 60));
	}
	
	// -----------------------------------------------------------------------------
	
	// Generic handler used by all the buttons, actions are based on the unique strings for each button. 
	private class ButtonListener extends SelectionAdapter
	{
		String message;
		ButtonListener(String message)
		{
			this.message = message;
		}
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			logger.debug("user clicked button to '{}' script", message);
			
			switch (message) {
			case "reset":
				// TODO reset
				resetScriptThread();
				break;
			case "pause":
				// TODO pause
				pauseScriptThread();
				break;
			case "run":
				// TODO run
				// Even if we don't have any script to run etc, we still have to reset all the charts on all the device parts
				for (MPart p : deviceParts) {
					TableDevicePart d = (TableDevicePart) p.getObject();
					d.restartChart();
				}
				
				// Read data from file (or use preloaded buffer from selection of filename)
				
				startScriptThread();
				break;
			}
		}
	}
	
	// FAKE SCRIPT stub DEBUG
	// The command thread can send commands, so needs to read one value from the script.
	// Example, TILT device at 17 sec from the start.
	private double getScriptValue(Device device, long timeSinceStart)
	{
		// Fake up some truly random value inside the legal range for this device.   Should really just be a +/- small amount from last known value.
		double range = device.getMax() - device.getMin();
		double newValue = device.getMin() + (range * Calc.rand(0, 100) / 100.0);
		return newValue;
	}
	
	// ------------------------------------------------------
	// Management of script thread
	
	private ScriptThread scriptThread = null;
	
	void startScriptThread()
	{
		if (scriptThread == null) {
			scriptThread = new ScriptThread();
			scriptThread.start();
		}
		else {
			if (scriptThread.isPaused()) {
				scriptThread.togglePause();
			}
		}
	}
	
	void resetScriptThread()
	{
		if (scriptThread != null) {
			scriptThread.interrupt();
			scriptThread = null;
		}
	}

	void pauseScriptThread()
	{
		if (scriptThread != null) {
			scriptThread.togglePause();
		}
	}
	
	// ------------------------------------------------------

	private class ScriptThread extends Thread
	{
		private boolean paused = false;
		public void togglePause()
		{
			paused = !paused;
		}
		public boolean isPaused()
		{
			return paused;
		}
		
		@Override
		public void run()
		{
			super.run();
			
			// TODO Make a copy of the script file or data.
			
			// Keep track of when we started, since the script file is relative to that value
			LocalTime startTime = LocalTime.now();
			
			while (true) {
				try {
					Thread.sleep(10000);
				}
				catch (InterruptedException e) {
					// Asked to exit, almost certainly from someone clicking the RESET button.
					return;
				}
				
				if (paused) {
					// Don't process any commands in the paused state
					continue;
				}
				
				// Update the timer in the controls, so user can see we are still running/alive down here.
				sync.asyncExec(() ->
				{
					if (timerText != null && !timerText.isDisposed()) {
						timerText.setText(formatTimer(startTime, LocalTime.now()));
					}
					
				});
				
				// Actually send commands
				long es = ChronoUnit.SECONDS.between(startTime, LocalTime.now());
				// Iterate the devices
				Device device;
				Request request;
				
//				device = Device.DEV_PITCH;
//				request = new Request(device, TableController.CMD_SET, getScriptValue(device, es), 0);		// Always tell it to move immediately (time = 0)
//				controller.sendRequest(request);
				
				device = Device.DEV_PUMP;
				// Fake up a new value as if from a script
				double newTargetValue = getScriptValue(device, es);
				// Updating a specific part target value, we need to tell the matching device part so the target GUI updates.
				for (MPart p : deviceParts)
				{
					TableDevicePart part = (TableDevicePart) p.getObject();
					part.updateTargetValue(device, newTargetValue);
				}
				request = new Request(device, TableController.CMD_SET, newTargetValue, 10);		// Always tell it to move immediately (time = 0)
				controller.sendRequest(request);
			}
		}

	}
}

