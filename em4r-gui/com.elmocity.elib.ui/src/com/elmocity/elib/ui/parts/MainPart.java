
package com.elmocity.elib.ui.parts;

import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.e4.ui.di.Focus;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.swt.ColorCache;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.SimpleConsole;
import com.elmocity.elib.util.Calc;
import com.elmocity.elib.util.FileIO;
import com.elmocity.elib.util.Format;
import com.elmocity.elib.util.SystemInfo;

public class MainPart
{
	Button loggingButton;
	Button utilButton;
	Button SWTButton;
	Button chartButton;
	
	SimpleConsole console;
	
	private static final Logger logger = LoggerFactory.getLogger(MainPart.class);
	
	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is text output console
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "ELib Main", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			loggingButton = GridHelpers.addButtonToGrid(inputComposite, "Logging");
			loggingButton.addSelectionListener(new LoggingListener());

			utilButton = GridHelpers.addButtonToGrid(inputComposite, "Util");
			utilButton.addSelectionListener(new UtilListener());

			SWTButton = GridHelpers.addButtonToGrid(inputComposite, "SWT");
			SWTButton.addSelectionListener(new SWTListener());

			chartButton = GridHelpers.addButtonToGrid(inputComposite, "Chart");
			chartButton.addSelectionListener(new ChartListener());
		}

		// RIGHT
		Composite consoleComposite = GridHelpers.addGridPanel(c, 1);
		{
			// Right side is a console text area, since the lines of text are short, but numerous
			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			consoleComposite.setLayoutData(gridData);

			console = new SimpleConsole(consoleComposite, SWT.NONE, 10);
		}
	}
	
	@PreDestroy
	public void preDestroy()
	{
	}

	@Focus
	public void onFocus()
	{
		console.setFocus();
	}
	
	// ----------------------------------------------------------------------------------------------------

	@Inject Shell shell;
	
	class LoggingListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Showing logging plugin info...");
			
//			console.append(ShowDebug.getDebugInfo());
			
			console.append("Done.");
		}
	}

	class UtilListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Showing util plugin info...");
			
			String s;
			
			s = Calc.unitTest();
			if (s != null && s.length() > 0) {
				console.append(s);
			}
			s = FileIO.unitTest();
			if (s != null && s.length() > 0) {
				console.append(s);
			}
			s = Format.unitTest();
			if (s != null && s.length() > 0) {
				console.append(s);
			}

			Set<String> keySet = SystemInfo.getKeySet();
			for (String key : keySet) {
				String line = String.format("%s = %s", key, SystemInfo.getSystemInfo(key));
				console.append(line);
			}
			console.append("Done.");
		}
	}

	class SWTListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Dumping SWT registry items...");

			
			Set<String> colorKeys = ColorCache.getColorKeys();
			console.append("ColorCache found " + colorKeys.size() + " keys.");

			for (String key : colorKeys) {
				console.append("   " + key);
			}

			console.append("TODO FontCache found " + colorKeys.size() + " keys.");

			console.append("TODO ImageCache found " + colorKeys.size() + " keys.");
			
			console.append("Done.");
		}
	}

	class ChartListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Showing chart plugin info...");
			
			console.append("TODO.");
			
			console.append("Done.");
		}
	}

	
}