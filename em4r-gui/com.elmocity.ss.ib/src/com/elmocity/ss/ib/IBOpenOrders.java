package com.elmocity.ss.ib;

import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.ILiveOrderHandler;


// KEY values for this cache are the ibOrderIds

public class IBOpenOrders
{
	private static HashMap<Integer, IBOpenOrder> openOrders = new HashMap<>();
	private static boolean isOpenOrderRequestRunning = false;

	
	static final Logger logger = LoggerFactory.getLogger(IBOpenOrders.class);

	
	public static void requestOpenOrders(ApiController controller)
	{
		if (isOpenOrderRequestRunning) {
			logger.debug("request for openorders while another one running already, ignoring.");
			return;
		}
		isOpenOrderRequestRunning = true;
		
		// Create a manager to absorb the callbacks
		IBOpenOrdersHandler manager = new IBOpenOrdersHandler();
		
		// All calls to the controller that may modify the orderID or requestID counters must be synchronized.
		synchronized (controller)
		{
			// NOTE: this is not the reqOpenOrders() or reqAllOpenOrders() as documented
			controller.takeTwsOrders(manager);
		}
		logger.trace("requested takeTwsOrders");
	}
	
	/**
	 * Ask for the copy of the live order from the cache, can return null if the order is missing/completed.
	 * <p>
	 * Key value is the ibOrdersId.
	 */
	public static IBOpenOrder getOpenOrder(Integer key)
	{
		IBOpenOrder openOrder = openOrders.get(key);
		// TODO Clone the wrapper object?
		if (openOrder != null) {
			openOrder = new IBOpenOrder(openOrder.contract, openOrder.order);
		}
		return openOrder;
	}
		

	
	// ----------------------------------------------------------------------------------------
	// Handler for the IB callbacks
	
	private static class IBOpenOrdersHandler implements ILiveOrderHandler
	{
		@Override
		public void openOrder(Contract contract, Order order, OrderState orderState)
		{
			Integer key = order.orderId();
			if (key == null || key == 0) {
				logger.warn("openorder callback got bad orderId, ignoring.");
				return;
			}
			
			// Discard orders that are for/from other IB TWS clients, which happens if two applications are running on one or two TWS instances.
			final int myClientID = IBDataProvider.getInstance().clientID;
			if (order.clientId() != myClientID) {
				logger.warn("we (client {}) see open orders from other robots (client {})", myClientID, order.clientId());
				return;
			}
			
//			logger.trace("openOrder {} handed in objects {} and {}", key, contract.hashCode(), order.hashCode());
//			IBOpenOrder oldLiveOrder = openOrders.get(key);
//			if (oldLiveOrder == null) {
//				logger.trace("recvd openOrder, oldLiveOrder was null!");
//			}
//			else {
//				logger.trace("recvd openOrder, diff: ");
//				logger.trace(IBCompare.debugCompareContracts(oldLiveOrder.contract, contract));
//				logger.trace(IBCompare.debugCompareOrders(oldLiveOrder.order, order));
//				int j = 9;
//			}
			
			// This overwrites any existing match for this key, silently.
			openOrders.put(key, new IBOpenOrder(contract, order));
			
			logger.trace("received openorder for key {}", key);
			return;
		}

		@Override
		public void openOrderEnd()
		{
			logger.trace("received openorderend, cache now contains {} orders", openOrders.size());
			isOpenOrderRequestRunning = false;
		}

		@Override
		public void orderStatus(int orderId, OrderStatus status, double filled, double remaining, double avgFillPrice,
				long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void handle(int orderId, int errorCode, String errorMsg) {
			// TODO Auto-generated method stub
			
		}
	}


	// -----------------------------------------------
	// Order callbacks to get the conversion from our Order/Contract info to acceptable format from IB to modify to move price.  :-(
	
	// Run the request to send us a copy of all live orders.
	// Cache those in a list (contract + order).
	// OrderHandlers can then ask us for the first copy ever seen and they can start modifying price/quantity.
	// Don't care about subsequent copies.
	//
	// The OrderHandlers cannot all do this request independently, because we would end up getting 20 full lists of 20 orders for example from TWS.
	
	public static class IBOpenOrder
	{
		public Contract contract;
		public Order order;

		public IBOpenOrder(Contract contract, Order order)
		{
			// Could do a deep copy? eek
			this.contract = contract;
			this.order = order;
		}
		
		public double getLimitPrice()
		{
			return order.lmtPrice();
		}
	}


}


