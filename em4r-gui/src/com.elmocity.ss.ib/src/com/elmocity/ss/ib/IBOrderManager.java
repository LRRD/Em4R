package com.elmocity.ss.ib;

import java.security.Provider;
import java.time.LocalTime;

import org.omg.CORBA.UNKNOWN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.util.Calc;
import com.elmocity.elib.util.Format;
import com.elmocity.elib.util.LoggerUtils;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;
import com.ib.client.Types.MktDataType;
import com.ib.controller.ApiController.IOrderHandler;

//import IB.IBDataProvider.LiveOrder;
//import db.Security;
//import trader.DataBroker;
//import trader.Quote;
//import trader.SSOrder;
//import trader.SSOrderOut;
//import trader.SSOrderStatus;
//import util.Helpers;
//import util.SS;
//import util.SSDateTime;
//import util.SSLogger;

public class IBOrderManager implements IOrderHandler
{
	private final String key;
	private final IBOrderListener listener;

	// Structure we keep up-to-date from all the callbacks, and push to the FeedUpdater constantly.
	IBOrderOut orderOut = new IBOrderOut();
//	int ibOrderID = 0;
//	LiveOrder liveOrder = null;		// copy of our contract/order returned from IB
	
	final static Logger logger = LoggerFactory.getLogger(IBOrderManager.class);
	
	
	IBOrderManager(String key, IBOrderListener listener)
	{
		this.key = key;
		this.listener = listener;
	}

//	// Called immediately after the provider transmits the order (and therefore gets the ibOrderID for the first time)
//	public void setIBOrderID(int ibOrderID)
//	{
//		this.ibOrderID = ibOrderID;
//	}
	
//	String logHead()
//	{
//		return "" + SSDateTime.getLogTime() + " OM " + key + " ";
//	}
	
	// Set the timestamp to "now", and push the data up to the broker
	void doUpdate(boolean alert)
	{
//		// HACK copy the pointers from the old orderOut for the ibContract and ibOrder.  TODO DEBUG
//		SSOrder temp = broker.getOrder(key);
//		if (temp != null) {
//			orderOut.ibContract = temp.orderOut.ibContract;
//			orderOut.ibOrder = temp.orderOut.ibOrder;
//		}
		orderOut.lastUpdateTime = LocalTime.now();
		if (listener != null) {
			listener.update(key, orderOut);
		}
		
		// If interesting change, wake the robot up to process immediately
		if (true) {
			listener.alert();
		}
	}
	
//	// Convert from IB status to SS status
//	void updateStatus(OrderStatus os)
//	{
//		// API 9.6, TWS 887: New Order Statuses
//		//
//		// When using reqAllOpenOrders() or reqOpenOrders(), such open orders will be reported by TWS using the orderStatus() callback with
//		// a new status of ApiPending. You can cancel such orders using cancelOrder(). TWS reports such order cancellations using the
//		// orderStatus() callback with a new status of ApiCancelled.
//		// MGC: these look like hack status values so that a clientID=0 robot could cancel orders from other robots.
//		// MGC: this means that clientID=0 would get a ApiCancelled, and the owner client would see a Cancelled status on same order.  Maybe.
//		
//		SSOrderStatus ssos = SSOrderStatus.UNKNOWN;
//		
//		switch (os) {
//		case Unknown:
//			ssos = SSOrderStatus.UNKNOWN;
//			break;
//
//		case PreSubmitted:
//		case PendingSubmit:						// set by our code, not IB
//		case PendingCancel:						// set by our code, not IB
//		case ApiPending:
//		case Inactive:
//			ssos = SSOrderStatus.PENDING;
//			break;
//			
//		case Submitted:
//			ssos = SSOrderStatus.LIVE;
//			break;
//
//		case ApiCancelled:
//		case Cancelled:
//			ssos = SSOrderStatus.CANCELLED;
//			break;
//			
//		case Filled:
//			ssos = SSOrderStatus.FILLED;
//			break;
//		}
//		
//		orderOut.status = ssos;
//		// Don't do the update here, the caller has to, since several different callbacks update the status :-(
//	}
	
	@Override
	public void orderState(OrderState orderState)
	{
		LoggerUtils.setOrder(key);

		logger.trace("orderState  {}: now {}", key, orderState.getStatus());	// getStatus() returns the String name of the enum
		
		orderOut.status = orderState.status();
		boolean alert = (orderOut.status == OrderStatus.Filled) || (orderOut.status == OrderStatus.Cancelled);
		if (alert) {
			logger.debug("alerting, IB status = {}", orderOut.status);
		}
		doUpdate(alert);
		
		// DEBUG show any warning text
		if (orderState.warningText() != null) {
			logger.trace("  warning text = {}", orderState.warningText());
		}
		
		LoggerUtils.clear(); 
	}

