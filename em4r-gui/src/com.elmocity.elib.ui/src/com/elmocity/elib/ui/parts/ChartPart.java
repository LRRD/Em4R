
package com.elmocity.elib.ui.parts;

import java.util.Arrays;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.IAxisSet;
import org.swtchart.IAxisTick;
import org.swtchart.ILineSeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ISeriesSet;
import org.swtchart.LineStyle;
import org.swtchart.Range;
import org.swtchart.ext.InteractiveChart;

import com.elmocity.elib.swt.ColorCache;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.SimpleConsole;
import com.elmocity.elib.ui.PartUtils;
import com.elmocity.elib.ui.parts.MainPart.ChartListener;
import com.elmocity.elib.ui.parts.MainPart.LoggingListener;
import com.elmocity.elib.ui.parts.MainPart.SWTListener;
import com.elmocity.elib.ui.parts.MainPart.UtilListener;
import com.elmocity.elib.util.Calc;

public class ChartPart
{
	private static final Logger logger = LoggerFactory.getLogger(ChartPart.class);

	@Inject
	UISynchronize sync;
	
	@Inject
	private IEventBroker eventBroker; 
	private static final String STATUSBAR ="statusbar";

	// ------------------------
	
	Button configureButton;
	Button fixedYRangeButton;
	Text delayText;
	Button progressButton;
	Button zoomInButton;
	Button zoomOutButton;

	SimpleConsole console;

	Chart chart;
	

	
	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters and mini-console, and right is chart
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "ELib Chart", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
				
			configureButton = GridHelpers.addButtonToGrid(inputComposite, "Chart Configure");
			configureButton.addSelectionListener(new TestConfigureListener());

			fixedYRangeButton = GridHelpers.addButtonToGrid(inputComposite, "Force Y Range");
			fixedYRangeButton.addSelectionListener(new ForceYRangeListener());

			delayText = GridHelpers.addEditorToGrid(inputComposite, "Delay (ms)", "1000");
			
			progressButton = GridHelpers.addButtonToGrid(inputComposite, "Add Live Data");
			progressButton.addSelectionListener(new TestProgressListener());

			zoomInButton = GridHelpers.addButtonToGrid(inputComposite, "Zoom In");
			zoomInButton.addSelectionListener(new ZoomInListener());

			zoomOutButton = GridHelpers.addButtonToGrid(inputComposite, "Zoom Out");
			zoomOutButton.addSelectionListener(new ZoomOutListener());


			// Console jammed below the buttons instead of its own area.
			// Need a shim composite to span the 2 columns used by buttons/labels.
			Composite consoleComposite = new Composite(inputComposite, SWT.NONE);
			gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			gridData.horizontalSpan = 2;
			consoleComposite.setLayoutData(gridData);
			consoleComposite.setLayout(new GridLayout(1, false));

			console = new SimpleConsole(consoleComposite, SWT.NONE, 20);
		}

		// RIGHT
		chart = PartUtils.makeChartComposite(c);
		
//		Composite chartComposite = GridHelpers.addGridPanel(c, 1);
//		{
//			// Right side is a console text area, since the lines of text are short, but numerous
//			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
//			consoleComposite.setLayoutData(gridData);
//
//			console = new SimpleConsole(consoleComposite, SWT.NONE);
//		}
//		
		
