package com.elmocity.elib.chart;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.swtchart.*;
import org.swtchart.ext.InteractiveChart;

public class ChartX extends InteractiveChart
{
	public ChartX(Composite parent, int style)
	{
		super(parent, style);

		// TODO it would be nice just to implement PaintListener on this extended class... but the base class already
		// implements that.  Not sure how the overrides work at that point, but a first attempt failed to get the events.
		// So we are just using an internal class to house the listener and everything seems to be working.
		Composite plotArea = this.getPlotArea();
		plotArea.addPaintListener(new ExtendedPaintListener());
	}

	class ExtendedPaintListener implements PaintListener
	{
		@Override
		public void paintControl(PaintEvent e)
		{
			GC gc = e.gc;
			Rectangle rBounds = gc.getClipping();

			{
				// Get the Y pixel value for the zero line (of the first Y axis)
				IAxisSet axisSet = getAxisSet();
				IAxis[] yAxes = axisSet.getYAxes();
				IAxis yAxis = yAxes[0];
				int yZeroPixel = yAxis.getPixelCoordinate(0.00);

				// Add a thicker grid line at the zero marks, since thats useful for most charts
				
//				// Make sure this is even onscreen
//				if (isYPixelInClipping(gc, yZeroPixel)) {
//					gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
//					gc.setAlpha(128);
//			
//					int x = rBounds.x;
//					int y = yZeroPixel - 1;		// 3 pixels tall... so -1, 0, and +1 from the actual zero line
//			
//					int w = rBounds.x + rBounds.width;
//					int h = 3;
//			
//					gc.fillRectangle(x, y, w, h);
//				}
			}
		}
	}

	private boolean isYPixelInClipping(GC gc, int yPixel)
	{
		// Make sure this is even in the clipping range - often only displayed on Normalized data
		Rectangle rBounds = gc.getClipping();
		if ((yPixel > rBounds.y) && (yPixel < (rBounds.y + rBounds.height))) {
			return true;
		}
		return false;
	}
	
	private boolean isXPixelInClipping(GC gc, int xPixel)
	{
		// Make sure this is even in the clipping range - often only displayed on Normalized data
		Rectangle rBounds = gc.getClipping();
		if ((xPixel > rBounds.x) && (xPixel < (rBounds.x + rBounds.width))) {
			return true;
		}
		return false;
	}
}
