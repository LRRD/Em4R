package com.elmocity.ss.ib;

import com.ib.controller.ApiController;
import com.ib.controller.ApiController.IBulletinHandler;
import com.ib.controller.ApiController.IConnectionHandler;
import com.ib.controller.ApiController.IContractDetailsHandler;
import com.ib.controller.ApiController.ILiveOrderHandler;
import com.ib.controller.ApiController.IOrderHandler;
import com.ib.controller.ApiController.ITimeHandler;
import com.ib.controller.ApiController.ITopMktDataHandler;
import com.ib.controller.ApiController.ITradeReportHandler;
import com.ib.controller.ApiController.TopMktDataAdapter;
import com.ib.controller.Formats;

import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityType;
import com.elmocity.ss.ib.IBLiveOrders.IBLiveOrder;
import com.elmocity.ss.ib.IBOpenOrders.IBOpenOrder;
import com.elmocity.ss.fin.Security.SecurityRight;
//Static import to fetch all the constant strings for shorthand
import static com.elmocity.ss.ib.IBMarker.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.elmocity.elib.util.Calc;
import com.elmocity.elib.util.Format;
import com.ib.client.ComboLeg;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.Execution;
import com.ib.client.ExecutionFilter;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;
import com.ib.client.OrderType;
import com.ib.client.TickType;
import com.ib.client.Types.Action;
import com.ib.client.Types.MktDataType;
import com.ib.client.Types.NewsType;
import com.ib.client.Types.OcaType;
import com.ib.client.Types.Right;
import com.ib.client.Types.SecType;
import com.ib.controller.AccountSummaryTag;
import com.ib.controller.ApiConnection;
import com.ib.controller.ApiConnection.ILogger;

//import trader.DataBroker;
//import trader.ISSLog;
//import trader.SSOrder;
//import trader.SSOrderIn;
//import trader.SSOrderStatus;
//import trader.SSOrder.SSSide;

/**
 * Singleton class to hide all the IB specific stuff, so changing to another stock broker would be easy.
 * <p>
 * Old versions used to make calls directly into a modified version of the APIController to access the nextValidID and
 * other values, in order to handle multiple robot applications using the same TWS/account combination.  No longer needed. 
 */
public class IBDataProvider
{
	private static IBDataProvider instance = new IBDataProvider();
	
	private static final Logger logger = LoggerFactory.getLogger(IBDataProvider.class);


	// We define an interface where we send status information that is suitable for a human/GUI display.
	// This is optional... the used of this package can simply never create an implementation of it, nor set this value.
	private static IBIStatusMessage statusMessage;
	
	// IB specific stuff
	public int clientID = 5;		// default to 5 (JFace 3 version was clientID 1 and 2, JFace 5 used "3", Azure uses "4")
	
	// The one and only controller, written by IB without any synchronization, so we have to do that ourselves.
	ApiController controller;
	ILogger inLogger = new IBLogger();
	ILogger outLogger = new IBLogger();
	ArrayList<String> accountList = new ArrayList<String>(10);

	MyIConnectionHandler connectionCallbacks = new MyIConnectionHandler();

	
	// List to track our Order Efficiency Ratio (OER) and Requests per Second (RPS) that could make IB hate us.
	private static IBMarkerList markers = new IBMarkerList();

	
	// Calls to the controller that create a request or create an order will increment the value of 
	// m_orderID or m_reqID inside the controller class.  IB did not protect/synchronize that counter, so
	// our multi-threaded (or multiple instances) robot can break it (2 will get same ID).
	//
	// The solution is either to modify their code and add synchronization to those counters, or kludge it here.
	// Modifying their code is annoying, since updates to the API source from IB (a couple times a year) will
	// overwrite the changes and we have to reapply/merge the changes back in each time.
	//
	// So for now, code in this class will use a -synchronized (this)- pattern to protect us against the
	// counters being modified simultaneously by threads.  Running two copies of this app at once will break it.
	// IB actually recommends only using one robot per TWS instance (probably because their code is crap).
	//
	// We use the "this" lock because both the reqID and orderID counters are intertwined... being set at the
	// same time by the connect() callback.  We could use a private lock object instead of "this".

	
	// -private-, to stop a second instance from being created
	private IBDataProvider()
	{
		// These loggers can dump raw packet data, see the -suppress- flag in the IBLogger class
		controller = new ApiController(connectionCallbacks, inLogger, outLogger);
	}
	
