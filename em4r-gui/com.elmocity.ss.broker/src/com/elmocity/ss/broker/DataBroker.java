package com.elmocity.ss.broker;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.util.Format;
import com.elmocity.ss.database.HistQuote;
import com.elmocity.ss.fin.ExchangeDate;
import com.elmocity.ss.fin.OptionSpacing;
import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityType;
import com.elmocity.ss.ib.IBDataProvider;
import com.elmocity.ss.ib.IBOrderListener;
import com.elmocity.ss.ib.IBOrderOut;
import com.elmocity.ss.ib.IBQuote;
import com.elmocity.ss.ib.IBQuoteListener;
import com.ib.client.Contract;
import com.ib.client.Order;

/*
 * Representation of a Brokerage Firm, providing all the calls needed for a robot/backtester etc that are needed.
 * This hides the implementation and allows multiple brokers to provide data or trade execution.
 * <p>
 * All calls should only be using SS classes, never passing up or taking in a trading platform object/class/ID.
 * <p>
 * Feeds are shared by multiple robots, feeds are never shutdown, so no reference counting etc is needed.  Changes
 * to the feed never alert any robot, since nothing time-critical really happens... just new price ticks flow in.
 * <p>
 * Orders are owned by a single particular robot that created it.  Changes to the order from IB need to alert the
 * robot immediately to take action, such as fills, cancels, partial fills, etc so the robot can handle OCA.
 *  
 */
public class DataBroker		// IB IMPLEMENTATION
{
	String name = "DataBroker_IB";
	
	// TODO this could be a derived class
	// NOTE there can be many DataBrokers (usually one per robot) all using the same singleton DataProvider
	static IBDataProvider provider = IBDataProvider.getInstance();
	
//	static private Object listLock = new Object();
	static ArrayList<SSFeed> feedList = new ArrayList<SSFeed>(50);
	static ArrayList<SSOrder> orderList = new ArrayList<SSOrder>(50);
	
	// DEBUG thread to keep periodically post the contents of the feed and order list to the log.
	static Thread watchdog = null;
	// DEBUG should we also watch IB openOrders() to compare the scale trader or manual moves to our expected SELL limit price?
	static boolean watchIBOpenOrders = false;

	
	// TODO should hide this in a getter or action methods
	static Object robotSleepLock = new Object();
	
	static private DataBroker INSTANCE = new DataBroker();
	
	static final Logger logger = LoggerFactory.getLogger(DataBroker.class);

	
	
	// Private constructor, since we are a singleton
	private DataBroker()
	{
		// First robot that creates us
		if (watchdog == null) {
			watchdog = createWatchdog();
		}
	}


	public static DataBroker getInstance()
	{
		return INSTANCE;
	}

	public static void connect()
	{
		// 7496 = live, 7497 = papertrader
		IBDataProvider.getInstance().connect("localhost", 7497, 7);
	}
	
	public static boolean isConnected()
	{
		return provider.isConnected();
	}
	
	// TODO: fix this to have the robots call this object to verify all conID values are loaded instead.
	// Hack to walk the feed list for IB robots, to verify all the CONID values are cached yet.
	public ArrayList<SSFeed> getFeedList()
	{
		return feedList;
	}
	
	public Object getSleepLock()
	{
		// Robot should do a -synchronized- timeout wait on this object, instead of Thread.sleep(), so we can wake him up on interesting changes.
		return robotSleepLock;
	}
	
	
	// Used by the OrderManagers to alert a robot of fills/etc
	// TODO this is broken, the databroker wakes up a random robot, since all the robots use the same databroker
	public void wakeSleepLock()
	{
		synchronized (robotSleepLock)
		{
			// Wake up one thread watching the lock, which should be the one and only Robot using this broker.
			robotSleepLock.notify();
		}
//		SS.log("some manager asked to wake the robot");
	}
	
	public void watchIBOpenOrders(boolean value)
	{
		// Only do stuff if we are changing the status of the flag
		if (watchIBOpenOrders != value) {
			watchIBOpenOrders = value;
			provider.watchOpenOrders(watchIBOpenOrders);
		}
	}
	
//	public IBOpenOrder getOpenOrder(Integer key)
//	{
//		return provider.getOpenOrder(key);
//	}
	
