package com.elmocity.ss.broker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.ss.broker.SSOrder.SSSide;
import com.elmocity.ss.database.HistQuote;
import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityRight;
import com.elmocity.ss.fin.Security.SecurityType;
import com.elmocity.ss.ib.IBContractDetails;
import com.elmocity.ss.ib.IBHistQuote;
import com.elmocity.ss.ib.IBHistoricalData;
import com.elmocity.ss.ib.IBOrderOut;
import com.elmocity.ss.ib.IBQuote;
import com.ib.client.ComboLeg;
import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderStatus;
import com.ib.client.OrderType;
import com.ib.client.Types.Action;
import com.ib.client.Types.Right;
import com.ib.client.Types.SecType;

/*
 * Class to house all of the conversion routines for having a DataBroker object communicate with a IBDataProvider.
 * <p>
 * This is the only place in the application that should have imports for both the SS and IB data types/interfaces.
 */
public class DataBroker_IB
{
	static final Logger logger = LoggerFactory.getLogger(DataBroker_IB.class);

	public static Contract createIBContractFromSecurity(Security security)
	{
		// Set up a contract for the stock
		Contract c = new Contract();

		if (security.type == SecurityType.STOCK) {
			c.symbol(security.underlying);
			c.secType(SecType.STK);
			c.exchange("SMART");
			c.currency("USD");
		}
		else {
			c.symbol(security.underlying);
			c.secType(SecType.OPT);
			c.exchange("SMART");					// "SMART" may return a contract details that show all the exchanges for this option? or pick BATS/PSE?
			c.currency("USD");		

			c.multiplier("100");					// 1 option is for 100 stock shares
			String ib_expiry = convertToIBExpiry(security.option_expiry);
			c.lastTradeDateOrContractMonth(ib_expiry);
			c.strike(security.option_strike);
			c.right(security.option_right == SecurityRight.CALL ? Right.Call : Right.Put);			// CALL or PUT
		}

		return c; 
	}

	// IB wants the expiry to be in the form: "yyyyMMdd" as a string.  Crappy.
	public static String convertToIBExpiry(LocalDate option_expiry)
	{
		String ib_expiry = option_expiry.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		return ib_expiry;
	}

	// -------------------------------------------------------------------------------------------

	// HISTORICAL INFO:
	//
	//		// Yahoo group says that all BAG combos are set to conID 28812380.
	//		// However we have seen several others, but it appears those are symbol specific when direct-routed to an exchange
	//		
	//		final int smartComboConID = 28812380;
	//
	//		// Direct-routed combos:
	//
	//		final int tslaComboConID = 76980376;
	//		final int nflxComboConID = 22542111;
	//		final int amznComboConID = 14442738;
	//		final int googlComboConID = 147871216;

	public static Contract createIBContractFromComboDesc(SSOrderIn orderIn)
	{	
		String currency = "USD";
		String exchange = orderIn.exchange;
		String underlying = orderIn.combo.legs.get(0).underlying;

		Contract comboContract = new Contract();
		comboContract.secType(SecType.BAG);
		comboContract.currency(currency);
		comboContract.exchange(exchange);		// set BAG to specific exchange, and later, all legs to the same
		if (exchange.equals("SMART")) {
			//	321 Error validating request:-'bw' : cause - The symbol or the local-symbol or the security id must be entered
			//			comboContract.symbol("USD");			// TOTAL HACK AS PER DOCUMENTATION
			comboContract.symbol(underlying);
		}
		else {
			comboContract.symbol(underlying);		// Appears we cannot do direct-routed combos unless all the legs are using the same underlying?!
		}

		for (int i = 0; i < orderIn.combo.legs.size(); i++) {
			ComboLeg leg = makeComboLegFromSecurity(orderIn.combo.legs.get(i), exchange);
			comboContract.comboLegs().add(leg);
		}

		return comboContract;
	}


	protected static ComboLeg makeComboLegFromSecurity(Security security, String exchange)
	{
		int conID = IBContractDetails.getConIDFromCache(security.getDBKey());

		ComboLeg leg = new ComboLeg();
		leg.conid(conID);
		leg.ratio(1);
		leg.action(Action.BUY);
		leg.exchange(exchange);		// Every leg should have the same exchange as the BAG

		return leg;
	}

	// ---------------------------------------