	public static IBDataProvider getInstance()
	{
		return instance;
	}
	
	// Log some trace/debug messages to a visible GUI control, instead of just to stdout.
	// The provided interface must invoke the GUI setter on the SWT main thread.
	public static void setLog(IBIStatusMessage statusMessage)
	{
		IBDataProvider.statusMessage = statusMessage;
	}
	
	private static void log(String s)
	{
		// Make sure the caller set an output log for us (usually a scroll Text control)
		// NOTE: In SS 5.0, the Text widget on the WUIBConnection is the receiver for these messages, instead of each robot.
		// This can be confusing, since each robot Text widget is not showing errors, instead the all go to WUIBConnection. 
		if (statusMessage != null) {
			statusMessage.send(s);
		}
		else {
			// Hopefully we can get rid of the IBConnection Text area completely.  The errors/warnings for a robot
			// should just go to the new logger (and into that specific robot's Text area if needed).
			//
			// Unfortunately, we still have startup/shutdown IB connection information that happens before/after robots exist,
			// so we still need to send messages to the WUIBConnection..  Yuck/weird.  Send it off to the ROOT logger by default.
			// TODO: there should be a bailout log() implementation in SSLogger for these situations, to at least get to the console.
			LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME).warn(s);
		}
	}
	
//	public static void showOER()
//	{
//		// Basic info, just show the count of different types of actions we recorded.
//		String[] messages = { OER_CREATE_ORDER, OER_MODIFY_ORDER, OER_CANCEL_ORDER };
//		
//		for (int type = 0; type < messages.length; type++) {
//			int count = 0;
//			for (int i = 0; i < OER.markers.size(); i++) {
//				Marker marker = OER.markers.get(i);
//				if (marker.name.equals(messages[type])) {
//					count++;
//				}
//			}
//			
//			log("OER " + messages[type] + " count = " + count);
//			logger.info("OER {} {}", messages[type], count);
//		}
//		
//		// Also show the count of modifications during the first 15 seconds of after opening bell.
//		LocalDate now = LocalDate.now();
//		LocalDateTime noZoneOpeningBell = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 9,  30, 0);
////		LocalDateTime noZoneOpeningBell = LocalDateTime.now().minusSeconds(30);	// DEBUG to show the pre-opening creation of orders
//		ZonedDateTime openingBell = noZoneOpeningBell.atZone(ZoneId.systemDefault());	// This computer/VM better be running on Eastern time like the markets.
//		
//		for (int sec = 0; sec < 15; sec++) {
//			Instant binStart = Instant.from(openingBell.plusSeconds(sec));
//			Instant binEnd = Instant.from(openingBell.plusSeconds(sec + 1));
//			
//			int count = 0;
//			for (int i = 0; i < OER.markers.size(); i++) {
//				Marker marker = OER.markers.get(i);
//				if (marker.name.equals(OER_MODIFY_ORDER)) {
//					if (marker.instant.isAfter(binStart) && marker.instant.isBefore(binEnd)) {
//						count++;
//					}
//				}
//			}
//			log("  bin " + binStart.toString() + " count = " + count);
//			logger.info("  bin {} {}", binStart.toString(), count);
//		}
//	}
	
	// -----------------------------------------------

	public boolean isConnected()
	{
		// This is a weird way to test if we are connected to the TWS instance, but the best we have publicly available.
		// NOTE code that makes calls to this class, especially in a loop, should check this first, so we don't get large
		// numbers of "not connected" errors.
		ApiConnection woof = controller.client();
		if (woof == null) {
			return false;
		}
		boolean connected = woof.isConnected();
		return connected;
	}
	
	public void connect(String host, int port, int clientID)
	{
		this.clientID = clientID;
		controller.connect(host, port, clientID, null);
	}

	public void disconnect()
	{
//		// TODO HACK on HACK
//		// When the application exits, it calls us to disconnect so we don't leave IB library threads running and hold the app open.
//		// The DataBroker has a watchdog thread too we can try to kill off here as well.
//		DataBroker.getInstance().stopWatchdog();
		
		controller.disconnect();
	}
	

	public int getClientId()
	{
		return clientID;
	}
	
	public ArrayList<String> getAccountList()
	{
		// Seems better than clone(), but we don't need deep-copy here anyway
		return new ArrayList<String>(accountList);
	}
	

	// Completely gross anonymous class crap
	volatile long timestampIBFormat;		// TWS server timestamp, expressed as seconds since 1970-01-01 GMT. 
	volatile LocalTime timestampLocalTime;
	
	// TWS gives us a timestamp that is only accurate to the SECOND, not MILLISECOND.  This is pretty sad, since we really can't
	// compare to the system clock very well with this low accuracy.
	// TODO verify that the returned long from TWS is really only to-the-second, and contains no milliseconds.
	public LocalTime getServerTimeBlocking()
	{
		timestampIBFormat = 0;
		timestampLocalTime = LocalTime.now();
	
		controller.reqCurrentTime(new ITimeHandler()
		{
			@Override
			public void currentTime(long time)
			{
				timestampIBFormat = time;
				logger.info("TWS reported server time {} GMT", Formats.fmtDate(time * 1000));	// * 1000 to convert sec to ms
			}
		});

		// Have to wait for TWS to respond, which comes in on the ApiController socket thread.
		// So we will block here (on the SWT main thread!) for just a second or so
		while (ChronoUnit.SECONDS.between(timestampLocalTime, LocalTime.now()) < 3)
		{
			// Typically see responses from TWS in 1-6 ms range, so just gonna yield() a few times instead
			// of using sleep() or a synchronized object lock.
			if (timestampIBFormat > 0) {
				break;
			}
			Thread.yield();
		}
		if (timestampIBFormat == 0) {
			log("Timedout attempting to read server time from TWS.");
			logger.error("failed to get response to current time req from TWS");
			return null;
		}
		

		LocalTime serverTime = null;
		try {
			// Convert the epoch second value from IB (GMT) to a modern java.time (local TZ).  IB provided no sub-second data.
			Instant instant = Instant.ofEpochSecond(timestampIBFormat);								// never returns null
			serverTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalTime();	// never returns null
			
		}
		catch (Exception e) {
			logger.warn("blew up computing server time", e);
		}
		
		// No need to compare these at sub-second accuracy, since TWS just reports sec data anyway.
		long secDiff = ChronoUnit.SECONDS.between(serverTime, timestampLocalTime);
		log("TWS time and local time are " + secDiff + " seconds apart.");
		log("(TWS " + Format.time(serverTime) + ", local " + Format.time(timestampLocalTime) + ")");
		
		logger.info("TWS time and local time are {} seconds apart.  TWS = {}, local = {}", secDiff, Format.time(serverTime), Format.time(timestampLocalTime));
		return serverTime;
	}
	
	public int updateAccountSummaryCacheBlocking()
	{
		int count = IBAccountSummary.updateAccountSummaryCacheBlocking(controller, 3000);	// TODO only wait a couple seconds
		return count;
	}

	public String getAccountSummaryValue(String account, AccountSummaryTag tag)
	{
		String value = IBAccountSummary.getCachedValue(account, tag);
		return value;
	}

	// Create a new untransmitted order so that the TWS GUI is forced to create the API tab.
	// The human can then set the layout of that tab and delete this junk order.
	//
	// TODO: wish TWS API provided a cleaner way to do this.
	public void forceOpenAPITab()
	{
		if (!isConnected()) {
			return;
		}
		if (accountList == null || accountList.size() == 0) {
			return;
		}
		
		// Actually make an order object
		Order order = new Order();
		// Boring settings:
		order.clientId(clientID);
		order.orderType(OrderType.LMT);
		order.auxPrice(0.00);
		order.account(accountList.get(0));	// just use the first account we found
		order.discretionaryAmt(0.00);		// defaults to MAX_VALUE sometimes and zero others, zero makes debugging easier to look at
		
		// Settings we can fiddle
		order.lmtPrice(0.01);
		order.totalQuantity(1);
		order.transmit(false);				// DO NOT SEND the order to IB

		Contract contract = new Contract();
		contract.symbol("TSLA");
		contract.secType(SecType.STK);
		contract.exchange("SMART");
		contract.currency("USD");
		
		synchronized (controller)
		{
			// No callback manager, we just make the human delete it.
			// TODO maybe we can delete the order (even tho it was never transmitted) so the human doesnt have to clean it up.
			controller.placeOrModifyOrder(contract, order, null);
		}
	}	
	
	public void preloadConID(String key, Contract contract)
	{
		// Fire off a request to IB to get the conID of this security, which we need to send orders containing it as a leg
		// TODO: not sure we need to do this for STOCK type, just OPTIONS?
		// TODO combos actually end up with a conID too, but it is a fixed "BAG" value, different for direct-routed combos tho.
		IBContractDetails.cacheConID(controller, key, contract);
	}

	class MyHandler extends TopMktDataAdapter
	{
		volatile double price = 0.00;	// -volatile-, since we update the value on the IB thread, and the robot reads the value from a worker thread
		
		// The order of the TICKS appears to be constant... BID, ASK, LAST, then a HLCO for the day so far.. CLOSE is from yesterday?
		@Override
		public void tickPrice(TickType tickType, double price, int canAutoExecute)
		{
			// TODO probably just need to show the LAST and CLOSE ticks, since those are the only ones that matter
			logger.trace("snapshot {} {}", tickType.toString(), Format.price(price));

			if (tickType == TickType.LAST) {
				this.price = price;
			}
			else if (tickType == TickType.CLOSE) {
				// If we already got a LAST, discard this CLOSE.
				// The CLOSE was back from 4:30 yesterday, so we prefer to use the pre-opening session LAST. 
				if (Calc.double_equals(this.price, 0.00)) {
					this.price = price;
				}
			}
		}
		// Could also override tickSize() if we cared about the size of the bid/ask/last.. we don't care.
		@Override
		public void tickSnapshotEnd()
		{
			// NOTE: outside RTH, this takes between 5 and 20 seconds to finish the snapshot and finally give us the "end" message.
			// NOTE: this works fine DURING RTH with paper trader, taking between 100-400ms to get 8 ticks and the end message.
			
			logger.trace("snapshot end, price = {}", Format.price(price));
		}
	}

	// This needs to work in pre-trading session, since it can get called a few seconds before opening bell.
	// TODO: 2/27/2017 reported ~257.50 (which was CLOSE from prior day?) instead of seeing the $10 drop prior to open
	public double getLastPriceBlocking(String underlyingSymbol)
	{
		Contract contract = new Contract();
		contract.symbol(underlyingSymbol);
		contract.secType(SecType.STK);
		contract.exchange("SMART");
		contract.currency("USD");
		
		MyHandler myHandler = new MyHandler();
		
		logger.debug("snapshot for {} {}", contract.secType(), contract.symbol());
		synchronized (controller)
		{
			// "snapshot" mode just sends us one set of ticks and then auto-ends, so we don't need to track and cancel it afterwards.
			// We only wait around until we get a LAST or CLOSE price anyway.
			controller.reqTopMktData(contract, "", true, myHandler);
		}
		
	    // Wait around for a few seconds, polling for completion
	    final int msTimeout = 3000;
	    final int msStep = 100;		// wait in small steps for polling, probably should be the roundtrip ping to IB servers
	    
	    LocalTime startTime = LocalTime.now();
	    while (ChronoUnit.MILLIS.between(startTime, LocalTime.now()) < msTimeout)
	    {
	    	// No answer yet, snooze
	    	try {
				Thread.sleep(msStep);
			}
	    	catch (InterruptedException e) {
	    		// We are blocking, so possible for someone to wake us up... not expected
	    		break;
			}
	    	// Any answer from the handler is fine, might be LAST or CLOSE or a live value
	    	if (myHandler.price > 0.01) {
	    		break;
	    	}
	    }
	    
	    return myHandler.price;
	}
	
	// -----------------------------------------------
	
