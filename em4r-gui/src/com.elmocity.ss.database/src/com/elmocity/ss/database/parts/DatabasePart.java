
package com.elmocity.ss.database.parts;

import java.time.LocalDate;
import java.util.ArrayList;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.SimpleConsole;
import com.elmocity.ss.database.HistQuote;
import com.elmocity.ss.database.HistQuoteDAO;
import com.elmocity.ss.database.SQLCore;

public class DatabasePart
{
	private final static Logger logger = LoggerFactory.getLogger(DatabasePart.class);

	@Inject
	UISynchronize sync;
	
	@Inject
	private IEventBroker eventBroker; 
	private static final String STATUSBAR ="statusbar";

	// ------------------------

	Text connectionText;
	Button connectButton;
	Button histButton;

	SimpleConsole console;

	
	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is text output
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "Database Part", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			connectionText = GridHelpers.addEditorToGrid(inputComposite, "Connection String", "");

			connectButton = GridHelpers.addButtonToGrid(inputComposite, "Connect");
			connectButton.addSelectionListener(new ConnectListener());

			histButton = GridHelpers.addButtonToGrid(inputComposite, "Historical Quote");
			histButton.addSelectionListener(new HistQuoteListener());
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
	public void preDestroy() {
		
	}

	@Focus
	public void onFocus()
	{
		connectionText.setFocus();
	}

	// ------------------------
	
	class ConnectListener extends SelectionAdapter
	{
		// Configure the chart to something like it might look for the em river table
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Fetching connection info...");

			// Debug function that sends back a large multi-line description of the JDBC driver and connection info
			String info = SQLCore.getDebugConnectionInfo();
			console.append(info);
			
			console.append("Done.");
		}
	}
	
	class HistQuoteListener extends SelectionAdapter
	{
		// Configure the chart to something like it might look for the em river table
		
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Fetching a single historical quote...");

			// TODO read this data from some GUI controls
			String securityKey = "TSLA";
			LocalDate date = LocalDate.of(2017, 6,  1);
			int bar = 1;

			console.append("" + securityKey + " " + date.toString() + " " + bar);

			// Expect to get zero or one rows back
			ArrayList<HistQuote> rows = HistQuoteDAO.findByDateAndBar(securityKey, date, bar);
			if (rows != null) {
				console.append("SQL returned " + rows.size() + " rows.");
				for (int i = 0; i < rows.size(); i++) {
					console.append("   " + rows.get(i).debugString());
				}
			}

			console.append("Done.");
		}
	}
}