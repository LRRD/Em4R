package com.elmocity.ss.ib;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.util.Calc;
import com.ib.client.TickType;
import com.ib.client.Types.MktDataType;
import com.ib.controller.ApiController.IOptHandler;


/**
 * Class to handle a number of tick callbacks for a data feed, and be a container for the results.
 * <p>
 * The caller could assign this object to a SNAPSHOT data feed, where we wait until "done", or
 * to a REALTIME data feed, where we run forever getting ticks randomly as they occur.
 */
public class IBQuoteManager implements IOptHandler // which extends TopMktDataAdapter
{
	String debugName;
	IBQuoteListener listener;
	
	// Structure we keep up-to-date from all the callbacks, and push up to the listener as needed.
	IBQuote quote = new IBQuote();
	
	// MarketDataType can be FROZEN or REALTIME or UNKNONW, this changes as the markets open or close.	// TODO doesn't work?
	MktDataType m_market_data_type = MktDataType.Unknown;
	
	// TODO: should be read from preferences or toggle dynamically somehow.
	// TODO: for speed, final static for now
	private final static boolean debugShowAllTicks = true;
	
	private final static Logger logger = LoggerFactory.getLogger(IBQuoteManager.class);
	
	
	IBQuoteManager(String debugName, IBQuoteListener listener)
	{
		this.listener = listener;
		this.debugName = debugName;
	}

	/**
	 * Send the data up to the DataBroker and optionally wake up listeners that something interesting happened.
	 */
	void doUpdate(boolean alert)
	{
		// Push the new data up first
		quote.last_update = LocalTime.now();
//		broker.updateFeed(security, quote);
		if (listener != null) {
			listener.update(quote);
		}
		
//		// If interesting change, wake any sleeping robots up to process immediately
//		
//		// TODO this is broken now that all the robots use the same DataBroker object.  They used to have one per robot, so waking always
//		// woke the correct robot.  Now I think the wake just wakes up a random robot, even tho this update is meaningless to the other bots.
//		if (alert) {
//			broker.wakeSleepLock();
//		}
	}
	
	// -------------------------------
	
	@Override public void tickPrice(TickType tickType, double price, int canAutoExecute)
	{
		if (debugShowAllTicks) { logger.trace("  got " + tickType.toString() + " " + debugName); }

		boolean needUpdate = false;
		switch( tickType) {
		case BID:
			quote.bid = price;
			needUpdate = true;
			break;
		case ASK:
			quote.ask = price;
			needUpdate = true;
			break;
		case LAST:
			// "LAST" is always the best we get, so overwrite whatever value is sitting there (see "CLOSE")
			quote.last = price;
			needUpdate = true;
			break;
		case CLOSE:
			// During NONRTH, there is no "LAST", but we do get a "CLOSE" value from the previous day, so use
			// this value if we never got anything better like LAST (see "LAST");
			if (Calc.double_equals(quote.last, 0.00)) {
				quote.last = price;
				needUpdate = true;
			}
			break;
		case HALTED:
			// PRICE (double) comes in as one of these:  0 = not halted, 1 = midday halt, 2 = volatility halt, 3+ = future reasons??
			quote.halted = false;
			if (price > 0.50) {
				quote.halted = true;
				logger.warn("HALTED " + debugName);	// TODO quack
			}
			needUpdate = true;
			break;
		default:
//			System.out.println("missing case for double " + tickType.toString());
			break;
		}
		
		if (needUpdate) {
			doUpdate(false);
		}
	}

	@Override public void tickSize(TickType tickType, int size)
	{
	}
	
	@Override public void tickString(TickType tickType, String value)
	{
	}
	
	@Override public void marketDataType(MktDataType marketDataType)
	{
		// This would get called when switching from FROZEN to REALTIME data, which can happen automatically at opening and closing bells
		m_market_data_type = marketDataType;

		logger.trace("  got " + marketDataType.toString() + " " + debugName);
	}
	
	// Should never get called, since we never use snapshot mode
	@Override public void tickSnapshotEnd()
	{
		logger.trace("  got " + "snapshotend" + " " + debugName);
	}


	private double m_last_bid_impvol = 0.0;
	private double m_last_ask_impvol = 0.0;
	
	@Override public void tickOptionComputation(TickType tickType, double impliedVol, double delta, double optPrice,
			double pvDividend, double gamma, double vega, double theta, double undPrice)
	{
		if (debugShowAllTicks) { logger.trace("  got tick {}", tickType.toString()); }
		boolean needUpdate = false;
		
		switch (tickType) {
		case BID_OPTION:
			// Adjust this to be daily percent, like "0.0350" = 3.50%
			if (impliedVol < 0.001 || impliedVol == Double.MAX_VALUE) {
				m_last_bid_impvol = 0.00;
				break;
			}
			m_last_bid_impvol = impliedVol / Math.sqrt(252);
			needUpdate = true;
			break;
		case ASK_OPTION:
			if (impliedVol < 0.001 || impliedVol == Double.MAX_VALUE) {
				m_last_ask_impvol = 0.00;
				break;
			}
			m_last_ask_impvol = impliedVol / Math.sqrt(252);
			needUpdate = true;
			break;
		default:
//			logger.warn("missing handler for option tick {}", tickType.toString());
			break;
		}

		// Extra check to make sure we have values for both sides.
		// At startup, we obviously get one of the sides first, but the other is still zero.
		if (needUpdate) {
			if ((m_last_bid_impvol > 0.001) && (m_last_ask_impvol > 0.001)) {
				// Average the two together
				quote.imp_vol = (m_last_bid_impvol + m_last_ask_impvol) / 2;
				needUpdate = true;		// redundant, already set
			}
			else {
				needUpdate = false;		// un-set the flag, since we are missing one side still
			}
		}
		
		if (needUpdate) {
			doUpdate(false);
		}
	}


	String debugString()
	{
// STK returns this:
//
// got BID
// got BID_SIZE
// got ASK
// got ASK_SIZE
// got LAST
// got LAST_SIZE
// got VOLUME
// got HIGH
// got LOW
// got CLOSE
// got OPEN
// got LAST_TIMESTAMP
// got HALTED
// got snapshotend
// 
// COMBOs return this:
//
// got BID
// got BID_SIZE
// got ASK
// got ASK_SIZE
// got HIGH
// got LOW
// got CLOSE
// got LAST_TIMESTAMP
// got HALTED
// got snapshotend

		String result = "" + debugName;
		
//		String bidAsk = String.format("[(%d) %.02f / %.02f (%d)]", m_bidSize, m_bid, m_ask, m_askSize);
//		String lastClose = String.format("[%b %.02f %.02f]", m_halted, m_last, m_close);
//				
//		result += m_description + " " + debugTime + " " + bidAsk + " " + lastClose;
//		SS.log(result);
		return result;
		
	}

}