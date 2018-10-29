package com.emriver.geomodel.table.parts;

import java.awt.Font;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
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
import com.elmocity.elib.swt.ShuttleComposite;
import com.elmocity.elib.swt.VerticalLabelComposite;
import com.elmocity.elib.swt.ShuttleComposite.ShuttleListener;
import com.elmocity.elib.ui.PartUtils;
import com.elmocity.elib.util.Calc;
import com.emriver.geomodel.table.Device;
import com.emriver.geomodel.table.Request;
import com.emriver.geomodel.table.Response;
import com.emriver.geomodel.table.TableController;

public class TableDevicePart
{
	// Need to be injected from parent or master part above us.
//	EthernetTableConnection connection;
	TableController controller;

	// Device was are manipulating
	Device device = Device.DEV_PITCH;

	// Target
	Text targetValueText;
	volatile double targetValue = 0.00;

	ShuttleComposite shuttle;
	
	// Actual
	Text actualValueText;
	
	
	// Debug
	Text targetValueDebugText;
	volatile double targetValueDebug = 0.00;

	Text targetTimeDebugText;
	volatile int targetTimeDebug = 0;

	Button debugApplyButton;
	
	
	// Chart
	Chart chart;
	LocalTime chartStartTime;		// X axis time on the chart is relative to a start time, not wall clock time
	private volatile boolean isChartRunning = false;
	
	
	private static final Logger logger = LoggerFactory.getLogger(TableDevicePart.class);

	private double targetMin = device.getMin();
	private double targetMax = device.getMax();
	private double targetStep = device.getStep();


	@Inject
	UISynchronize sync;

	@PostConstruct
	public void postConstruct(Composite parent, MApplication app, MPart part)
	{
		// If we construct several of this same part via the XML/fragment E4 mechanism, we have to send in
		// a param that tells us which device we should default to monitoring.
		// Alternatively, we could make this a baseclass, and make 5 or so derived classes that just had one line
		// of code to set the device.  That would let XML/fragments pick the one by classname instead of param
		//
		// Example of reading params from the XML:
		//
		// We use a custom "variable" on the "supplementary" tab of the application.e4xml file to tell use what device each part should
		// initially load itself with.  The user can edit it later if needed.   The variable is just a string and looks like "DEVICE=TILTX".
		List<String> vars = part.getVariables();
		for (int i = 0; i < vars.size(); i++) {
			if (vars.get(i).startsWith("DEVICE=")) {
				String devString = vars.get(i).substring(7);
				if (devString != null && devString.length() > 0) {
					logger.debug("part found DEVICE= context variable. using value {}", devString);
					try {
						device = Device.valueOf(devString);
					}
					catch (Exception e) {
						// Can get IllegalArgumentException I think
					}
				}
			}
		}

		// Should use injection and an OSGI service to provide our controller, but temp hack we are just publishing the controller object into
		// the application context so any part can use it.
		final String globalControllerID = "com.emriver.geomodel.globalcontroller";
		IEclipseContext context = app.getContext();
		controller = (TableController)context.get(globalControllerID);
		if (controller == null) {
			// Very bad.
			logger.warn("PANIC - device part could not find table controller in the application context");
			return;
		}
		
		Display.getCurrent().getActiveShell().setBackgroundMode(SWT.INHERIT_FORCE);
		
		// 2 columns, left is input parameters and right is chart
		Composite c = GridHelpers.addGridPanel(parent, 2);
		
		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 3);
		{
			// Have inputComposite stick to the top of the screen, instead of centering/filling vertically.
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);

