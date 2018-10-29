package com.elmocity.elib.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class VerticalLabelComposite extends Composite
{
	String labelText = "Woof";
	Canvas canvas; 

	private static final Logger logger = LoggerFactory.getLogger(VerticalLabelComposite.class);

	public VerticalLabelComposite(Composite parent, int style)
	{
		super(parent, style);

		GridLayout layout = new GridLayout(1, false);
		setLayout(layout);

		canvas = new Canvas((Composite)this, SWT.NONE);
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		canvas.setLayoutData(gridData);

		canvas.addPaintListener(new PaintListener()
		{
			public void paintControl(PaintEvent e)
			{
				Rectangle clientArea = canvas.getClientArea();
				logger.debug("paint {} {}", clientArea.width, clientArea.height);
				
//				e.gc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
				// Make our colors the same as our container, since the user set the colors there
				e.gc.setBackground(VerticalLabelComposite.this.getBackground());
				e.gc.setForeground(VerticalLabelComposite.this.getForeground());
				
//				e.gc.fillOval(0,0,clientArea.width,clientArea.height);

				// Make the string all caps
				String s = labelText.toUpperCase();

				// Guess how wide each char is, based on the font or pre-render offscreen for extent.
				final int estimatedCharWidth = 40;		// HACK
				
				// label ROTATED 90 degrees
				e.gc.setFont(FontCache.getFont(FontCache.EMRIVER_HUGE_DEVICE, SWT.BOLD));
				int y = clientArea.x + (clientArea.width / 2) + ((s.length() * estimatedCharWidth) / 2);
				drawText(e.gc, clientArea.x + 10, y, -90, s, VerticalLabelComposite.this.getForeground(), null);
//				drawText(e.gc, clientArea.x + 10, y, -90, s, VerticalLabelComposite.this.getForeground(), ColorCache.getColor(ColorCache.FRIDAY));		// DEBUG force bounding box on text via background color
			}
		});
	}

	public void setText(String text)
	{
		labelText = text;
	}

	private static void drawText(GC gc, int x, int y, int angle, String s, Color fgColor, Color bgColor)
	{
		Color old_fgColor = gc.getForeground();
		Color old_bgColor = gc.getBackground();
		
        if (angle != 0) {
        	// Rotate the whole world
        	Transform tr = new Transform(gc.getDevice());
        	tr.rotate(-90);
        	gc.setTransform(tr);
        	int temp = x;
        	x = -y;
        	y = temp;
//    	    g2d.translate((float)x,(float)y);
//    	    g2d.rotate(Math.toRadians(angle));	    		
    	}

		gc.setForeground(fgColor);
		if (bgColor != null) {
    		gc.setBackground(bgColor);
    		gc.drawText(s, x, y);
    	}
    	else {
    		gc.drawText(s, x, y, true);	// transparent
    	}
		
		gc.setForeground(old_fgColor);
		gc.setBackground(old_bgColor);
		
       if (angle != 0) {
        	// Un-rotate the whole world
        	gc.setTransform(null);
       }        	

	}

}