//		// Stack the major GUI components vertically
//		parent.setLayout(new GridLayout(1, false));
//		
//		// Fixed height text output area for messages
//		console = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
//		GridData gd = new GridData(SWT.FILL, SWT.NONE, true, false);
//		gd.heightHint = 6 * console.getLineHeight();		
//		console.setLayoutData(gd);
//
//		console.setText("Idle\n");
//
//		// Single horizonal row of buttons to start tests
//		Composite buttonRow = new Composite(parent, SWT.NONE);
//		{
//			RowLayout rowLayout = new RowLayout();
//			rowLayout.pack = false;				// make all buttons the same size as the largest button (longest text)
//			rowLayout.spacing = 12;				// add a little more space between the buttons (horizontally)
//			buttonRow.setLayout(rowLayout);
//			
//			configureButton = new Button(buttonRow, SWT.PUSH);
//			configureButton.setText("Test Chart Configure");
//			configureButton.addSelectionListener(new TestConfigureListener());
//
//			fixedYRangeButton = new Button(buttonRow, SWT.PUSH);
//			fixedYRangeButton.setText("Force Y Range");
//			fixedYRangeButton.addSelectionListener(new ForceYRangeListener());
//
//			progressButton = new Button(buttonRow, SWT.PUSH);
//			progressButton.setText("Add Live Data");
//			progressButton.addSelectionListener(new TestProgressListener());
//
//			zoomInButton = new Button(buttonRow, SWT.PUSH);
//			zoomInButton.setText("Zoom In");
//			zoomInButton.addSelectionListener(new ZoomInListener());
//
//			zoomOutButton = new Button(buttonRow, SWT.PUSH);
//			zoomOutButton.setText("Zoom Out");
//			zoomOutButton.addSelectionListener(new ZoomOutListener());
//		}
//
//		// Add plugin specific GUI
//		chart = new InteractiveChart(parent, SWT.BORDER);	// | SWT.H_SCROLL | SWT.V_SCROLL);
//		GridData gd2 = new GridData(SWT.FILL, SWT.FILL, true, true);
//		chart.setLayoutData(gd2);
//		chart.setBackground(ColorCache.getColor(ColorCache.WHITE));
		