	public static Order makeIBOrderFromSSOrderIn(SSOrderIn orderIn, int clientID)
	{
		// Create an IB order object
		Order ibOrder = new Order();
		// Boring settings:
		if (orderIn.side == SSSide.BUY) {
			ibOrder.action(Action.BUY);
		}
		else if (orderIn.side == SSSide.SELL) {
			ibOrder.action(Action.SELL);
		}
		else {
			logger.error("Missing BUY/SELL flag in AddOrder BAILING, key = {}", orderIn.key);
			return null;
		}
		ibOrder.clientId(clientID);
		ibOrder.orderType(OrderType.LMT);
		ibOrder.auxPrice(0.00);
		ibOrder.account(orderIn.account);
		ibOrder.discretionaryAmt(0.00);	// defaults to MAX_VALUE sometimes and zero others, zero makes debugging easier to look at
		ibOrder.orderRef(orderIn.reference);

		// Native IB OCA causes issues with jammed orders and IB-side cancels, DO NOT USE

		ibOrder.lmtPrice(orderIn.price);
		ibOrder.totalQuantity(orderIn.totalQuantity);

		// scale orders
		if (orderIn.scalePriceIncrement > 0.01) {
			ibOrder.scaleInitLevelSize(orderIn.scaleInitialComponentSize);	 			// Number of contracts in the first block
			ibOrder.scaleSubsLevelSize(orderIn.scaleSubsequentComponentSize); 			// Number of contracts in each subsequent block
			ibOrder.scalePriceIncrement(orderIn.scalePriceIncrement);					// Price kickback (up) each time a block sells
			ibOrder.scalePriceAdjustInterval(orderIn.scaleAutoAdjustSeconds);			// Auto adjustment timer, in seconds (0 means don't use)
			ibOrder.scalePriceAdjustValue(orderIn.scaleAutoAdjustAmount);				// Auto adjustment pennies down each time, always NEGATIVE
		}

		// DEBUG
		if (orderIn.side == SSSide.SELL) {
			ibOrder.transmit(false);	// DEBUG just create the order in TWS but leave it unsent to IB.
		}


		return ibOrder;
	}

	// Convert from IB status to SS status
	public static SSOrderStatus makeSSOrderStatusFromIBOrderStatus(OrderStatus os)
	{
		// API 9.6, TWS 887: New Order Statuses
		//
		// When using reqAllOpenOrders() or reqOpenOrders(), such open orders will be reported by TWS using the orderStatus() callback with
		// a new status of ApiPending. You can cancel such orders using cancelOrder(). TWS reports such order cancellations using the
		// orderStatus() callback with a new status of ApiCancelled.
		// MGC: these look like hack status values so that a clientID=0 robot could cancel orders from other robots.
		// MGC: this means that clientID=0 would get a ApiCancelled, and the owner client would see a Cancelled status on same order.  Maybe.

		SSOrderStatus ssos = SSOrderStatus.UNKNOWN;

		switch (os) {
		case Unknown:
			ssos = SSOrderStatus.UNKNOWN;
			break;

		case PreSubmitted:
		case PendingSubmit:						// set by our code, not IB
		case PendingCancel:						// set by our code, not IB
		case ApiPending:
		case Inactive:
			ssos = SSOrderStatus.PENDING;
			break;

		case Submitted:
			ssos = SSOrderStatus.LIVE;
			break;

		case ApiCancelled:
		case Cancelled:
			ssos = SSOrderStatus.CANCELLED;
			break;

		case Filled:
			ssos = SSOrderStatus.FILLED;
			break;
		}

		return ssos;
	}
	
	// Convert from IB specific quote into the application quote (SS specific)
	public static SSQuote newSSQuoteFromIBQuote(IBQuote quote)
	{
		SSQuote ssQuote = new SSQuote(quote.bid, quote.ask, quote.last, quote.imp_vol, quote.halted, quote.last_update);
		return ssQuote;
	}

	// Convert from IB specific quote into the application quote (SS specific)
	public static SSOrderOut newSSOrderOutFromIBOrderOut(IBOrderOut ibOrderOut)
	{
		SSOrderStatus ssos = makeSSOrderStatusFromIBOrderStatus(ibOrderOut.status);

		SSOrderOut ssOrderOut = new SSOrderOut(ssos, ibOrderOut.filled, ibOrderOut.remaining, ibOrderOut.averageFillPrice, ibOrderOut.lastFillPrice,ibOrderOut. lastUpdateTime);
		return ssOrderOut;
	}

	public static ArrayList<HistQuote> downloadHistData(Security security, LocalDate date)
	{
		Contract contract = createIBContractFromSecurity(security);
		IBHistoricalData hist = new IBHistoricalData(contract, date);
		hist.start();
		logger.debug("sent req for {} on {} obj = {}", security.debugString(), date, hist);

		// Wait around for up to 20 seconds
		for (int i = 0; i < 20; i++) {
			if (hist.done) {
				logger.debug("done after {} for {} {}", i, security, date);
				break;
			}
			
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				break;
			}
		}
		if (!hist.done) {
			// Timedout or interrupted
			logger.debug("timedout for {} {}", security, date);
			return null;
		}
		
		ArrayList<IBHistQuote> raw = hist.getResults();
		if (raw == null || raw.size() != 390) {
			logger.debug("bad data for {} on {}", contract.description(), date);
			return null;
		}

		// Allocate return array
		ArrayList<HistQuote> results = new ArrayList<HistQuote>(390);
		
		// Copy the data from the IB-specific struct to the general SS/DB format
		String securityKey = security.getDBKey();
		
		for (int bar = 0; bar < raw.size(); bar++) {
			IBHistQuote rawQ = raw.get(bar);
			
			HistQuote q = new HistQuote(0, securityKey, date, bar,
					rawQ.quote_open, rawQ.quote_high, rawQ.quote_low, rawQ.quote_close, rawQ.quote_volume);
			results.add(q);
		}
		return results;
	}
}
