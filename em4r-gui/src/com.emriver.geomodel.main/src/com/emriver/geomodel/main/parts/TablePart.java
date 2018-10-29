package com.emriver.geomodel.main.parts;

import java.awt.Font;
import java.awt.Toolkit;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.IAxisSet;
import org.swtchart.IAxisTick;
import org.swtchart.ILineSeries;
import org.swtchart.ISeriesSet;
import org.swtchart.LineStyle;
import org.swtchart.Range;
import org.swtchart.ILineSeries.PlotSymbolType;
import org.swtchart.ISeries.SeriesType;

import com.elmocity.elib.swt.ColorCache;
import com.elmocity.elib.swt.FontCache;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.ImageCache;
import com.elmocity.elib.swt.ShuttleComposite;
import com.elmocity.elib.swt.ShuttleComposite.ShuttleListener;
import com.elmocity.elib.ui.PartUtils;
import com.elmocity.elib.util.Calc;
import com.elmocity.elib.util.Format;
import com.emriver.geomodel.table.Device;
import com.emriver.geomodel.table.EthernetTableConnection;
import com.emriver.geomodel.table.Request;
import com.emriver.geomodel.table.Response;
import com.emriver.geomodel.table.SerialTableConnection;
import com.emriver.geomodel.table.TableController;

public class TablePart
{
	// Server
	Text serverAddressText;
	Text serverPortText;
	Button connectButton;

	// Device
	Device device = Device.DEV_PITCH;
	Combo deviceCombo;
	Label deviceRunningStatus;

	// Target
	Text targetValueText;
	volatile double targetValue = 0.00;
	ShuttleComposite shuttle;
	
	Button goButton;

	Text targetTimeText;
	volatile int targetTime = 0;

//	Button upButton;
//	Button downButton;

	// Actual
	Text actualValueText;
	
	// Chart
	Chart chart;
	LocalTime chartStartTime;		// X axis time on the chart is relative to a start time, not wall clock time

	
	//SerialTableConnection connection;
	EthernetTableConnection connection;
	TableController controller;

	
	private static final Logger logger = LoggerFactory.getLogger(TablePart.class);

	// TODO put this in a config location for all 5 devices
	private double targetMin = device.getMin();
	private double targetMax = device.getMax();
	private double targetStep = device.getStep();
	// TODO read the IP connection values from prefs etc
	private final String defaultIPAddress = "10.0.8.200";
	private final String defaultPort = "40000";


	@Inject
	UISynchronize sync;

	@PostConstruct
	public void postConstruct(Composite parent, MPart part)
	{
		// If we construct several of this same part via the XML/fragment E4 mechanism, we have to send in
		// a param that tells us which device we should default to monitoring.
		// Alternatively, we could make this a baseclass, and make 5 or so derived classes that just had one line
		// of code to set the device.  That would let XML/fragments pick the one by classname instead of param
		//
		// Example of reading params from the XML:
		//
		//		// We use a custom "variable" on the "supplementary" tab of the application.e4xml file to tell use what device each part should
		//		// initially load itself with.  The user can edit it later if needed.   The variable is just a string and looks like "DEVICE=TILTX".
		//		List<String> vars = part.getVariables();
		//		for (int i = 0; i < vars.size(); i++) {
		//			if (vars.get(i).startsWith("DEVICE=")) {
		//				String dev = vars.get(i).substring(7);
		//				if (dev != null && dev.length() > 0) {
		//					logger.debug("part found DEVICE= context variable. using value {}", dev);
		//					device = dev;
		//				}
		//			}
		//		}

		// 2 columns, left is input parameters and right is chart
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			// Have inputComposite stick to the top of the screen, instead of centering/filling vertically.
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);

			// TITLE
			GridHelpers.addTitlePanel(inputComposite, "Table Controller", 2);

