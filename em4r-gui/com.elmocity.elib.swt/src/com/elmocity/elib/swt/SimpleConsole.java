package com.elmocity.elib.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

public class SimpleConsole
{
	Text console;
	
	// NOTE: assumes parent has a grid layout (of one column).
	
	// TODO -or- the provided style to the default? or override
	public SimpleConsole(Composite parent, int style, int rows)
	{
		console = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.heightHint = rows * console.getLineHeight();
		console.setLayoutData(gd);
		
		console.setText("Idle.\n");
	}
	
	// Send in a blank string to force a newline?
	public void append(String s)
	{
		if (console == null || console.isDisposed()) {
			return;
		}
		console.append(s + "\n");
	}
	
	public void setFocus()
	{
		console.setFocus();
	}
}
