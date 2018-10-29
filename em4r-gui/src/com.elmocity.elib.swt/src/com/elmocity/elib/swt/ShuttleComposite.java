package com.elmocity.elib.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.util.Calc;


public class ShuttleComposite extends Composite
{
	Button bigDownButton;
	Button littleDownButton;
	Button stopButton;
	Button littleUpButton;
	Button bigUpButton;
	
	// Just initial defaults... the creator should set up the limits via method calls.
	private double shuttleValue = 0.00;

	private double shuttleMin = -10.00;
	private double shuttleMax = 10.00;
	private double shuttleStep = 0.10;
	
	public final double SHUTTLE_STOP_VALUE = -64000.0;
	
	// Layout constants - this assumes the buttons are square and the native icon images are square (will be scaled)
	private final int bSize = 40;
	
	static final Logger logger = LoggerFactory.getLogger(ShuttleComposite.class);


	public ShuttleComposite(Composite parent, int style)
	{
		super(parent, style);
		init(2);
	}

	public ShuttleComposite(Composite parent, int style, int colSpan)
	{
		super(parent, style);
		init(colSpan);
	}
		
	private void init(int colSpan)
	{
		// Assumes we are on a "standard" input GridLayout of our parents, of 2 columns.
		GridData gd = new GridData();
		gd.horizontalSpan = colSpan;
		setLayoutData(gd);
		
		// Set up a grid for our 5 children (horizontal)
		GridLayout layout = new GridLayout(5, true);
		setLayout(layout);
		
		// Several different ways to adjust the value
		bigDownButton = new Button(this, SWT.PUSH);
		bigDownButton.setImage(ImageCache.getScaled(ImageCache.NAV_SHUTTLE_LITTLE_DOWN_ICON, bSize, bSize));
		bigDownButton.addSelectionListener(new ButtonListener("bigdown"));

		littleDownButton = new Button(this, SWT.PUSH);
//		littleDownButton.setBackground(ColorCache.getColor(ColorCache.THURSDAY));
		littleDownButton.setImage(ImageCache.getScaled(ImageCache.NAV_SHUTTLE_LITTLE_DOWN_ICON, bSize, bSize));
		littleDownButton.addSelectionListener(new ButtonListener("littledown"));

		stopButton = new Button(this, SWT.PUSH);
		stopButton.setImage(ImageCache.getScaled(ImageCache.NAV_SHUTTLE_STOP_ICON, bSize, bSize));
		stopButton.addSelectionListener(new ButtonListener("stop"));

		littleUpButton = new Button(this, SWT.PUSH);
		littleUpButton.setImage(ImageCache.getScaled(ImageCache.NAV_SHUTTLE_LITTLE_UP_ICON, bSize, bSize));
		littleUpButton.addSelectionListener(new ButtonListener("littleup"));

		bigUpButton = new Button(this, SWT.PUSH);
		bigUpButton.setImage(ImageCache.getScaled(ImageCache.NAV_SHUTTLE_BIG_UP_ICON, bSize, bSize));
		bigUpButton.addSelectionListener(new ButtonListener("bigup"));
	}

	/**
	 * Call to initialize params and limits.  Usually just called once at creation time.
	 * @return
	 */
	public void setShuttleParams(double shuttleMin, double shuttleMax, double shuttleStep)
	{
		this.shuttleMin = shuttleMin;
		this.shuttleMax = shuttleMax;
		this.shuttleStep = shuttleStep;
	}
	/**
	 * Call to set the value.  This updates Text or Slider representations, and it needed for relative +/- moves too.
	 * @return
	 */
	public void setShuttleValue(double shuttleValue)
	{
		this.shuttleValue = shuttleValue;
	}
	
	public double getShuttleValue()
	{
		return shuttleValue;
	}


	// ----------------------------------------------------------------------------
	// TODO only one, could be list
	private ShuttleListener shuttleListener = null;

	public interface ShuttleListener
	{
		void shuttleCommand(String command);
		void shuttleValueChanged(double shuttleValue);
	}
	
	public void addShuttleListener(ShuttleListener shuttleListener)
	{
		this.shuttleListener = shuttleListener;
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
			processSelection(message);
		}
	}	
	
	// Meta handler used by all the buttons
	void processSelection(String message)
	{
		double oldValue = shuttleValue;
		double newValue = 0.00;

		switch(message) {
		case "bigdown":		newValue = shuttleValue - (shuttleStep * 10);	break;
		case "littledown":	newValue = shuttleValue - shuttleStep;			break;
		case "stop":		newValue = shuttleValue;						break;
		case "littleup":	newValue = shuttleValue + shuttleStep;			break;
		case "bigup":		newValue = shuttleValue + (shuttleStep * 10);	break;
		default:
			logger.warn("unknown shuttle control message {}", message);
		}
		
		if (shuttleListener != null && message.equals("stop")) {
			shuttleListener.shuttleCommand("stop");
		}
		
		// Make sure we stayed inside the limits
		shuttleValue = Math.max(shuttleMin, Math.min(newValue, shuttleMax));
		
		// Don't bother the listeners if we didn't actually change the value.
		if (!Calc.double_equals(oldValue, shuttleValue)) {
			if (shuttleListener != null) {
				shuttleListener.shuttleValueChanged(shuttleValue);
			}
		}
	}
}