	// TODO. this is bad, since multiple robots use the same watchdog.  it needs a reference counter to stay running until the last robot quits.
	public void stopWatchdog()
	{
		if (watchdog != null) {
			watchdog.interrupt();
			watchdog = null;
		}
	}
	
	// Connection/Disconnect/AccountList and logging are all handled from the WU that shows/tests the connection.
	// TODO should be an interface here.
	
	// ---------------------------------------------------------
	// FEEDS
	
	public synchronized void addFeed(Security security)
	{
		int i = findFeedIndex(security);
		if (i >= 0) {
			logger.trace("addfeed found existing match for {}", security.debugString());
			return;
		}
		feedList.add(new SSFeed(security));

		// TODO HACK? timing issues?
		// Have to make sure that IB has the conID for this security first.  Yuck.
		Contract contract = DataBroker_IB.createIBContractFromSecurity(security);
		provider.preloadConID(security.getDBKey(), contract);
		
//		provider.addFeed(this, security);
		provider.addFeed(DataBroker_IB.createIBContractFromSecurity(security), new FeedUpdateListener_IB(security));
	}
	
	public synchronized SSQuote getQuote(Security security)
	{
		int i = findFeedIndex(security);
		if (i < 0) {
			logger.trace("getquote missing for {}", security.debugString());
			return null;
		}
		
		return feedList.get(i).quote;
	}
	
// OBSOLETE, the low level IB should be using a callback via IBQuoteListener
//	public synchronized void updateFeed(Security security, SSQuote quote)
//	{
//		int i = findFeedIndex(security);
//		if (i < 0) {
//			logger.trace("updatefeed missing for {}", security.debugString());
//			return;
//		}
//		feedList.get(i).quote = quote;
//	}
	
	// private already only called from inside a -synchronized- method, so no need here.
	private int findFeedIndex(Security security)
	{
		for (int i = 0; i < feedList.size(); i++) {
			if (feedList.get(i).security.same(security)) {
				return i;
			}
		}
		return -1;
	}
	
	class FeedUpdateListener_IB implements IBQuoteListener
	{
		private final Security security;

		public FeedUpdateListener_IB(Security security)
		{
			this.security = security;
		}
		
		@Override
		public void update(IBQuote quote)
		{
			synchronized (DataBroker.this)
			{
				int i = findFeedIndex(security);
				if (i < 0) {
					logger.trace("updatefeed missing for {}", security.debugString());
					return;
				}
				feedList.get(i).quote = DataBroker_IB.newSSQuoteFromIBQuote(quote);
			}
		}
	}
	
	// ---------------------------------------------------------
	// ORDERS
	
	public synchronized void addOrder(SSOrderIn orderIn, Object robotSleepLock)
	{
		int i = findOrderIndex(orderIn.key);
		if (i >= 0) {
			logger.warn("addorder found existing key {} for {}", orderIn.key, orderIn.debugString());
			return;
		}

		// Create the master application order and add it to the list.
		SSOrder ssOrder = new SSOrder(orderIn);
		orderList.add(ssOrder);

		// Assemble a combo contract, with all the legs and conID values.
		Contract ibcontract = DataBroker_IB.createIBContractFromComboDesc(ssOrder.orderIn);

		// Build up an IB order object with all the user-provider parameters
		Order ibOrder = DataBroker_IB.makeIBOrderFromSSOrderIn(ssOrder.orderIn, IBDataProvider.getInstance().getClientId());

		
		// PreFill some of what we know on the output side
//		ssOrder.orderOut.price = ssOrder.orderIn.price;
		ssOrder.orderOut.filled = 0;
		ssOrder.orderOut.remaining = ssOrder.orderIn.totalQuantity;
		ssOrder.orderOut.status = SSOrderStatus.PENDING;

//		// Temp stuff the copy of our original contract/order pair into the order for debugging
//		ssOrder.ibContract = contract;
//		ssOrder.ibOrder = ibOrder;

		int ibOrderID = provider.AddOrder(ssOrder.orderIn.key, ibcontract, ibOrder, new OrderUpdateListener_IB(ssOrder.orderIn.key, robotSleepLock));
		ssOrder.ibOrderID = ibOrderID;
	}
	
	public synchronized SSOrder getOrder(String key)
	{
		int i = findOrderIndex(key);
		if (i < 0) {
			logger.warn("getorder missing key {}", key);
			return null;
		}
		
		return orderList.get(i);
	}
	
