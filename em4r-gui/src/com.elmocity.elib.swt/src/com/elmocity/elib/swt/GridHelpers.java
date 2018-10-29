package com.elmocity.elib.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class GridHelpers
{
	// ---------------------------------------------------------------------------------------------------------
	// Helpers methods that a Part class can call to layout param widgets to a input or dialog area
	
	static final boolean visualDebug = false;
	static int visualDebugStyle = SWT.NONE;
	static {
		if (visualDebug) {
			visualDebugStyle = SWT.BORDER | SWT.FLAT | SWT.MAX;
		}
	}
	
    // Tell the grid layouts that all controls should be a certain width.  This should help things line up better for
    // nested composites and such.  Generally used for buttons, text edit controls... things that don't span 2 grid columns.
    final static int gridWidthHint = 120;

    
	// Make a new sub-panel, with a grid layout of N columns.  Typically 2 columns for standard label+editor layout.
	public static Composite addGridPanel(Composite parent, int columns)
	{
		Composite c = new Composite(parent, SWT.NONE);
		{
			GridLayout layout = new GridLayout(columns, false);
			c.setLayout(layout);

			if (visualDebug) {
				c.setBackground(ColorCache.getRandomColor());
			}
		}
		return c;
	}

	public static Composite addTitlePanel(Composite parent, String title, int columns)
	{
		Composite c = new Composite(parent, SWT.NONE);
		{
			GridLayout layout = new GridLayout(1, true);
			c.setLayout(layout);

			// This title panel serves as a minimal width that the whole params panel will end up.
			GridData data = new GridData();
			data.horizontalSpan = columns;
			data.widthHint = gridWidthHint * 2 + 20;		// span 2 columns, plus tons of slop for nested groups
			c.setLayoutData(data);

			if (visualDebug) {
				c.setBackground(ColorCache.getRandomColor());
			}
		}
	
		Label label = new Label(c , SWT.FILL);
		label.setText(title);
		label.setFont(FontCache.getFont(FontCache.WU_TITLE));
		label.setAlignment(SWT.CENTER);

		// Force this label to eat up all the space in the panel, so any background color changes look correct
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		label.setLayoutData(gd);
		
		return c;
	}
	
	public static Composite addGroupPanel(Composite parent, String title)
	{
		return addGroupPanel(parent, title, 1);
	}
	public static Composite addGroupPanel(Composite parent, String title, int columns)
	{
		Group c = new Group(parent, SWT.NONE);
		{
			GridLayout layout = new GridLayout(2, true);
			c.setLayout(layout);
			
			c.setText(title);
			c.setFont(FontCache.getFont(FontCache.WU_GROUP));

			if (visualDebug) {
				c.setBackground(ColorCache.getRandomColor());
			}
		}
		
		GridData gridData = new GridData();
		gridData.horizontalSpan = columns;
	    gridData.horizontalAlignment = SWT.FILL;
	    gridData.widthHint = gridWidthHint * 2;		// spans 2 columns	// TODO "columns"?
		c.setLayoutData(gridData);
		
		return c;
	}
	
	// Assumes parent is 2 column gridlayout
	public static Text addEditorToGrid(Composite parent, String labelText)
	{
		return addEditorToGrid(parent, labelText, "");
	}
	public static Text addEditorToGrid(Composite parent, String labelText, String defaultText)
	{
		return addEditorToGrid(parent, labelText, SWT.NONE, defaultText);
	}
	public static Text addEditorToGrid(Composite parent, String labelText, int labelAlignment, String defaultText)
	{
		// Label in the first column
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		if (labelAlignment != SWT.NONE) {
			label.setAlignment(labelAlignment);
		}
		GridData gd = new GridData();
		gd.widthHint = gridWidthHint;
		label.setLayoutData(gd);
		
		// Text editor in the second column
		Text w = new Text(parent, SWT.SINGLE | SWT.BORDER);
		w.setText(defaultText);
		w.setTextLimit(30);
		GridData gd2 = new GridData();
		gd2.widthHint = gridWidthHint;
		w.setLayoutData(gd2);
		
		return w;
	}

	// Assumes parent is 2 column gridlayout
	public static Button addButtonToGrid(Composite parent, String buttonText)
	{
		return addButtonToGrid(parent, buttonText, "");
	}
	public static Button addButtonToGrid(Composite parent, String buttonText, String labelText)
	{
		// Button in the first column
		Button w = new Button(parent, SWT.PUSH);
		w.setText(buttonText);
		GridData gd = new GridData();
		gd.widthHint = gridWidthHint;
		w.setLayoutData(gd);

		// Label in the second column (usually blank)
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		
		return w;
	}
	
	public static Button addTransparentButtonToGrid(Composite parent, String buttonText)
	{
		// Need a shim composite to force the background color to show through to the Button (rendered by the OS).
		// If we did not do this, we get the entire button drawn by the OS, which is ugly grey in Windows.
		// Simply setting the background color on a Button does not work like you assume, it is just a hint that is largely ignored.
		// NOTE that buttons that are only Images (no text) work fine without this shim, as the transparent parts of the image
		// allow background to show through (which is the real background color), whereas the background of text is always opaque OS gray.
		Composite bComp = new Composite(parent, SWT.NONE);
		bComp.setLayout(new GridLayout());
		bComp.setBackgroundMode(SWT.INHERIT_FORCE);

		Button w = new Button(bComp, SWT.PUSH);
	//	w.setBackground(ColorCache.getColor(ColorCache.BLACK));		// FAIL USELESS
		w.setText(buttonText);

		return w;
	}	

	
	// Assumes parent is 2 column gridlayout
	public static void addSeparatorToGrid(Composite parent)
	{
		Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
		GridData gridData = new GridData();
		gridData.horizontalSpan = 2;
		gridData.horizontalAlignment = SWT.FILL;
		separator.setLayoutData(gridData);
	}
	
	public static void adjustCustomToGrid(Composite parent, Control left, Control right)
	{
		// first column
		{
			GridData gridData = new GridData();
			gridData.widthHint = gridWidthHint;
			left.setLayoutData(gridData);
		}

		// second column
		{
			GridData gridData = new GridData();
			gridData.widthHint = gridWidthHint;
			right.setLayoutData(gridData);
		}
	}

}