//	
//	public void addFeed(DataBroker broker, ComboDesc comboDesc)
//	{
//		// Create an IB security to match the one sent in
//		Contract contract = createIBContractFromComboDesc(comboDesc);
//		
//		// Fire off a request to IB to get the conID of this security, which we need to send orders containing it as a leg
//		IBContractDetails.cacheConID(controller, "WOOF", contract);
//	}
//	

//	public void addFeed(DataBroker broker, Security security)
	public void addFeed(Contract contract, IBQuoteListener listener)
	{
//		// Create an IB security to match the one sent in
//		Contract contract = createIBContractFromSecurity(security);
		
//		// Fire off a request to IB to get the conID of this security, which we need to send orders containing it as a leg
//		// TODO: not sure we need to do this for STOCK type, just OPTIONS?
//		IBContractDetails.cacheConID(controller, security.getDBKey(), contract);

		// Create a manager, start up the data ticks from IB and hook them all together
		IBQuoteManager manager = new IBQuoteManager(contract.description(), listener);
		
		// All calls to the controller that may modify the orderID or requestID counters must be synchronized.
		synchronized (controller)
		{
			// req both stock and option data, even for stocks.
			controller.reqOptionMktData(contract, "", false, manager);
		}
	}
//	
//	public Contract createIBContractFromSecurity(Security security)
//	{
//		// Set up a contract for the stock
//		Contract c = new Contract();
//	
//		if (security.type == SecurityType.STOCK) {
//			c.symbol(security.underlying);
//			c.secType(SecType.STK);
//			c.exchange("SMART");
//			c.currency("USD");
//		}
//		else {
//			c.symbol(security.underlying);
//			c.secType(SecType.OPT);
//			c.exchange("SMART");					// "SMART" may return a contract details that show all the exchanges for this option? or pick BATS/PSE?
//			c.currency("USD");		
//
//			c.multiplier("100");					// 1 option is for 100 stock shares
//			String ib_expiry = convertToIBExpiry(security.option_expiry);
//			c.lastTradeDateOrContractMonth(ib_expiry);
//			c.strike(security.option_strike);
//			c.right(security.option_right == SecurityRight.CALL ? Right.Call : Right.Put);			// CALL or PUT
//		}
//		
//		return c; 
//	}
//
//	// IB wants the expiry to be in the form: "yyyyMMdd" as a string.  Crappy.
//	String convertToIBExpiry(LocalDate option_expiry)
//	{
//		String ib_expiry = option_expiry.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
//		return ib_expiry;
//	}
//
//	// -------------------------------------------------------------------------------------------
//	
//	// HISTORICAL INFO:
//	//
//	//		// Yahoo group says that all BAG combos are set to conID 28812380.
//	//		// However we have seen several others, but it appears those are symbol specific when direct-routed to an exchange
//	//		
//	//		final int smartComboConID = 28812380;
//	//
//	//		// Direct-routed combos:
//	//
//	//		final int tslaComboConID = 76980376;
//	//		final int nflxComboConID = 22542111;
//	//		final int amznComboConID = 14442738;
//	//		final int googlComboConID = 147871216;
//	
//	public Contract createIBContractFromComboDesc(SSOrderIn orderIn)
//	{	
//		String currency = "USD";
//		String exchange = orderIn.exchange;
//		String underlying = orderIn.combo.legs.get(0).underlying;
//		
//		Contract comboContract = new Contract();
//		comboContract.secType(SecType.BAG);
//		comboContract.currency(currency);
//		comboContract.exchange(exchange);		// set BAG to specific exchange, and later, all legs to the same
//		if (exchange.equals("SMART")) {
////	321 Error validating request:-'bw' : cause - The symbol or the local-symbol or the security id must be entered
//			//			comboContract.symbol("USD");			// TOTAL HACK AS PER DOCUMENTATION
//			comboContract.symbol(underlying);
//		}
//		else {
//			comboContract.symbol(underlying);		// Appears we cannot do direct-routed combos unless all the legs are using the same underlying?!
//		}
//		
//		for (int i = 0; i < orderIn.combo.legs.size(); i++) {
//			ComboLeg leg = makeComboLegFromSecurity(orderIn.combo.legs.get(i), exchange);
//			comboContract.comboLegs().add(leg);
//		}
//		
//		return comboContract;
//	}
//	
//
//	protected ComboLeg makeComboLegFromSecurity(Security security, String exchange)
//	{
//		int conID = IBContractDetails.getConIDFromCache(security.getDBKey());
//		
//		ComboLeg leg = new ComboLeg();
//		leg.conid(conID);
//		leg.ratio(1);
//		leg.action(Action.BUY);
//		leg.exchange(exchange);		// Every leg should have the same exchange as the BAG
//
//		return leg;
//	}

	// -------------------------------------------------------------------------------------------
	
	// Keep track of all the orders we create (a copy of the order as we sent it to IB)
	IBLiveOrders liveOrders = new IBLiveOrders();
	
	public int AddOrder(String callbackKey, Contract contract, Order order, IBOrderListener listener)	//DataBroker broker, SSOrder ssOrder)
	{
		// Create a manager and hook them all together
		IBOrderManager manager = new IBOrderManager(callbackKey, listener);

		// All calls to the controller that may modify the orderID or requestID counters must be synchronized.
		synchronized (controller)
		{
			controller.placeOrModifyOrder(contract, order, manager);
			// TODO set the status to PendingSubmit
		}
		markers.add(OER_CREATE_ORDER);
		
		manager.setKey(order.orderId());
		
		// The IB controller assigns an orderId when it transmits it to TWS, so we should save that off.
		logger.info("placed new order, IB OID = {}", order.orderId());
//		ssOrder.ibOrderID = ibOrder.orderId();

		// Save the order/contract pair that we sent to IB
		Integer key = order.orderId();
		liveOrders.putMyLiveOrder(key, contract, order);
		
		return order.orderId();
	}