	// TODO: this should validate that all the legs of the combo have real data (sometimes takes 100ms after opening bell to get first tick) and that
	// the order is in a SUBMITTED state (sometimes takes several seconds for a new order to go "live" from the IB side (and get us an ACK).
	public synchronized boolean readyToAdjustOrder(String key)
	{
		// TODO see the IBHintAllOrdersSent	-- if a robot is repeatedly calling us for a missing order, we could/need to fire off openorders request.
		int i = findOrderIndex(key);
		if (i < 0) {
			logger.warn("readytoadjust missing key {}", key);
			return false;
		}
		return true;	// TODO
		// We always have a Live copy of the order, since we saved it off when we initially sent it to IB.
		
//		return provider.readyToAdjustOrder(orderList.get(i).ibOrderID);
	}
	
	// Called DOWN from the robot to move the price
	public synchronized void adjustOrder(String key, int quantity, double price)
	{
		int i = findOrderIndex(key);
		if (i < 0) {
			logger.warn("adjustorder missing key {}", key);
			return;
		}
		
		provider.adjustOrder(orderList.get(i).ibOrderID, quantity, price);
	}
	// Called DOWN from the robot to move the price
	public synchronized void adjustOrder(String key, int quantity, double price, int scaleInitLevelSize, int scaleAutoAdjustSeconds)
	{
		int i = findOrderIndex(key);
		if (i < 0) {
			logger.warn("adjustorder missing key {}", key);
			return;
		}
		
		provider.adjustOrder(orderList.get(i).ibOrderID, quantity, price, scaleInitLevelSize, scaleAutoAdjustSeconds);
	}
	
// OBSOLETE the low level calls up via an interface now IBOrderListener
//	// Called UP from the provider to post changes seen from IB/TWS (like a fill or OCA cancel)
//	public synchronized void updateOrder(String key, SSOrderOut orderOut)
//	{
//		int i = findOrderIndex(key);
//		if (i < 0) {
//			logger.warn("updateorder missing key {}", key);
//			return;
//		}
//
//		orderList.get(i).orderOut = orderOut;
//	}

	public synchronized void cancelOrder(String key)
	{
		int i = findOrderIndex(key);
		if (i < 0) {
			logger.warn("cancelorder missing key {}", key);
			return;
		}

		provider.cancelOrder(orderList.get(i).ibOrderID);
	}
	
	// Private called from inside other -synchronized- methods, so no need here
	private int findOrderIndex(String key)
	{
		for (int i = 0; i < orderList.size(); i++) {
			if (orderList.get(i).orderIn.key.equals(key)) {
				return i;
			}
		}
		return -1;
	}
	
	// --------

	class OrderUpdateListener_IB implements IBOrderListener
	{
		private final String key;
		private final Object robotSleepLock;

		public OrderUpdateListener_IB(String key, Object robotSleepLock)
		{
			this.key = key;
			this.robotSleepLock = robotSleepLock;
		}
		
		@Override
		public void update(String key, IBOrderOut ibOrderOut)
		{
			synchronized (DataBroker.this)
			{
				int i = findOrderIndex(key);
				if (i < 0) {
					logger.trace("updateorder missing for {}", key);
					return;
				}
				orderList.get(i).orderOut = DataBroker_IB.newSSOrderOutFromIBOrderOut(ibOrderOut);
			}
		}

