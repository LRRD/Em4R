package com.elmocity.elib.ui;

import java.time.DayOfWeek;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.IAxisSet;
import org.swtchart.IAxisTick;
import org.swtchart.ILineSeries;
import org.swtchart.ISeries;
import org.swtchart.ISeriesSet;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ext.InteractiveChart;

import com.elmocity.elib.chart.ChartX;
import com.elmocity.elib.chart.Normalize;
import com.elmocity.elib.swt.ColorCache;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.table.GenericRowList;
import com.elmocity.elib.swt.table.GenericTableInfoList;
import com.elmocity.elib.swt.table.GenericTableViewer;
import com.elmocity.elib.util.Format;

public class PartUtils
{
	private static final Logger logger = LoggerFactory.getLogger(PartUtils.class);

	// This assumes a parent with a 3 column grid layout.
	// First COL is created with controls and buttons for the user.
	// Second COL is a tableviewer with generic data in it
	// Third COL is a chart to plot series.
	
	public static GenericTableViewer makeTableComposite(Composite parent, GenericTableInfoList info, GenericRowList data)
	{
		GenericTableViewer viewer;
		
		// CENTER
		Composite tableComposite = GridHelpers.addGridPanel(parent, 1);
		{
			// Tell the tableComposite to fill up the vertical area only
			GridData gridData = new GridData(SWT.BEGINNING, SWT.FILL, false, true);
			tableComposite.setLayoutData(gridData);
	
			viewer = new GenericTableViewer(tableComposite, info, data);
		}
		return viewer;
	}

	public static Chart makeChartComposite(Composite parent)
	{
		Chart chart;
		
		// RIGHT
		Composite chartComposite = GridHelpers.addGridPanel(parent, 1);
		{
			// Tell the chartComp to fill up the rest of the screen
			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			chartComposite.setLayoutData(gridData);
	
			chart = new ChartX(chartComposite, SWT.BORDER);	// | SWT.H_SCROLL | SWT.V_SCROLL);
			GridData gridData2 = new GridData(SWT.FILL, SWT.FILL, true, true);
			chart.setLayoutData(gridData2);
			chart.setBackground(ColorCache.getColor(ColorCache.WHITE));
		}
		return chart;
	}
	
	
	// Clear all the data series off the chart
	public static class ClearListener extends SelectionAdapter
	{
		public ClearListener(Chart chart)
		{
			super();
			this.chart = chart;
		}

		Chart chart;
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// If we have 100 series, this can flicker if we allow it to repaint after each
			// delete of a single series.  So suspend the updates while we delete them all.
			chart.suspendUpdate(true);
			
			// Odd that there is no "deleteAllSeries()" method.
			ISeriesSet ss = chart.getSeriesSet();
			for (ISeries s : ss.getSeries()) {
				ss.deleteSeries(s.getId());
			}
			
			// Turn updating back on, which will cause a redraw() event
			chart.suspendUpdate(false);
		}
	}

	// Normalize all the data series on the chart
	public static class NormalizeListener extends SelectionAdapter
	{
		public NormalizeListener(Chart chart)
		{
			super();
			this.chart = chart;
		}

		Chart chart;
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// If we have 100 series, this can flicker while adjusting each series... so suspend the updates 
			chart.suspendUpdate(true);
			
			Normalize.normalizeAllSeries(chart);
			
			// Update the axis min and max, based on the actual data in all the series set right now
			chart.getAxisSet().adjustRange();

			// Turn updating back on, which will cause a redraw() event
			chart.suspendUpdate(false);
		}
	}
	
	// TODO send in the axis lables
	public static void skinChart(Chart chart, String chartTitle)
	{
		final String xAxisTitle = "Time (minutes)";
		final String yAxisTitle = "Combo Price (USD)";
		final Color textColor = ColorCache.getColor(ColorCache.BLACK);				// all titles, axis ticks, axis labels etc


		// Fix up the title area
		chart.getTitle().setText(chartTitle);
		chart.getTitle().setForeground(textColor);;

		// Fix up the axis
		IAxisSet axisSet = chart.getAxisSet();
		
		IAxis xAxis = axisSet.getXAxis(0);
		IAxisTick xTick = xAxis.getTick();
		xTick.setForeground(textColor);
		xTick.setTickMarkStepHint(75);			// Have to just eye-ball this to see if there are "too many" ticks

		IAxis yAxis = axisSet.getYAxis(0);
		IAxisTick yTick = yAxis.getTick();
		yTick.setForeground(textColor);
		xTick.setTickMarkStepHint(55);			// Have to just eye-ball this to see if there are "too many" ticks

		// Set the axis titles
		xAxis.getTitle().setText(xAxisTitle);
		xAxis.getTitle().setForeground(textColor);
		yAxis.getTitle().setText(yAxisTitle);
		yAxis.getTitle().setForeground(textColor);
	}
	
	public static void skinSeries(ILineSeries series, String seriesLegendName, DayOfWeek dotw)
	{
		// Color of the data points (line)
		final Color seriesLineColor = ColorCache.getColorByDOTW(dotw);
//		seriesLineColor = ColorCache.getRandomColor();

		// Create a new single series for desired
		series.setDescription(seriesLegendName);
		series.setLineColor(seriesLineColor);
		series.setLineWidth(2);			// thicker than default
		series.setSymbolSize(1);		// smaller than default
//		comboSeries.enableStep(true);
//		comboSeries.setLineStyle(LineStyle.DASH);
		series.setAntialias(SWT.ON);
	}
	
	public static void createSeries(Chart chart, String seriesID, String seriesLegendName, double[] xData, double[] yData, DayOfWeek dotw)
	{
		// Create a new series
		ILineSeries comboSeries = (ILineSeries) chart.getSeriesSet().createSeries(SeriesType.LINE, seriesID);
		
		// Add the data (the chart/series makes a copy when we set these, so we can disposed/reuse as needed afterwards without changing the chart display
		comboSeries.setXSeries(xData);
		comboSeries.setYSeries(yData);
	
		// Update the axis min and max, based on the actual data in all the series set right now
		chart.getAxisSet().adjustRange();
		
		PartUtils.skinSeries(comboSeries, seriesLegendName, dotw);
		
		chart.redraw();
	}
}
