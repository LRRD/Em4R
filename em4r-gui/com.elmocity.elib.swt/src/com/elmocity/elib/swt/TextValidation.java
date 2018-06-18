package com.elmocity.elib.swt;

import org.eclipse.swt.widgets.Text;

public class TextValidation
{
	public static double parseDouble(Text widget, double defaultValue)
	{
		double v = defaultValue;
		try {
			v = Double.parseDouble(widget.getText());
		}
		catch (NumberFormatException e) {
		}
		return v;
	}
}
