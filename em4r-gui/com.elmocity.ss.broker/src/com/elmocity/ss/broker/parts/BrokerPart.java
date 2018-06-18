
package com.elmocity.ss.broker.parts;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;

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
import com.elmocity.elib.util.Format;
import com.elmocity.ss.broker.DataBroker;
import com.elmocity.ss.broker.DataBroker_IB;
import com.elmocity.ss.broker.SSOrderIn;
import com.elmocity.ss.broker.SSOrder.SSSide;
import com.elmocity.ss.fin.ComboDesc;
import com.elmocity.ss.fin.ComboDesc.ComboType;
import com.elmocity.ss.fin.ExchangeDate;
import com.elmocity.ss.fin.OptionSpacing;
import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityType;
import com.elmocity.ss.ib.IBDataProvider;
import com.ib.client.Order;
import com.ib.client.OrderType;
import com.ib.client.Types.Action;


public class BrokerPart {

	private final static Logger logger = LoggerFactory.getLogger(BrokerPart.class);

	@Inject
	UISynchronize sync;
	
	@Inject
	private IEventBroker eventBroker; 
	private static final String STATUSBAR ="statusbar";

	// ------------------------

	Text symbolText;
	Button connectButton;
	Button addFeedButton;
	Button addOrderButton;

	SimpleConsole console;
	
	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is text output
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "Example Hist Fin Part", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			symbolText = GridHelpers.addEditorToGrid(inputComposite, "Symbol", "TSLA");

			connectButton = GridHelpers.addButtonToGrid(inputComposite, "Connect");
			connectButton.addSelectionListener(new ConnectListener());

			addFeedButton = GridHelpers.addButtonToGrid(inputComposite, "Add Feed");
			addFeedButton.addSelectionListener(new AddFeedListener());

			addOrderButton = GridHelpers.addButtonToGrid(inputComposite, "Add Order");
			addOrderButton.addSelectionListener(new AddOrderListener());
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
		symbolText.setFocus();
	}

	// ------------------------
	
	class ConnectListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("\nConnecting to IB TWS...\n");

			// 7496 = live, 7497 = papertrader
			IBDataProvider.getInstance().connect("localhost", 7497, 7);
			
			console.append("Done.\n");
		}
	}
	class AddFeedListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("\nAdding feed...\n");

			Security security = new Security(SecurityType.STOCK, symbolText.getText());
			
			DataBroker.getInstance().addFeed(security);
			
			console.append("Done.\n");
		}
	}
	class AddOrderListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("\nAdding order... \n");

			String symbol = symbolText.getText();
			Security security = new Security(SecurityType.STOCK, symbol);
			
			SSOrderIn orderIn = new SSOrderIn();
//			orderIn.key = "100";		// auto-created
			orderIn.side = SSSide.SELL;
			orderIn.account = "DU341910";		// paper trader
			orderIn.reference = "robot1";
			orderIn.exchange = "SMART";

			orderIn.price = 1.00;
			orderIn.totalQuantity = 1;

			// scale orders
			orderIn.scalePriceIncrement = 0.00;
//				ibOrder.scaleInitLevelSize(orderIn.scaleInitialComponentSize);	 			// Number of contracts in the first block
//				ibOrder.scaleSubsLevelSize(orderIn.scaleSubsequentComponentSize); 			// Number of contracts in each subsequent block
//				ibOrder.scalePriceIncrement(orderIn.scalePriceIncrement);					// Price kickback (up) each time a block sells
//				ibOrder.scalePriceAdjustInterval(orderIn.scaleAutoAdjustSeconds);			// Auto adjustment timer, in seconds (0 means don't use)
//				ibOrder.scalePriceAdjustValue(orderIn.scaleAutoAdjustAmount);				// Auto adjustment pennies down each time, always NEGATIVE
			

			LocalDate expiry = ExchangeDate.getWeeklyExpiry(LocalDate.now());
			double nos = OptionSpacing.getNativeOptionSpacing(symbol);
			double strike = OptionSpacing.getNearestStrike(symbol, 350.00);

			Security call = new Security(SecurityType.OPTION, symbol, expiry, strike, Security.SecurityRight.CALL);
			IBDataProvider.getInstance().preloadConID(call.getDBKey(), DataBroker_IB.createIBContractFromSecurity(call));
			Security put = new Security(SecurityType.OPTION, symbol, expiry, strike, Security.SecurityRight.PUT);
			IBDataProvider.getInstance().preloadConID(put.getDBKey(), DataBroker_IB.createIBContractFromSecurity(put));

			orderIn.combo = new ComboDesc(ComboType.STRADDLE, call, put);

			// Wait for the CONID values to arrive
			try {
				Thread.sleep(3000);
			}
			catch (InterruptedException exc)
			{
			}
			
			Object sleepLock = new Object();

			DataBroker.getInstance().addOrder(orderIn, sleepLock);
			
			Thread watchdog = new Thread(new Runnable() {
			    public void run()
			    {
			    	Thread.currentThread().setName("DataBrokerX");

			    	boolean okToRun = true;
					while (okToRun) {
						try {
							final long msTimeout = 3000;
							LocalTime start = LocalTime.now();
							synchronized (sleepLock)
							{
								sleepLock.wait(msTimeout);
							}
							LocalTime end = LocalTime.now();
							long msSlept = ChronoUnit.MILLIS.between(start, end);
							if (msSlept < msTimeout) {
//							if (end.isBefore(start.plus(msUntilTimeout, ChronoUnit.MILLIS))) {
								logger.debug("robot woke up early after {} ms", msSlept);
							}
							else {
								logger.debug("robot woke normally after {} ms", msSlept);
							}
						}
						catch (InterruptedException e)
						{
							logger.info("robot thread woke up");
							okToRun= false;
						}
					}
			    }
			});
			watchdog.start();
			
			console.append("Done.\n");
		}
	}
}