			// TARGET GROUP
			{
				Composite targetGroup = new Composite(inputComposite, SWT.NONE);
//				targetGroup.setText("Target");

				gridData = new GridData();
				gridData.verticalAlignment  = GridData.FILL;
				targetGroup.setLayoutData(gridData);

				GridLayout targetLayout = new GridLayout(4, false);
				targetGroup.setLayout(targetLayout);

				// 4 horz by 3 vert grid
				
				VerticalLabelComposite deviceLabel = new VerticalLabelComposite(targetGroup, SWT.NONE);
//				Label deviceLabel = new Label(targetGroup, SWT.NONE);
				deviceLabel.setText(device.toString());
				gridData = new GridData();
				gridData.horizontalAlignment = SWT.FILL;
				gridData.verticalAlignment = SWT.FILL;
				gridData.verticalSpan = 3;
				deviceLabel.setLayoutData(gridData);
				deviceLabel.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_BLUE));
				deviceLabel.setForeground(ColorCache.getColor(ColorCache.WHITE));
				
				Button autoButton = new Button(targetGroup, SWT.TOGGLE);
				autoButton.setText("Auto-script");
				gridData = new GridData();
				gridData.horizontalSpan = 3;
				gridData.horizontalAlignment = SWT.FILL;
				autoButton.setLayoutData(gridData);
			
				targetValueText = new Text(targetGroup, SWT.CENTER);
				targetValueText.setText(formatValue(device, targetValue));
				gridData = new GridData();
				gridData.horizontalAlignment = SWT.FILL;
				gridData.horizontalSpan = 3;
				gridData.widthHint = 200;
				targetValueText.setLayoutData(gridData);
				
				targetValueText.setEditable(false);	// This only greys down the background.
				targetValueText.setFont(FontCache.getFont(FontCache.EMRIVER_HUGE_TARGET, Font.BOLD));
				targetValueText.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GREEN));
//				targetValueText.setBackground(ColorCache.getColor(ColorCache.WHITE));
				targetValueText.setEditable(false);	// This only greys down the background.
				
				shuttle = new ShuttleComposite(targetGroup, SWT.NONE, 3);		// 3 col span
				shuttle.addShuttleListener(new MyShuttleListener());
				shuttle.setShuttleParams(targetMin, targetMax, targetStep);
				shuttle.setShuttleValue(targetValue);

			}

			// ACTUAL GROUP
			{
				Composite actualGroup = new Composite(inputComposite, SWT.NONE);
//				actualGroup.setText("Actual");

				GridLayout actualLayout = new GridLayout(1, false);
				actualGroup.setLayout(actualLayout);

				gridData = new GridData();
				gridData.verticalAlignment  = GridData.FILL;
//				gridData.widthHint = 180;
				actualGroup.setLayoutData(gridData);

				actualValueText = new Text(actualGroup, SWT.CENTER);
				actualValueText.setText("9999");
				
				// Make it big and scary
//				actualValueText.setEnabled(false);	// Unfortunately, this greys down the text and the background too
				actualValueText.setEditable(false);	// This only greys down the background.
				actualValueText.setFont(FontCache.getFont(FontCache.EMRIVER_HUGE_ACTUAL, Font.BOLD));
				actualValueText.setForeground(ColorCache.getColor(ColorCache.MONDAY));
//				actualValueText.setBackground(ColorCache.getColor(ColorCache.WHITE));
				actualValueText.setEditable(false);	// This only greys down the background.

				Label actualUnitLabel = new Label(actualGroup, SWT.CENTER);
				actualUnitLabel.setText(device.getUnit());
				gridData = new GridData(SWT.FILL, SWT.TOP, false, false);
				actualUnitLabel.setLayoutData(gridData);
				actualUnitLabel.setFont(FontCache.getFont(FontCache.WU_TITLE, Font.PLAIN));

			}
			
//			// DEBUG GROUP
//			{
//				Composite debugGroup = new Composite(inputComposite, SWT.NONE);
//						//				debugGroup.setText("Debug");
//				
//				GridLayout actualLayout = new GridLayout(2, false);
//				debugGroup.setLayout(actualLayout);
//
//				Label l = new Label(debugGroup, SWT.NONE);
//				l.setText("Value:");
//
//				targetValueDebugText = new Text(debugGroup, SWT.BORDER);
//				targetValueDebugText.setText(formatValue(device, targetValueDebug));
//				gridData = new GridData();
//				gridData.widthHint = 100;
//				targetValueDebugText.setLayoutData(gridData);
//
//				Label l2 = new Label(debugGroup, SWT.NONE);
//				l2.setText("Time (sec):");
//
//				targetTimeDebugText = new Text(debugGroup, SWT.BORDER);
//				targetTimeDebugText.setText("" + targetTimeDebug);	// just an int (seconds)
//				gridData = new GridData();
//				gridData.widthHint = 100;
//				targetTimeDebugText.setLayoutData(gridData);
//
//				debugApplyButton = new Button(debugGroup, SWT.PUSH);
//				debugApplyButton.setText("APPLY");
//				debugApplyButton.addSelectionListener(new MoveTargetListener());
//				Label lx = new Label(debugGroup, SWT.NONE);
//				lx.setText(" ");
//			}
		}

		// RIGHT
		{
			Composite graphGroup = new Composite(c, SWT.NONE);
//			graphGroup.setText("Graph");

			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, false);
			graphGroup.setLayoutData(gridData);

			GridLayout graphLayout = new GridLayout(1, false);
			graphGroup.setLayout(graphLayout);

			chart = PartUtils.makeChartComposite(graphGroup);

