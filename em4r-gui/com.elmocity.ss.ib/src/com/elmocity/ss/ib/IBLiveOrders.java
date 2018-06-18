package com.elmocity.ss.ib;

import java.util.HashMap;

import com.ib.client.Contract;
import com.ib.client.Order;

/*
 * Container for all the orders we have create with IB.  These are just the copies of the last version we sent to IB, not the
 * versions that we receive back from IB (which are called OpenOrders).
 * <p>
 * This is used to modify existing orders, allowing the higher level code to just send down "changes" to the order.<br>
 * Orders which have been filled or cancelled are still here for the application run.
 */
public class IBLiveOrders
{
	public class IBLiveOrder
	{
		Contract contract;
		Order order;
	
		public IBLiveOrder(Contract contract, Order order)
		{
			this.contract = contract;
			this.order = order;
		}
	}
	
	
	private HashMap<Integer, IBLiveOrder> liveOrders = new HashMap<>();
//	boolean isLiveOrderRequestRunning = false;
	
	

	public boolean readyToAdjustOrder(Integer orderKey)
	{
//		Integer key = ssOrder.ibOrderID;
		IBLiveOrder liveOrder = getMyLiveOrder(orderKey);
		
		// DEBUG we have both the original order/contract that the robot created (in the ssOrder) and the live running
		// order/contract pair that IB returned from the OpenOrders call (in the LiveOrder).  Compare them.
//		if ((live != null) && !didOnce) {
	//		SS.log("---- comparing original contract and order to live versions from IB ----");
			
	//		debugCompareContracts(ssOrder.ibContract, live.contract);
	//		debugCompareOrders(ssOrder.ibOrder, live.order);
			
	//		SS.log(IBCompare.debugCompareContracts(ssOrder.ibContract, live.contract));
	//		SS.log(IBCompare.debugCompareOrders(ssOrder.ibOrder, live.order));
			
	//		SS.log("----");
			
//			didOnce = true;
//		}
		
		return (liveOrder != null);
	}
	

	
	// -----------------------------------------------
	// Order callbacks to get the conversion from our Order/Contract info to acceptable format from IB to modify to move price.  :-(
	
	// Run the request to send us a copy of all live orders.
	// Cache those in a list (contract + order).
	// OrderHandlers can then ask us for the first copy ever seen and they can start modifying price/quantity.
	// Don't care about subsequent copies.
	//
	// The OrderHandlers cannot all do this request independently, because we would end up getting 20 full lists of 20 orders for example from TWS.
	
	
	IBLiveOrder getMyLiveOrder(Integer key)
	{
		IBLiveOrder liveOrder = liveOrders.get(key);
		return liveOrder;
	}
	
	void putMyLiveOrder(Integer key, Contract contract, Order order)
	{
		liveOrders.put(key, new IBLiveOrder(contract, order));
	}
}	