			// SERVER GROUP
			{
				Group serverGroup = new Group(inputComposite, SWT.NONE);
				serverGroup.setText("Server");

				gridData = new GridData();
				gridData.horizontalAlignment  = GridData.FILL;
				gridData.horizontalSpan  = 2;
				serverGroup.setLayoutData(gridData);

				GridLayout layout = new GridLayout(2, false);
				serverGroup.setLayout(layout);
			
				serverAddressText = GridHelpers.addEditorToGrid(serverGroup, "IP Address", defaultIPAddress);
				serverPortText = GridHelpers.addEditorToGrid(serverGroup, "Port", defaultPort);
				
				connectButton = GridHelpers.addButtonToGrid(serverGroup, "Connect");
				connectButton.addSelectionListener(new ConnectListener());
			}

			// DEVICE GROUP
			{
				Group deviceGroup = new Group(inputComposite, SWT.NONE);
				deviceGroup.setText("Device");
				
				gridData = new GridData();
				gridData.horizontalAlignment  = GridData.FILL;
				gridData.horizontalSpan  = 2;
				deviceGroup.setLayoutData(gridData);

				GridLayout layout = new GridLayout(4, false);
				deviceGroup.setLayout(layout);

				// Hand-layout for 4 controls horizontally
				
				Label l = new Label(deviceGroup, SWT.NONE);
				l.setText("Device:");

//				Text deviceText = new Text(deviceGroup, SWT.SINGLE | SWT.BORDER);
//				deviceText.setText(device.toString());
//
//				gridData = new GridData();
//				gridData.widthHint = 80;
//				deviceText.setLayoutData(gridData);

				deviceCombo = new Combo(deviceGroup, SWT.DROP_DOWN | SWT.SIMPLE | SWT.READ_ONLY);
				
				// Fill up the combo from the list of devices.
				// Kinda gross.  Need to add both the display string and a matching reverse lookup int value, for each device.
				for (Device d : Device.values()) {
					// Skip the kludge UNKNOWN
					if (d == Device.DEV_UNKNOWN) {
						continue;
					}
					deviceCombo.add(d.toString());
					deviceCombo.setData(d.toString(), d.getNumValue());
				}

				
				deviceCombo.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						int id = (Integer)deviceCombo.getData(deviceCombo.getText());
						logger.debug("combo selected device id {} = {}", id, Device.getByValue(id));
						device = Device.getByValue(id);
						deviceChanged();
					}
				});
				deviceCombo.select(0);

				Button updateButton = GridHelpers.addTransparentButtonToGrid(deviceGroup, "Start/Stop");
				updateButton.addSelectionListener(new GoListener());
				
