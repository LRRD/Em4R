package com.elmocity.elib.chart;

import org.swtchart.Chart;
import org.swtchart.ISeries;
import org.swtchart.ISeriesSet;

import com.elmocity.elib.util.Calc;

public class Normalize
{
	/*
	 *  Change the actual data in every series so that they all start at Y coord zero.  The X data is unaffected.
	 *  <p>
	 *  This is useful to show many series at once that are all relative (like $ profit)
	 */
	public static void normalizeAllSeries(Chart chart)
	{
		// Walk each series individually, they do not interact with each other.
		ISeriesSet seriesSet = chart.getSeriesSet();
		for (ISeries series : seriesSet.getSeries()) {
			// Sanity tests
			if (series.getXSeries() == null || series.getXSeries().length <= 0) {
				continue;
			}
			if (series.getYSeries() == null || series.getYSeries().length <= 0) {
				continue;
			}
			
			// Get info about the old Y array
			final double oldYValues[] = series.getYSeries();
			final int oldYLength = oldYValues.length;
			final double initialYValue = oldYValues[0];
			
			// No need to copy all the data if the offset adjustment is going to be zero anyway.
			// This might happen if someone normalizes all the data, adds one more series, and then calls here again.
			if (!Calc.double_equals(initialYValue, 0.00)) {
				// Allocate new data
				double y[] = new double[oldYLength];
				for (int i = 0; i < oldYLength; i++) {
					y[i] = oldYValues[i] - initialYValue;
				}
				
				series.setYSeries(y);
			}
		}
	}
}