//		// Hook up the logger to our Text control
//		LogbackWidgetAppender.setAppenderWidget(console);
	}

	@PreDestroy
	public void preDestroy() {
	}

	@Focus
	public void onFocus()
	{
		console.setFocus();
	}

	// ----------------------------------------------------------------------------------------------------
	
	class TestConfigureListener extends SelectionAdapter
	{
		// Configure the chart to something like it might look for the em river table
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// TODO user inputs
			final String deviceID = "tiltx_dev";			// string or int id used to talk to the EMRiverController for one device we are tracking
			final String deviceDisplayName = "Tilt X";		// human display name for this device
			
			final String desiredSeriesID = deviceID + "_desired";
			final String desiredSeriesDisplayName = "Desired";
			final String actualSeriesID = deviceID + "_actual";
			final String actualSeriesDisplayName = "Actual";
			
			final String xAxisTitle = "Time (seconds)";
			final String yAxisTitle = "Slope (degrees)";
			
			final Color textColor = ColorCache.getColor(ColorCache.BLACK);			// all titles, axis ticks, axis labels etc
			final Color desiredLineColor = ColorCache.getColor(ColorCache.MONDAY);		// data points
			final Color actualLineColor = ColorCache.getColor(ColorCache.TUESDAY);		// data points
			

			console.append("");
			
			chart.getTitle().setText(deviceDisplayName);
			chart.getTitle().setForeground(textColor);;

			console.append("Setting fake data...");

			// Get the container for all the data series
			ISeriesSet seriesSet = chart.getSeriesSet();
			
			// Create a new single series for desired
			ILineSeries desiredSeries = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, desiredSeriesID);
			desiredSeries.setDescription(desiredSeriesDisplayName);
			desiredSeries.setLineColor(desiredLineColor);
			desiredSeries.enableStep(true);
			desiredSeries.setLineStyle(LineStyle.DASH);
			
			// Add some fake data
			desiredSeries.setXSeries(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
			desiredSeries.setYSeries(new double[] { 3, 3, 3, 4, 4, 4, 4, 5, 5, 5});

			// Create a new single series for actual
			ILineSeries actualSeries = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, actualSeriesID);
			actualSeries.setDescription(actualSeriesDisplayName);
			actualSeries.setLineColor(actualLineColor);
			actualSeries.setLineWidth(actualSeries.getLineWidth() + 2);
			
			// Add some fake data
			actualSeries.setXSeries(new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
			actualSeries.setYSeries(new double[] { 3, 3, 3, 3, 3, 4, 4, 4, 5, 5});

			
			console.append("Setting axes...");

			// Get the container for all the axis data
			IAxisSet axisSet = chart.getAxisSet();
			
			// Update the axis min and max, based on the actual data in all the series set right now
			axisSet.adjustRange();
			
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
	
			console.append("Done.");
			
			chart.redraw();
		}
	}

	class ForceYRangeListener extends SelectionAdapter
	{
		// Configure the chart to something like it might look for the em river table
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			
			console.append("Force Y to be from 0 to 10 scale...");

			// Get the container for all the axis data
			IAxisSet axisSet = chart.getAxisSet();
			
			// Update the axis min and max, based on the actual data in all the series set right now
			axisSet.getYAxes()[0].setRange(new Range(0.00, 10.00));
			
			console.append("Done.");
			
			chart.redraw();
		}
	}
	

	// Run a long task and show the status on the Progress Bar of the main app Status Bar.
	// Also acts as a basic test of the E4 Job mechanism.
	private static boolean progressTestRunning = false;

	class TestProgressListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");

			final int thingCount = 10;
			final int msThingDelay = Integer.parseInt(delayText.getText());
			
			// Since we start a job, we should disable our button so that the user cannot spam us... we are not reentrant.
			if (progressTestRunning) {
				console.append("Already running... ignoring new request.");
				return;
			}
			progressTestRunning = true;
			
			console.append("Scheduling job...");

			// The button handler just creates the job and sets it free.
			// TODO could store job and implement a cancel mechanism.
			Job job = new Job("ChartAppendData")
			{
				@Override
				protected IStatus run(IProgressMonitor monitor)
				{
					doLongThing(monitor, thingCount, msThingDelay);

					sync.asyncExec( () -> console.append("Done.") );

					progressTestRunning = false;
					return Status.OK_STATUS;
				}

			};
			job.setUser(true);
			job.schedule();
			logger.debug("scheduled job to test progress bar and eventBroker");
			
			console.append("Job free running, see progress bar...");
		}

		// Our example work method that gets run in the Job pool on some random worker thread.
		private void doLongThing(IProgressMonitor monitor, int thingCount, int msThingDelay)
		{
			// Example of 10 parts of the overall task, like copying 10 files maybe
			final int tasks = thingCount;
			
			// Update the text widget on the status bar to a message that we are running.
			eventBroker.send(STATUSBAR, "Starting " + tasks + " part work job...");

			// Convert into submonitor
			SubMonitor subMonitor = SubMonitor.convert(monitor, tasks);
			subMonitor.setTaskName("" + tasks + " tasks");

			// Do the tasks, one at a time, this could even spawn more threads etc
			for (int task = 0; task < tasks; task++) {
				// Break off one task and give it to the worker method
				doOneTask(subMonitor.split(1), task + 1, msThingDelay);
			}
			
			// When done, the status bar never gets to 100%, since it has an "extra" check at the end to be finished.
			// So this call "uses up" the final tick by breaking off another (might be correct to call done() instead)
			// TODO not sure what the preferred way to "finish" the monitor.  could be monitor.done, setworkremaining(0) etc
			subMonitor.split(1);
//			subMonitor.done();
//			subMonitor.setWorkRemaining(0);
			
			// Tell the text widget on the status bar that we are all done.
			eventBroker.send(STATUSBAR, "Done.");
//			monitor.done();
		}
		
		private void doOneTask(SubMonitor subMonitor, int ID, int msThingDelay)
		{
			final String taskName = "task" + ID;
			subMonitor.setTaskName(taskName);
			
			// We simulate a long running operation here
			try {
				final String deviceID = "tiltx_dev";
				final String actualSeriesID = deviceID + "_actual";

				// HACK, literally extending the X and Y data arrays by one at a time, barf, by resizing and copying.
				double[] x = chart.getSeriesSet().getSeries(actualSeriesID).getXSeries();
				x = Arrays.copyOf(x, x.length + 1);
				x[x.length - 1] = x.length;
				chart.getSeriesSet().getSeries(actualSeriesID).setXSeries(x);
				
				double[] y = chart.getSeriesSet().getSeries(actualSeriesID).getYSeries();
				y = Arrays.copyOf(y, y.length + 1);
				y[y.length - 1] = Calc.rand(20, 90) / 10.00;
				chart.getSeriesSet().getSeries(actualSeriesID).setYSeries(y);
				
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
//					logger.trace("{} {}", defaultXRange.lower, defaultXRange.upper);
					
					// We have to force the replot ourselves, which is good that we can do several changes then just one replot.
					chart.redraw();
					}
				);
				
				Thread.sleep(msThingDelay);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			// Complete
			
			System.out.println("Finished " + taskName);
		}
	}
	
	// ----------------------------------------------------------------------------------------------------
	
	class ZoomInListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			chart.getAxisSet().zoomIn();
			chart.redraw();
		}
	}
	class ZoomOutListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			chart.getAxisSet().zoomOut();
			chart.redraw();
		}
	}

}