//				Composite bComp = new Composite(deviceGroup, SWT.NONE);
//				bComp.setLayout(new GridLayout());
//				bComp.setBackgroundMode(SWT.INHERIT_FORCE);
//				{
//					Button updateButton = new Button(bComp, SWT.PUSH);
////					updateButton.setBackground(ColorCache.getColor(ColorCache.FRIDAY));
//					updateButton.setText("Start/Stop");
//					updateButton.addSelectionListener(new GoListener());
//				}
				
				deviceRunningStatus = new Label(deviceGroup, SWT.NONE);
				deviceRunningStatus.setText("Stopped.");

			}

			// TARGET GROUP
			{
				Group targetGroup = new Group(inputComposite, SWT.NONE);
				targetGroup.setText("Target");

				gridData = new GridData();
				gridData.verticalAlignment  = GridData.FILL;
				targetGroup.setLayoutData(gridData);

				GridLayout targetLayout = new GridLayout(2, false);
				targetGroup.setLayout(targetLayout);

				Label l = new Label(targetGroup, SWT.NONE);
				l.setText("Value:");

				targetValueText = new Text(targetGroup, SWT.BORDER);
				targetValueText.setText(formatTargetValue(targetValue));
				gridData = new GridData();
				gridData.widthHint = 100;
				targetValueText.setLayoutData(gridData);

				Label l2 = new Label(targetGroup, SWT.NONE);
				l2.setText("Time (sec):");

				targetTimeText = new Text(targetGroup, SWT.BORDER);
				targetTimeText.setText("" + targetTime);	// just an int (seconds)
				gridData = new GridData();
				gridData.widthHint = 100;
				targetTimeText.setLayoutData(gridData);

				goButton = new Button(targetGroup, SWT.PUSH);
				goButton.setText("APPLY");
				goButton.addSelectionListener(new MoveTargetListener());
				Label lx = new Label(targetGroup, SWT.NONE);
				lx.setText(" ");

				shuttle = new ShuttleComposite(targetGroup, SWT.BORDER);
				shuttle.addShuttleListener(new MyShuttleListener());
				shuttle.setShuttleParams(targetMin, targetMax, targetStep);
				shuttle.setShuttleValue(targetValue);

			}

			// ACTUAL GROUP
			{
				Group actualGroup = new Group(inputComposite, SWT.NONE);
				actualGroup.setText("Actual");

				GridLayout actualLayout = new GridLayout(1, false);
				actualGroup.setLayout(actualLayout);

				gridData = new GridData();
				gridData.verticalAlignment  = GridData.FILL;
				gridData.widthHint = 120;
				actualGroup.setLayoutData(gridData);

				actualValueText = new Text(actualGroup, SWT.BORDER | SWT.RIGHT);
				actualValueText.setText("8.70");
				
				// Make it big and scary
//				actualValueText.setEnabled(false);	// Unfortunately, this greys down the text and the background too
				actualValueText.setEditable(false);	// This only greys down the background.
				actualValueText.setFont(FontCache.getFont(FontCache.EMRIVER_HUGE_ACTUAL, Font.BOLD));
				actualValueText.setForeground(ColorCache.getColor(ColorCache.MONDAY));
				actualValueText.setBackground(ColorCache.getColor(ColorCache.FRIDAY));
				actualValueText.setEditable(false);	// This only greys down the background.

			}
		}

		// RIGHT
		{
			Group graphGroup = new Group(c, SWT.NONE);
			graphGroup.setText("Graph");

			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			graphGroup.setLayoutData(gridData);

			GridLayout graphLayout = new GridLayout(1, false);
			graphGroup.setLayout(graphLayout);

			chart = PartUtils.makeChartComposite(graphGroup);
			//			chart = new ChartX(graphGroup, SWT.NONE);
			//			graph.setSize(300, 100);		// set title, labels, scale, size etc

			Button clearButton = new Button(graphGroup, SWT.PUSH);
			clearButton.setText("Clear Data");
			clearButton.addSelectionListener(new ClearListener());

			Button resetYAxisButton = new Button(graphGroup, SWT.PUSH);
			resetYAxisButton.setText("Reset Y Axis");
			resetYAxisButton.addSelectionListener(new ResetYAxisListener());

			// Fix up the chart axes and legends etc
			initChart();
		}



		//		// Publish any objects we want into the eclipse context (unique for each new part).
		//		// This allows sharing with handlers instead of needing callbacks into this class.
		//        IEclipseContext context = part.getContext();
		//        context.set(SGraph.graphContextKey, graph);
		//        context.declareModifiable(SGraph.graphContextKey);
	}

	@Focus
	public void setFocus()
	{
//		if (upButton != null) {
//			upButton.setFocus();
//		}
	}

	void deviceChanged()
	{
		// Fix the value field
		targetValue = 0.00;
		targetValueText.setText("" + targetValue);
		
		// Fix the shuttle
		targetMin = device.getMin();
		targetMax = device.getMax();
		targetStep = device.getStep();
		shuttle.setShuttleParams(targetMin, targetMax, targetStep);
		shuttle.setShuttleValue(targetValue);
		
		// Fix the chart
		// Remove the old series from the chart before we fix up the series names
		chart.getSeriesSet().deleteSeries(targetSeriesID);
		chart.getSeriesSet().deleteSeries(actualSeriesID);

		initChart();
	}
	
	// ----------------------------------------------------------------

	// These change based on the currently selected device
	String targetSeriesID = "?";
	String actualSeriesID = "?";
	final String targetSeriesDisplayName = "Target";
	final String actualSeriesDisplayName = "Actual";

	// Reconfigure the chart based on the current Device selection.
	void initChart()
	{
		// Compute or fetch all the needed strings and limits
		String chartTitle = device.toString();
		targetSeriesID = device.name() + "_target";
		actualSeriesID = device.name() + "_actual";

		String xAxisTitle = "Time (seconds)";
		String yAxisTitle = "?";
		switch (device) {
		case DEV_PITCH:
		case DEV_ROLL:
			yAxisTitle = "Slope (degrees)";
			break;
		case DEV_UPPIPE:
		case DEV_DOWNPIPE:
			yAxisTitle = "Height (mm)";
			break;
		case DEV_PUMP:
			yAxisTitle = "Flow (mL/sec)";
			break;
		}

		
		// Apply all these values to the chart
		
		final Color textColor = ColorCache.getColor(ColorCache.BLACK);				// all titles, axis ticks, axis labels etc
		final Color targetLineColor = ColorCache.getColor(ColorCache.MONDAY);		// data points
		final Color actualLineColor = ColorCache.getColor(ColorCache.TUESDAY);		// data points


		chart.getTitle().setText(chartTitle);
		chart.getTitle().setForeground(textColor);;


		// Get the container for all the data series
		ISeriesSet seriesSet = chart.getSeriesSet();

		// Create a new single series for target
		ILineSeries targetSeries = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, targetSeriesID);
		targetSeries.setDescription(targetSeriesDisplayName);
		targetSeries.enableStep(true);
		targetSeries.setLineColor(targetLineColor);
		targetSeries.setLineStyle(LineStyle.DASH);
		targetSeries.setLineWidth(targetSeries.getLineWidth() + 2);
		targetSeries.setSymbolColor(targetLineColor);
		targetSeries.setSymbolSize(targetSeries.getSymbolSize() + 1);
		targetSeries.setSymbolType(PlotSymbolType.TRIANGLE);
		targetSeries.setAntialias(SWT.ON);

		// Create a new single series for actual
		ILineSeries actualSeries = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, actualSeriesID);
		actualSeries.setDescription(actualSeriesDisplayName);
		actualSeries.setLineColor(actualLineColor);
		actualSeries.setLineWidth(actualSeries.getLineWidth() + 2);
		actualSeries.setSymbolColor(actualLineColor);
		actualSeries.setSymbolSize(actualSeries.getSymbolSize() + 3);
		actualSeries.setSymbolType(PlotSymbolType.CROSS);
		actualSeries.setAntialias(SWT.ON);
		

		// Get the container for all the axis data
		IAxisSet axisSet = chart.getAxisSet();