//	static boolean didOnce = false;
//	public boolean readyToAdjustOrder(SSOrder ssOrder)
//	{
//		Integer key = ssOrder.ibOrderID;
//		LiveOrder live = getMyLiveOrder(key);
//		
//		// DEBUG we have both the original order/contract that the robot created (in the ssOrder) and the live running
//		// order/contract pair that IB returned from the OpenOrders call (in the LiveOrder).  Compare them.
//		if ((live != null) && !didOnce) {
////			SS.log("---- comparing original contract and order to live versions from IB ----");
//			
////			debugCompareContracts(ssOrder.ibContract, live.contract);
////			debugCompareOrders(ssOrder.ibOrder, live.order);
//			
////			SS.log(IBCompare.debugCompareContracts(ssOrder.ibContract, live.contract));
////			SS.log(IBCompare.debugCompareOrders(ssOrder.ibOrder, live.order));
//			
////			SS.log("----");
//			
//			didOnce = true;
//		}
//		
//		return (live != null);
//	}

	public void adjustOrder(int ibOrderID, int quantity, double price)
	{
		adjustOrder(ibOrderID, quantity, price, -1, -1);
	}

	// Either or both of the scale trader sell options can be -1 to mean "don't change"
	public void adjustOrder(int ibOrderID, int quantity, double price, int scaleInitLevelSize, int scaleAutoAdjustSeconds)
	{
		Integer key = ibOrderID;
		IBLiveOrder live = liveOrders.getMyLiveOrder(key);
		if (live == null) {
			// TRULY BAD.  The robot was supposed to call readyToAdjustOrder() first to make sure we have a copy.
			logger.error("BAD ROBOT: called adjustOrder() when no copy was available to readyToAdjustOrder(), IB OID {}", ibOrderID);
			return;
		}
		
//		logger.trace("live   = tq {}, lmt {}, sils {}", live.order.totalQuantity(), live.order.lmtPrice(), live.order.scaleInitLevelSize());
//		logger.trace("params = tq {}, lmt {}, sils {}", quantity, price, scaleInitLevelSize);
//
//		String s = IBCompare.debugCompareOrders(ssOrder.ibOrder, live.order);
//		logger.trace(s.length() == 0 ? "NO DIFF FROM LIVE ORDER" : s);

		// Modify the live order
		live.order.totalQuantity(quantity);
		// Only reset the price if it really changed, not just twiddling of -double- sub-epsilon bits
		if (!Calc.double_equals(live.order.lmtPrice(), price)) {
			live.order.lmtPrice(price);
		}
		if (scaleInitLevelSize >= 0) {
			live.order.scaleInitLevelSize(scaleInitLevelSize);
		}
		if (scaleAutoAdjustSeconds >= 0) {
			live.order.scalePriceAdjustInterval(scaleAutoAdjustSeconds);
		}
		live.order.transmit(true);
		
		
		
		// All calls to the controller that may modify the orderID or requestID counters must be synchronized.
		synchronized (controller)
		{
			controller.placeOrModifyOrder(live.contract, live.order, null);	// null manager, since we already have one watching this order from create
			// TODO set the status to ApiPending?
		}
		markers.add(OER_MODIFY_ORDER);

		String sils = (scaleInitLevelSize >= 0) ? (" sils = " + scaleInitLevelSize) : "";
		logger.info("adjusted order {} to {} @ {} {}", ibOrderID, quantity, Format.price(price), sils);
	}
	
	public void cancelOrder(int ibOrderID)
	{
		// All calls to the controller that may modify the orderID or requestID counters must be synchronized.
		synchronized (controller)
		{
			controller.cancelOrder(ibOrderID);
		}
		markers.add(OER_CANCEL_ORDER);

		logger.info("canceled order {}", ibOrderID);
	}

	// -----------------------------------------------

	// IConnectionHandler methods
	private class MyIConnectionHandler implements IConnectionHandler
	{
		// When we instantiate an ApiController, it creates 2 threads internally:
		//		(1) is a TCP/IP socket handler and marshaller thread, that never calls us.
		//		(2) is the EClient? thread, which handles all the callbacks into our code on listeners.
		//
		// So, since IB was too lazy to set the thread name, we do that here.  MGC used to just modify the
		// source code from IB to do it, but now we try to just link with IB Jar file, instead of building it.
		//
		// NOTE: I assumed connected() would be called first, but the accountList() call actually appears to
		// be called first, so added the naming code to both callbacks.
		private boolean firstCallback = true;
		private String oldName = "???";
		private final String newName = "IBAPICallback";

		@Override
		public void connected()
		{
			updateThreadName();
			
			log("connected() to IB TWS as clientID = " + clientID);
			logger.info("connected to IB TWS as client {}", clientID);
			
//			controller.reqCurrentTime( new ITimeHandler() {
//				@Override public void currentTime(long time) {
//					log( "Server date/time is " + Formats.fmtDate(time * 1000) );
//				}
//			});
			
			controller.reqBulletins(true, new IBulletinHandler() {
				@Override
				public void bulletin(int msgId, NewsType newsType, String message, String exchange)
				{
					String str = String.format("Received bulletin:  type=%s  exchange=%s", newsType, exchange);
					log(str);
					log(message);
					logger.info(str);
					logger.info(message);
				}
			});
		}
	
		@Override
		public void disconnected()
		{
			updateThreadName();

			log("disconnected()");
			logger.info("disconnected from IB TWS");
		}
	
	
		@Override
		public void accountList(ArrayList<String> list)
		{
			updateThreadName();

			log("accountList() called with " + list.size() + " accounts.");
			logger.info("TWS returned {} accounts", list.size());

			accountList.clear();
			accountList.addAll(list);
		}
	
	
		@Override
		public void error(Exception e)
		{
			updateThreadName();

			log("error() : " + e.getMessage());
			
			// TODO "TWS error Socket closed" is not an error if we requested the disconnect from our side.  Could change logger level for that.
			logger.error("TWS error {}", e.getMessage());
		}
	
	
		// I think this means a debug message, like a IB data connection going down/up
		// example:		message() : -1 2104 Market data farm connection is OK:usopt
		@Override
		public void message(int id, int errorCode, String errorMsg)
		{
			updateThreadName();

			log("message() : " + id + " " + errorCode + " " + errorMsg);
			
			// Determine the logger level of these messages... some are real errors and some are standard boring messages.
			boolean justInfo = false;
			// Check for "market data farm connection"
			if ((errorCode == 2104) && errorMsg.contains("is OK")) {
				justInfo = true;
			}
			// Check for "HMDS data farm connection"
			else if ((errorCode == 2106) && errorMsg.contains("is OK")) {
				justInfo = true;
			}
			// TODO a 502 means could-not-connect, should be ERROR level
			// TODO check IB error code table, maybe they are grouped by severity like HTTP return codes.

			String noCRLFMsg = errorMsg.replace('\n', ' ');
			if (justInfo) {
				logger.info("TWS message {} {} {}", id, errorCode, noCRLFMsg);
			}
			else {
				// TODO some could be ERROR level too
				logger.warn("TWS message {} {} {}", id, errorCode, noCRLFMsg);
			}
		}
	
		// This method allows the ApiController to send us internal error strings, rather than the other methods that
		// receive messages from the remote TWS instance.  Looking at the source code, it appears we will never get
		// called, since the situations are impossible/assert type errors.
		@Override
		public void show(String string)
		{
			log("show() : " + string);
			logger.error("TWS show {}", string);
		}

		// ----------------------------------------------------------------

		// All methods should call this first, to see if this is the first time every that we got a callback.
		// If so, overwrite the thread name (which IB didn't bother to set) to be meaningful in the logs.
		private void updateThreadName()
		{
			if (firstCallback) {
				oldName = Thread.currentThread().getName();
				Thread.currentThread().setName(newName);
				logger.debug("adjusting apicontroller thread name, from {} to {}", oldName, newName);
				firstCallback = false;
			}
		}
	}

	public void watchOpenOrders(boolean watchIBOpenOrders)
	{
		if (watchIBOpenOrders == true) {
			IBOpenOrders.requestOpenOrders(controller);
		}
	}
	public IBOpenOrder getOpenOrder(Integer key)
	{
		return IBOpenOrders.getOpenOrder(key);
	}

	// ----------------------------------------------------------------------------------
	
	public class TradeReport implements Comparable
	{
		public String key;
		public Contract contract;
		public Execution execution;
		public CommissionReport commission;

		public TradeReport(String key, Contract contract, Execution execution, CommissionReport commission)
		{
			super();
			this.key = key;
			this.contract = contract;
			this.execution = execution;
			this.commission = commission;
		}

//		// Compares the TradeReport using Time
//		@Override
//		public int compare(TradeReport o1, TradeReport o2)
//		{
//			// Time is in string, and since we assume the ~time is 9:30-9:40 AM, we just do a string compare.
//			// This will break if trading around the 12:59 to 1:00 PM time.
//			return o1.execution.time().compareTo(o2.execution.time());
//		}

		@Override
		public int compareTo(Object o)
		{
			TradeReport other = (TradeReport) o;
			return this.execution.time().compareTo(other.execution.time());
		}
	}
	
	private class MyTradeReportHandler implements ITradeReportHandler
	{
		HashMap<String, TradeReport> trades = new HashMap<String, TradeReport>(50);
		
		private volatile boolean done = false;
		public boolean done()
		{
			return done;
		}
		
		@Override
		public void tradeReport(String tradeKey, Contract contract, Execution execution)
		{
			logger.trace("" + tradeKey + " " + execution.toString());
			if (trades.containsKey(tradeKey)) {
				logger.debug("duplicate trade report for {}", tradeKey);
				return;
			}
			
			// Create a zero'd fake commissions report to start with for BAG trades
			CommissionReport commissionReport = new CommissionReport();
			commissionReport.m_commission = 0.00;
			commissionReport.m_realizedPNL = 0.00;
			trades.put(tradeKey, new TradeReport(tradeKey, contract, execution, commissionReport));
		}

		// TODO appears the commission reports come after all the trade reports have arrived.
		@Override
		public void commissionReport(String tradeKey, CommissionReport commissionReport) 
		{
			logger.trace("" + tradeKey + " " + commissionReport.toString());
			if (!trades.containsKey(tradeKey)) {
				logger.debug("missing trade report for commissions {}", tradeKey);
				return;
			}
			
			// Add this chunk of data to the existing map entry
			TradeReport match = trades.get(tradeKey);
			// Overwrite any fake initial zero'd report
			match.commission = commissionReport;
		}

		@Override
		public void tradeReportEnd()
		{
			logger.trace("tradeReportEnd() called");
			done = true;
		}

	}
	
	// Get trade reports after we are closed out for the session.
	// This is a "replacement" for the Trade Log dialog in TWS, which doesn't let us paste/filter like we might want.
	
	public HashMap<String, TradeReport> getDailyTradeReport()
	{
		// Ask for everything
		ExecutionFilter filter = new ExecutionFilter();
//		// Unfortunately, it won't let us get the previous few days... sad.
//		filter.time("20170913 09:30:00");
		
		MyTradeReportHandler handler = new MyTradeReportHandler();
		
		// All calls to the controller that may modify the orderID or requestID counters must be synchronized.
		synchronized (controller)
		{
			controller.reqExecutions(filter, handler);
		}
		
		// Blocking
		for (int i = 0; i < 10; i++) {
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
				break;
			}
			
			if (handler.done()) {
				break;
			}
		}
		
		if (!handler.done()) {
			logger.error("reqExecutions timedout waiting for end notification");
			return null;
		}
		
		return handler.trades;
	}

}

