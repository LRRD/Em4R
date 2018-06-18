package com.elmocity.ss.broker.parts;

import java.time.LocalDate;
import java.util.ArrayList;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.ss.broker.DataBroker_IB;
import com.elmocity.ss.fin.ExchangeDate;
import com.elmocity.ss.fin.OptionSpacing;
import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityType;
import com.elmocity.ss.ib.IBContractDetails;
import com.elmocity.ss.ib.IBContractDetails.ConIDEntry;
import com.elmocity.ss.ib.IBDataProvider;


public class BrokerConIDPart
{
	private final static Logger logger = LoggerFactory.getLogger(BrokerConIDPart.class);

	@Inject
	UISynchronize sync;
	
//	@Inject
//	private IEventBroker eventBroker; 
//	private static final String STATUSBAR ="statusbar";

	// ------------------------

	Text symbolText;
	Button fillCacheButton;
	Button showCacheButton;

	TableViewer viewer;


	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is text output
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "Broker ConID", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			symbolText = GridHelpers.addEditorToGrid(inputComposite, "Symbol", "TSLA");

			fillCacheButton = GridHelpers.addButtonToGrid(inputComposite, "Fill Cache");
			fillCacheButton.addSelectionListener(new FillCacheListener());

			showCacheButton = GridHelpers.addButtonToGrid(inputComposite, "Show Cache");
			showCacheButton.addSelectionListener(new ShowCacheListener());
		}

		// RIGHT
		Composite viewerComposite = GridHelpers.addGridPanel(c, 1);
		{
			// Right side is a console text area, since the lines of text are short, but numerous
			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			viewerComposite.setLayoutData(gridData);

			makeTableViewer(viewerComposite);
			
//			console = new Text(consoleComposite, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
//			GridData gridData2 = new GridData(SWT.FILL, SWT.FILL, true, true);
//			console.setLayoutData(gridData2);
//			console.setText("Idle\n");
		}
	}

	void makeTableViewer(Composite parent)
	{
		viewer = new TableViewer(parent,
				SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		
		// Set the content and label providers
		viewer.setContentProvider(new ArrayContentProvider());
//		viewer.setLabelProvider(new ConIDLabelProvider());
//		viewer.setInput(new FixedTestData());

		// Set up the table
		Table table = viewer.getTable();
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		// COLUMNS
		
		// KEY
		TableViewerColumn colKey = new TableViewerColumn(viewer, SWT.NONE);
		colKey.getColumn().setWidth(200);
		colKey.getColumn().setText("KEY");
		colKey.setLabelProvider(new ColumnLabelProvider() {
		    @Override
		    public String getText(Object element) {
		        ConIDEntry entry = (ConIDEntry)element;
		        return entry.key;
		    }
		});

		// CONID
		TableViewerColumn colConID = new TableViewerColumn(viewer, SWT.NONE);
		colConID.getColumn().setWidth(200);
		colConID.getColumn().setText("ConID");
		colConID.setLabelProvider(new ColumnLabelProvider() {
		    @Override
		    public String getText(Object element) {
		        ConIDEntry entry = (ConIDEntry)element;
		        return "" + entry.conID;	// string
		    }
		});

	}

	@PreDestroy
	public void preDestroy() {
		
	}

	@Focus
	public void onFocus()
	{
		viewer.getControl().setFocus();
	}

	// ------------------------
	
	class ShowCacheListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			ArrayList<ConIDEntry> conIDList = IBContractDetails.getCache();
			logger.debug("conID cache contains {} keys", conIDList.size());
			
			viewer.setInput(conIDList);
			viewer.refresh();
		}
	}

	class FillCacheListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			String symbol = symbolText.getText();
			Security security = new Security(SecurityType.STOCK, symbol);
			
			LocalDate expiry = ExchangeDate.getWeeklyExpiry(LocalDate.now());
			double nos = OptionSpacing.getNativeOptionSpacing(symbol);
			double strike = OptionSpacing.getNearestStrike(symbol, 350.00);

			for (int i = -2; i <= 2; i++) {
				Security call = new Security(SecurityType.OPTION, symbol, expiry, strike + (nos * i), Security.SecurityRight.CALL);
				IBDataProvider.getInstance().preloadConID(call.getDBKey(), DataBroker_IB.createIBContractFromSecurity(call));
				Security put = new Security(SecurityType.OPTION, symbol, expiry, strike + (nos * i), Security.SecurityRight.PUT);
				IBDataProvider.getInstance().preloadConID(put.getDBKey(), DataBroker_IB.createIBContractFromSecurity(put));
			}
			
			// Wait for the CONID values to arrive
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException exc)
			{
			}
		}
	}
}