//		// Update the axis min and max, based on the actual data in all the series set right now
//		axisSet.adjustRange();

		// Fix the colors of the axis numeric values to plot black instead of the pukey blue default
		IAxis xAxis = axisSet.getXAxis(0);
		IAxisTick xTick = xAxis.getTick();
		xTick.setForeground(textColor);
		xTick.setTickMarkStepHint(150);

		IAxis yAxis = axisSet.getYAxis(0);
		IAxisTick yTick = yAxis.getTick();
		yTick.setForeground(textColor);

		// Set the axis titles
		xAxis.getTitle().setText(xAxisTitle);
		xAxis.getTitle().setForeground(textColor);
		yAxis.getTitle().setText(yAxisTitle);
		yAxis.getTitle().setForeground(textColor);

		resetYChartAxis();
		
		chart.redraw();		
	}
	
	private void resetYChartAxis()
	{
		if (chart != null) {
			// Instead of updating the Y axis from the range of real data, we can alternatively force a fixed range.
			final double slop = (targetMax - targetMin) * 0.01;	// 1% of range?
			IAxisSet axisSet = chart.getAxisSet();
			axisSet.getYAxes()[0].setRange(new Range(targetMin - slop, targetMax + slop));
			chart.redraw();		
		}
	}

	void addDataPoint(String seriesID, double xValue, double yValue)
	{
		// HACK, literally extending the X and Y data arrays by one at a time, barf, by resizing and copying.
		double[] x = chart.getSeriesSet().getSeries(seriesID).getXSeries();
		x = Arrays.copyOf(x, x.length + 1);
		x[x.length - 1] = xValue;	// x.length;
		chart.getSeriesSet().getSeries(seriesID).setXSeries(x);

		double[] y = chart.getSeriesSet().getSeries(seriesID).getYSeries();
		y = Arrays.copyOf(y, y.length + 1);
		y[y.length - 1] = yValue;	// Calc.rand(20, 90) / 10.00;
		chart.getSeriesSet().getSeries(seriesID).setYSeries(y);

		sync.asyncExec( () -> {

			// We assume the Y axis is fixed to the known range of the chart, like a table can tilt only from 0 to 10 degrees.

			// The default X axis "adjustRange()" only leaves small gaps on the left and right (like 1 sec).
			// We want to leave 1 sec on the left, but want to extend in "blocks" of like 30 seconds on the right, so that as single
			// data points come in, they fill in that blank area, instead of constantly rescaling the X axis to just barely fit the data.

			// Have the chart package recompute the default range for the data we now have in the series (with 1 sec edges)
			IAxis xAxis = chart.getAxisSet().getXAxes()[0];
			xAxis.adjustRange();						// NOTE: this does not cause a redraw

			// We want a right edge gap of between 1 sec to 30 sec
			Range defaultXRange = xAxis.getRange();		// Read back what the package computed
			defaultXRange.upper = ((int)((defaultXRange.upper + 29.0) / 30)) * 30 + 1;		// + 1 so if we filled the whole block, there is still a gap
			defaultXRange.lower = -1.0;
			xAxis.setRange(defaultXRange);
			//			logger.trace("{} {}", defaultXRange.lower, defaultXRange.upper);

			// We have to force the replot ourselves, which is good that we can do several changes then just one replot.
			if (!chart.isDisposed()) {
				chart.redraw();
			}
		});

	}
	
	/**
	 * If the last point on this series has the same Y value we care about, then just update the X value to the new X.
	 * This basically extends a live plotting line that is maintaining the same value, without adding new marker points.
	 *
	 * @return true if there was a match, and the data was updated.  false means the Y value is different and the
	 *	caller needs to take other action, most likely adding a new point. 
	 */
	private boolean testAndReplaceLastPointIfYIsTheSame(String seriesID, double newXValue, double matchingYValue)
	{
		double[] y = chart.getSeriesSet().getSeries(seriesID).getYSeries();
		if (y.length < 2) {
			return false;
		}
		
		double lastYValue = y[y.length - 1];
		double secondLastYValue = y[y.length - 2];
		if (Calc.double_equals(lastYValue, matchingYValue) && Calc.double_equals(secondLastYValue, matchingYValue)) {
			// Overwrite the last X value with the new timestamp
			double[] x = chart.getSeriesSet().getSeries(seriesID).getXSeries();
			x[x.length - 1] = newXValue;	// x.length;
			chart.getSeriesSet().getSeries(seriesID).setXSeries(x);
			
			return true;
		}

		return false;	// did not replace
	}
	
	private class ResetYAxisListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			resetYChartAxis();
		}
	}
	private class ClearListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			if (chart != null) {
				// NOTE: we don't want to really delete the series object, as we lose the color/lineattribs etc
				//chart.getSeriesSet().deleteSeries(actualSeriesID);
				chart.getSeriesSet().getSeries(actualSeriesID).setXSeries(new double[0]);
				chart.getSeriesSet().getSeries(actualSeriesID).setYSeries(new double[0]);

				IAxis xAxis = chart.getAxisSet().getXAxes()[0];
				xAxis.adjustRange();						// NOTE: this does not cause a redraw

				chart.redraw();
				//				sendE4Command("com.elmoco.ss.rcp.graph.command.cleardata");		    		
			}
		}
	}

	private static String formatTargetValue(double targetValue)
	{
		return String.format("%.02f", targetValue);
	}