	@Override
	public void orderStatus(OrderStatus status, double filled, double remaining, double avgFillPrice, long permId,
			int parentId, double lastFillPrice, int clientId, String whyHeld)
	{
		// DANGER: the IB API code checked to see if an orderID is on our list, but not that the clientID matches!
		// This means we can actually get called here for status changes on orders that were created in other robots or
		// potentially even in the TWS GUI.  Most "stray" orders seem to have the clientID = 0, which we never use for
		// a robot, so just test the clientID and ignore this message if we didn't create this order.
		if (clientId != IBDataProvider.getInstance().clientID) {
			logger.trace("ignoring orderStatus callback from clientId {}, not our order", clientId);
			return;
		}

		LoggerUtils.setOrder(key);
		
		// Check to see if the incoming "filled" value is different from the last one we saw for this order, before we overwrite it
		boolean changeInFill = (orderOut.filled != (int)filled);

		orderOut.status = status;
		orderOut.filled = (int)filled;
		orderOut.remaining = (int)remaining;
		orderOut.averageFillPrice = Calc.round_to_penny(avgFillPrice);
		orderOut.lastFillPrice = Calc.round_to_penny(lastFillPrice);

		boolean alert = (orderOut.status == OrderStatus.Filled) || (orderOut.status == OrderStatus.Cancelled) || (changeInFill); 
		if (alert) {
			logger.debug("alerting, IB status = {}, changeInFill = {}", orderOut.status, changeInFill);
		}
		doUpdate(alert);
		
		// Walt had a sell trade where he got a fill that was greater than his active limit price.  Very weird, may have
		// been an in-house IB match to a market order.  Or just a bug in Walt's code.
		if (changeInFill) {
			logger.trace("orderStatus {}: status {}, filled {}, remaining {}, lastFill {}, avgFill {}",
					key, status.toString(), (int)filled, (int)remaining, Format.price(lastFillPrice) , Format.price(avgFillPrice));
			
			// NOTE When using a scaletrader SELL order, this can get called with a FILL status, but the lastFill and avgFill will be zeros.  Broken on IB side?
			//logger.trace("orderStatus {}: raw: lastFill {}, avgFill {}", key, lastFillPrice, avgFillPrice);
		}

		LoggerUtils.clear();
	}

	// https://www.interactivebrokers.com/en/software/api/apiguide/tables/api_message_codes.htm
	@Override
	public void handle(int errorCode, String errorMsg)
	{
		boolean quack = false;

		
		LoggerUtils.setOrder(key);

		switch (errorCode) {
		case 100:	// max message per second too high!
			quack = true;
			break;
		case 105:	// order modify does not match original order!
			quack = true;
			break;
//		// We get 202 messages as we cancel the orders, 200 series is just informational i guess
		// EXAMPLE pressing CANCEL button in TWS on our order gives "202 Order Canceled - reason:" (reason always seems to be blank)
//		case 202: 	// order cancelled (this happens alot, from IB side of OCA, or us spamming cancel at the end)
//			quack = true;
//			break;
		
		// TODO Appears to get errorCode -1 when some data farms are up/down, should suppress that
		//   "-1 2104 Market data farm connection is OK:usopt.us"  in non-RTH papertrader... not sure if in live system

		// case 399:
		// EXAMPLE "Warning: your order will not be placed at the exchange until 2017-02-07 09:30:00 US/Eastern"
		// This is just a warning about not being in RTH, but its a ERROR 300 series, odd.
		}

		if (quack) {
			// Only quack once every few seconds
//			long now = new Date().getTime();
//			if (now - m_last_quack > 5000) {
//				SSAudio.startSound(SSAudioFile.duck_quack);
//				m_last_quack = now;
//			}
		}
		
		// TODO we should probably alert() the robot to wake us and check on things early.
		
		// Need to strip /r/n from message, since some are multi-lined
		logger.error("handle {}: {} {}", key , errorCode, errorMsg.replace('\n',  ' '));
		
		LoggerUtils.clear();
	}

	// DEBUG - this handler doesn't really care what the IB level library thinks this orderID is, since we should only be
	// getting messages from our own order in the first place.
	int ibOrderID = 0;
	public void setKey(int ibOrderID)
	{
		this.ibOrderID = ibOrderID;
	}
}