		@Override
		public void alert()
		{
			logger.debug("alerting {} using obj {}", key, robotSleepLock.hashCode());
			synchronized (robotSleepLock)
			{
				robotSleepLock.notify();
			}
		}
		
//		// Convert from IB specific quote into the application quote (SS specific)
//		private SSOrderOut newSSOrderOutFromIBOrderOut(IBOrderOut ibOrderOut)
//		{
//			SSOrderOut ssOrderOut = new SSOrderOut(quote.bid, quote.ask, quote.last, quote.imp_vol, quote.halted, quote.last_update);
//			return ssOrderOut;
//		}
	}
	
	
	private Thread createWatchdog()
	{
		Thread watchdog = new Thread(new Runnable() {
		    public void run()
		    {
		    	Thread.currentThread().setName("DataBrokerWD");

		    	final LocalTime openingBellTime = LocalTime.of(9, 30, 00);
		    	
		    	boolean okToRun = true;
				while (okToRun) {
					LocalTime now = LocalTime.now();
				
					long msDelay;
					long secondsAfterOpeningBell = ChronoUnit.SECONDS.between(openingBellTime, now);
					// If we are "near" opening bell by a few seconds, we should run the watchdog alot faster to catch bugged feeds or IB issues.
					if ((secondsAfterOpeningBell > -15) && (secondsAfterOpeningBell < 15)) {	// Plus or minus 15 seconds from opening bell
						msDelay = 3 * 1000;
					}
					// If it is after 3 mintues, drop way way down... probably could turn it off
					else if (secondsAfterOpeningBell > 185) {
						msDelay = 90 * 1000;
					}
					else {
						msDelay = 10 * 1000;
					}

					try {
						Thread.sleep(msDelay);
					}
					catch (InterruptedException e)
					{
						logger.info("databroker watchdog thread asked to terminate");
						// Fall through and do one last spew of the broker's data.
						okToRun= false;
						// All the orders have been cancelled, but we want to hold up a couple seconds to see the ACKs if possible.
						try {
							Thread.sleep(2000);
						}
						catch (InterruptedException e2)
						{
						}
//						break;
					}
					
					// Check on the list of feeds and see what the values are:
					// This relies on the fact that feeds are never removed... they are only added to the broker.
					// So we don't really need synchronization... the feedList/orderList might get longer as we loop through them, but should be safe as is.
					synchronized (DataBroker.this)
					{
						// Show the running feeds
						for (int i = 0; i < feedList.size(); i++) {
							String securityString = feedList.get(i).security.debugString();
							String quoteString = feedList.get(i).quote.debugString();
							logger.trace("   FEED  {} {} {}",
									String.format("%03d", i), securityString, quoteString);
						}
						// Show the running orders
						for (int i = 0; i < orderList.size(); i++) {
							String orderInString = orderList.get(i).orderIn.debugString();
							String orderOutString = orderList.get(i).orderOut.debugString();
							logger.trace("   ORDER {} {} {} IBOID = {}",
									String.format("%03d", i), orderInString, orderOutString, orderList.get(i).ibOrderID);
						}
					}
				}
				// thread exit.
		    }
		});
		
		watchdog.start();
		return watchdog;
	}

	public void preloadConIDCache(String underlyingSymbol, double underlyingPrice, int chainWidth)
	{
		if (!provider.isConnected()) {
			//log("Not connected to IB.  " + underlyingSymbol);
			return;
		}

		Security security = new Security(SecurityType.STOCK, underlyingSymbol);
		provider.preloadConID(security.getDBKey(), DataBroker_IB.createIBContractFromSecurity(security));

		LocalDate expiry = ExchangeDate.getWeeklyExpiry(LocalDate.now());
		double nos = OptionSpacing.getNativeOptionSpacing(underlyingSymbol);
		double nearestStrike = OptionSpacing.getNearestStrike(underlyingSymbol, underlyingPrice);

		// If the called failed to determine a LAST or CLOSE price for the stock, we can just ask one time to TWS for
		// the 0.00 strike and it will return the entire chain for this expiry (50-100 legs).  Don't ask for -2.50 and +2.50 etc.
		if (underlyingPrice < 0.01) {
			chainWidth = 0;
		}

		for (int i = -chainWidth; i <= chainWidth; i++) {
			double strike = nearestStrike + (nos * i);
			Security call = new Security(SecurityType.OPTION, underlyingSymbol, expiry, strike, Security.SecurityRight.CALL);
			provider.preloadConID(call.getDBKey(), DataBroker_IB.createIBContractFromSecurity(call));
			Security put = new Security(SecurityType.OPTION, underlyingSymbol, expiry, strike, Security.SecurityRight.PUT);
			provider.preloadConID(put.getDBKey(), DataBroker_IB.createIBContractFromSecurity(put));
		}
		final int expectedCount = (1 + chainWidth * 2) * 2 + 1;		// N chains * 2 legs each + 1 for underlying
		logger.debug("" + underlyingSymbol + " snapshot price = " + Format.price(underlyingPrice));
		logger.debug("  requested " + expectedCount + " nearby legs (including + 1 for underlying conid)");
	}

	public ArrayList<HistQuote> downloadHistData(Security security, LocalDate date)
	{
		return DataBroker_IB.downloadHistData(security, date);
	}
}