//	private class TargetUpListener extends SelectionAdapter
//	{
//		@Override
//		public void widgetSelected(SelectionEvent e)
//		{
//			targetValue += 0.2;
//			if (targetValue < 0) {
//				targetValue = 0.0;
//			}
//			targetValueText.setText(formatTargetValue(targetValue));
//		}
//	}
//	private class TargetDownListener extends SelectionAdapter
//	{
//		@Override
//		public void widgetSelected(SelectionEvent e)
//		{
//			targetValue -= 0.2;
//			if (targetValue < 0) {
//				targetValue = 0.0;
//			}
//			targetValueText.setText(formatTargetValue(targetValue));
//		}
//	}	

	private class MyShuttleListener implements ShuttleListener
	{
		@Override
		public void shuttleValueChanged(double shuttleValue)
		{
			targetValue = shuttleValue;
			targetValueText.setText(formatTargetValue(targetValue));
			sendTargetUpdate();
		}

		@Override
		public void shuttleCommand(String command) {
			// TODO Auto-generated method stub
			
		}
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

	private class MoveTargetListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent event)
		{
			try {
				targetValue = Double.parseDouble(targetValueText.getText());
				shuttle.setShuttleValue(targetValue);
				sendTargetUpdate();
			}
			catch (Exception e)
			{
				// parsing error;
			}
		}
	}

	private void sendTargetUpdate()
	{
		Request request = new Request(device, TableController.CMD_SET, targetValue, targetTime);
		controller.sendRequest(request);

		logger.trace("Send request {}", request.debugString());
		addDataPoint(targetSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), targetValue);
	}

	private Thread tableWatcherThread = null;
	private class GoListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// GO acts like a toggle, starting or stopping the watcher thread
			if (tableWatcherThread != null) {
				tableWatcherThread.interrupt();
				tableWatcherThread = null;

				deviceRunningStatus.setText("Stopped.");
				return;
			}
			deviceRunningStatus.setText("Running.");

			
			initChart();
			
			// Record the starting time... all X values will be in seconds after that time.
			chartStartTime = LocalTime.now();


			tableWatcherThread = new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					tableWatcherThread.setName("TableWatcher");