//			Button clearButton = new Button(graphGroup, SWT.PUSH);
//			clearButton.setText("Clear Data");
//			clearButton.addSelectionListener(new ClearListener());
//
//			Button resetYAxisButton = new Button(graphGroup, SWT.PUSH);
//			resetYAxisButton.setText("Reset Y Axis");
//			resetYAxisButton.addSelectionListener(new ResetYAxisListener());

			// Fix up all the shuttles and min/max depending on the device that was supplied
//			initChart();
			deviceChanged();
		}

		initWatcherThread();
		isChartRunning = true;

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
		// Fix the value fields
		updateTargetValue(0.00);
		
		// Fix the shuttle
		targetMin = device.getMin();
		targetMax = device.getMax();
		targetStep = device.getStep();
		shuttle.setShuttleParams(targetMin, targetMax, targetStep);
		shuttle.setShuttleValue(targetValue);
		
		// Fix the chart
//		// Remove the old series from the chart before we fix up the series names
//		chart.getSeriesSet().deleteSeries(targetSeriesID);
//		chart.getSeriesSet().deleteSeries(actualSeriesID);

		initChart();
	}

	/**
	 * HACK not sure what part is handling what device, so this call can come from the script running to test
	 * 
	 */
	public void updateTargetValue(Device device, double newTargetValue)
	{
		if (device == this.device) {
			updateTargetValue(newTargetValue);
		}
	}
	/**
	 * Update the targetValue to something new.  This can happen from a GUI control, or a script etc.  This does not send an update request to the table.
	 */
	void updateTargetValue(double newTargetValue)
	{
		targetValue = newTargetValue;
		shuttle.setShuttleValue(targetValue);
		
		sync.asyncExec(() -> {
			targetValueText.setText(formatValue(device, targetValue));
		});
	}

	/**
	 * Using the live targetValue variable, send a wire request to the controller to change the table.
	 */
	void sendTargetValueRequest()
	{
		// GUI controls always tell it to move immediately (time = 0) - NOTE could add extra GUI controls
		final int timeToTake = 0;	// seconds
		Request request = new Request(device, TableController.CMD_SET, targetValue, timeToTake);
		controller.sendRequest(request);

		logger.trace("Send request {}", request.debugString());
		if (isChartRunning) {
			addDataPoint(targetSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), targetValue);
		}
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
		
		chart.getLegend().setVisible(false);

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
		final Color targetLineColor = ColorCache.getColor(ColorCache.TUESDAY);		// data points
		final Color actualLineColor = ColorCache.getColor(ColorCache.MONDAY);		// data points


		chart.getTitle().setText(chartTitle);
		chart.getTitle().setForeground(textColor);
		chart.getTitle().setVisible(false);				// hide it


		// Get the container for all the data series
		ISeriesSet seriesSet = chart.getSeriesSet();

		// Create a new single series for target
		ILineSeries targetSeries = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, targetSeriesID);
		targetSeries.setDescription(targetSeriesDisplayName);
		targetSeries.enableStep(true);
		targetSeries.setLineColor(targetLineColor);
		targetSeries.setLineStyle(LineStyle.DASH);
		targetSeries.setLineWidth(targetSeries.getLineWidth() + 4);
		targetSeries.setSymbolColor(targetLineColor);
		targetSeries.setSymbolSize(targetSeries.getSymbolSize() + 3);
		targetSeries.setSymbolType(PlotSymbolType.NONE);
		targetSeries.setAntialias(SWT.ON);

		// Create a new single series for actual
		ILineSeries actualSeries = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, actualSeriesID);
		actualSeries.setDescription(actualSeriesDisplayName);
		actualSeries.setLineColor(actualLineColor);
		actualSeries.setLineWidth(actualSeries.getLineWidth() + 4);
		actualSeries.setSymbolColor(actualLineColor);
		actualSeries.setSymbolSize(actualSeries.getSymbolSize() + 3);
		actualSeries.setSymbolType(PlotSymbolType.NONE);
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
//		xAxis.getTitle().setText(xAxisTitle);
//		xAxis.getTitle().setForeground(textColor);
		xAxis.getTitle().setVisible(false);				// hide it

		yAxis.getTitle().setText(yAxisTitle);
		yAxis.getTitle().setForeground(textColor);

		resetYChartAxis();
		
		chart.redraw();		
	}
	
	private void resetYChartAxis()
	{
		if (chart != null) {
			// Instead of updating the Y axis from the range of real data, we can alternatively force a fixed range.
			final double slop = (targetMax - targetMin) * 0.05;	// 5% of range?
			IAxisSet axisSet = chart.getAxisSet();
			axisSet.getYAxes()[0].setRange(new Range(targetMin - slop, targetMax + slop));
			chart.redraw();		
		}
	}

	private final int maxPointsToKeep = 5 * 60;		// TODO CEA   these are in 1 second points.

	void addDataPoint(String seriesID, double xValue, double yValue)
	{
		// HACK, literally extending the X and Y data arrays by one at a time, barf, by resizing and copying.
		double[] x = chart.getSeriesSet().getSeries(seriesID).getXSeries();
		if (x.length == maxPointsToKeep) {
			x = Arrays.copyOfRange(x, 1, x.length + 1);		// Copy starting at element 1 (instead of 0) and "walk off the end" by 1 element
		}
		else {
			x = Arrays.copyOf(x, x.length + 1);
		}
		x[x.length - 1] = xValue;	// x.length;
		chart.getSeriesSet().getSeries(seriesID).setXSeries(x);

		double[] y = chart.getSeriesSet().getSeries(seriesID).getYSeries();
		if (y.length == maxPointsToKeep) {
			y = Arrays.copyOfRange(y, 1, y.length + 1);		// Copy starting at element 1 (instead of 0) and "walk off the end" by 1 element
		}
		else {
			y = Arrays.copyOf(y, y.length + 1);
		}
		y[y.length - 1] = yValue;	// Calc.rand(20, 90) / 10.00;
		chart.getSeriesSet().getSeries(seriesID).setYSeries(y);

		sync.asyncExec( () -> {
			if (chart.isDisposed()) {
				return;
			}
			
		
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
			defaultXRange.lower = Math.max(-1.0, defaultXRange.upper - maxPointsToKeep);
//			defaultXRange.lower = -1.0;
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
	
//	private class ResetYAxisListener extends SelectionAdapter
//	{
//		@Override
//		public void widgetSelected(SelectionEvent e)
//		{
//			resetYChartAxis();
//		}
//	}
//	private class ClearListener extends SelectionAdapter
//	{
//		@Override
//		public void widgetSelected(SelectionEvent e)
//		{
//			if (chart != null) {
//				// NOTE: we don't want to really delete the series object, as we lose the color/lineattribs etc
//				//chart.getSeriesSet().deleteSeries(actualSeriesID);
//				chart.getSeriesSet().getSeries(actualSeriesID).setXSeries(new double[0]);
//				chart.getSeriesSet().getSeries(actualSeriesID).setYSeries(new double[0]);
//
//				IAxis xAxis = chart.getAxisSet().getXAxes()[0];
//				xAxis.adjustRange();						// NOTE: this does not cause a redraw
//
//				chart.redraw();
//				//				sendE4Command("com.elmoco.ss.rcp.graph.command.cleardata");		    		
//			}
//		}
//	}

//	private static String formatTargetValue(double targetValue)
//	{
//		return String.format("%.02f", targetValue);
//	}
//

	private class MyShuttleListener implements ShuttleListener
	{
		@Override
		public void shuttleValueChanged(double shuttleValue)
		{
			updateTargetValue(shuttleValue);
//			targetValue = shuttleValue;
//			targetValueText.setText(formatValue(device, targetValue));
			sendTargetValueRequest();
		}

		@Override
		public void shuttleCommand(String command) {
			if (command.equals("stop")) {
				logger.debug("got shuttle command to stop device {}", device.name());

				Request request = new Request(device, TableController.CMD_STOP, 0, 0);
				controller.sendRequest(request);
			}
		}
	}


//	private class MoveTargetListener extends SelectionAdapter
//	{
//		@Override
//		public void widgetSelected(SelectionEvent event)
//		{
//			try {
//				targetValue = Double.parseDouble(targetValueText.getText());
//				shuttle.setShuttleValue(targetValue);
//				sendTargetUpdate();
//			}
//			catch (Exception e)
//			{
//				// parsing error;
//			}
//		}
//	}


	/**
	 * Clear any data from the chart and start recording values.
	 * <p>
	 * The chart may be running or not when called.
	 */
	public void restartChart()
	{
		// Remove the old series from the chart before we fix up the series names
		chart.getSeriesSet().deleteSeries(targetSeriesID);
		chart.getSeriesSet().deleteSeries(actualSeriesID);

		// Recreate the series
		initChart();
		
		// Tell the working to start pumping data
		chartStartTime = LocalTime.now();				// reset the zero time point to now
		isChartRunning = true;
	}
	
	/**
	 * Leave whatever data is on the chart at this point, but stop adding any more data points.
	 */
	public void stopChart()
	{
		isChartRunning = false;
	}
	
	// Hack DEBUG just to tell which of the 5 device parts is which remotely
	public Device getDevice()
	{
		return device;
	}
	
	private Thread tableWatcherThread = null;
	private void initWatcherThread()
	{
		if (tableWatcherThread != null) {
			logger.warn("multiple calls to initWatcherThread() not allowed.");
			return;
		}
		
		
//		initChart();
//		
		// Record the starting time... all X values will be in seconds after that time.
		chartStartTime = LocalTime.now();

		tableWatcherThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				tableWatcherThread.setName("Watcher" + device.name());

				final int msAverageSleepCycle = 250;
				final int chartMultiplier = 4;
				
				long counter = 0;
				while (true) {

					counter++;
					
					try {
						Thread.sleep(msAverageSleepCycle);
					}
					catch (InterruptedException e) {
						logger.debug("feed watcher asked to quit");
						break;
					}
					
					// At startup or shutdown, we might get in a situation where our controller is broken.
					// Don't bail out, since the main app might get it connected() again.
					if (controller == null || !controller.isConnected()) {
						continue;
					}

					// Plot the existing target value on the chart to extend the line.
					// TODO need a way to keep the line going without placing old points...
					// TODO like look at the last point on the data series, and if the same as us, just replace the X to now.

					if (isChartRunning && (counter % chartMultiplier == 0)) {
//						if (!testAndReplaceLastPointIfYIsTheSame(targetSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), targetValue)) {
							addDataPoint(targetSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), targetValue);
//						}
					}

//					// Sleep a little to offset the two series markers, otherwise they usually plot directly over each other.
//					// TODO could solve this with some alpha on both series lines?
//					try {
//						Thread.sleep(Calc.rand(400, 600));
//					}
//					catch (InterruptedException e) {
//						logger.debug("feed watcher asked to quit");
//						break;
//					}

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
					
//					logger.debug("adding new data point for dev {} value {}", response.device, response.value);
					if (isChartRunning && (counter % chartMultiplier == 0)) {
						addDataPoint(actualSeriesID, ChronoUnit.SECONDS.between(chartStartTime, LocalTime.now()), clippedValue);
					}
					
					final String formatted = formatValue(device, clippedValue);
					sync.asyncExec(() -> {
						if (actualValueText != null && !actualValueText.isDisposed()) {
							actualValueText.setText(formatted);
						}
					});
				}

				// This cleans up the underlying connection too (serial or UDP etc)
				if (controller != null && controller.isConnected()) {
					controller.disconnect();
				}
			}
		});
		tableWatcherThread.start();
	}
	
	/**
	 * Knowing the device, we generate a string to display, integer or 1 decimal point depending on units
	 */
	private static String formatValue(Device device, double value)
	{
		final String result;
		switch (device) {
		case DEV_PITCH:
		case DEV_ROLL:
			// One digit floating points values
			result = String.format("%.1f", value);
			break;
		default:
			// Integer values
			result = String.format("%d", Math.round(value));
			break;
		}
		return result;
	}
}