//					//			    	// DEBUG keep track of a fake value that we make wander up and down a little to make a realistic graph
//					//			    	double fakeValue = 2.00;
//
//					EthernetTableConnection connection = new EthernetTableConnection(serverAddress, serverPort);
//					//			    	SerialTableConnection connection = new SerialTableConnection();
//					TableController controller = new TableController(connection);	// autoconnects?
//
//					double oldTargetValue = -1;

					while (true) {

						try {
							Thread.sleep(Calc.rand(800, 1200));
						}
						catch (InterruptedException e) {
							logger.debug("feed watcher asked to quit");
							break;
						}
						
						if (controller == null || !controller.isConnected()) {
							continue;
						}

						// Plot the existing target value on the chart to extend the line.
						// TODO need a way to keep the line going without placing old points...
						// TODO like look at the last point on the data series, and if the same as us, just replace the X to now.

						if (!testAndReplaceLastPointIfYIsTheSame(targetSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), targetValue)) {
							addDataPoint(targetSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), targetValue);
						}

						// Sleep a little to offset the two series markers, otherwise they usually plot directly over each other.
						// TODO could solve this with some alpha on both series lines?
						try {
							Thread.sleep(Calc.rand(800, 1200));
						}
						catch (InterruptedException e) {
							logger.debug("feed watcher asked to quit");
							break;
						}

						// Read the latest value that the controller has... this doesn't block or query the table directly. 
						Response response = controller.getCurrentValue(device);
						if (response == null) {
							continue;	// impossible
						}

						// Sanity tests in case the controller is sending corrupt data
						double clippedValue = response.value;
						if (clippedValue < targetMin) {
							clippedValue = targetMin;
							logger.debug("BAD VALUE from table device {} value {}", response.device, response.value);
						}
						if (clippedValue > targetMax) {
							clippedValue = targetMax;
							logger.debug("BAD VALUE from table device {} value {}", response.device, response.value);
						}
						
						logger.debug("adding new data point for dev {} value {}", response.device, response.value);
						addDataPoint(actualSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), clippedValue);
						
						final double fake = clippedValue;
						sync.asyncExec(() -> { actualValueText.setText("" + fake); } );// TODO format
					}

					// This cleans up the underlying connection too (serial or UDP etc)
					if (controller != null && controller.isConnected()) {
						controller.disconnect();
					}
				}
			});
			tableWatcherThread.start();

		}
	}
}

