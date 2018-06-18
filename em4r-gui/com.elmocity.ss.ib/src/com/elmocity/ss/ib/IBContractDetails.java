package com.elmocity.ss.ib;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.IContractDetailsHandler;


// KEY values for this cache are Security keys.

public class IBContractDetails
{
	// Map: DBKey as the key (String), and conID as the values (Integer).
	//
	// Synchronized, so we can store new results from IB while the robots are reading values.
	// NOTE: no reason to ever delete an item from the cache, so code can assume that.
	static ConcurrentHashMap<String, Integer> conIDCache = new ConcurrentHashMap<String, Integer>();
	
	static final Logger logger = LoggerFactory.getLogger(IBContractDetails.class);

	
	public static void cacheConID(ApiController controller, String key, Contract c)
	{
		if (getConIDFromCache(key) != 0) {
			// already have this conid from another robot or previous run... no need to ask IB again
			return;
		}
		// TODO: could verify the contract, to make sure it is a fully specified option, so we get back exactly one response.
		// NOTE: tried this with a BAG combo contract and IB rejects the request.
		
		// SCARY DEBUG - get contractdetails for an option on specific exchange, not just the conID.
		// was debugging how to tell that AMEX has 0.05 GOOGL option ticks, not 0.01 like PSE/BOX or AMZN on AMEX.
//		if(c.getSecType().equals("OPT")) {
//			c.exchange("AMEX");
//		}
		synchronized (controller)
		{
			controller.reqContractDetails(c, new MyIContractDetailsHandler(key));
		}
		logger.trace("requested contract details for {}", key);

		// No way to know when this comes back, but it will end up in the cache list for when we create an order, we hope.
		// NOTE: a robot could pre-load all the conIDs before open by requesting them all and iterate getConIDFromCache to verify.
	}
	
	// TODO add a "test if in cache" method, so we can *not* log errors there, which would allow us to log errors here all the time
	public static int getConIDFromCache(String key)
	{
		Integer cached = conIDCache.get(key);
		if (cached == null) {
			// NOTE: this method could get called by a robot polling to see if the conID has been returned from IB, so not an error TODO
			logger.trace("missing conID in cache {}", key);
			return 0;
		}
		
		return cached.intValue();
	}
	
	// ----------------------------------------------------------------------------------------
	// This ConIDList allows the GUI (or debug code) to get a snapshot of the conID cache.
	// TODO: may need to lock the whole Map while we walk it (a callback could insert one while we walk).
	// NOTE: not allowed to have anyone ever delete an item.
	public static class ConIDEntry
	{
		public String key;
		public Integer conID;
	}
	
	public static ArrayList<ConIDEntry> getCache()
	{
		ArrayList<ConIDEntry> list = new ArrayList<ConIDEntry>();
		
		Set entries = conIDCache.entrySet();
		Iterator entriesIterator = entries.iterator();
		while (entriesIterator.hasNext()) {
			Map.Entry mapping = (Map.Entry) entriesIterator.next();

			ConIDEntry entry = new ConIDEntry();
			entry.key = (String)mapping.getKey();
			entry.conID = (Integer)mapping.getValue();
			list.add(entry);
		}
		
		return list;
	}
	
	// ----------------------------------------------------------------------------------------
	// Handler for the IB callbacks, for each conID requested by a robot
	
	private static class MyIContractDetailsHandler implements IContractDetailsHandler
	{
		String key;
		MyIContractDetailsHandler(String key)
		{
			this.key = key;
		}
		
		@Override
		public void contractDetails(ArrayList<ContractDetails> list)
		{
			if ((list == null) || (list.size() == 0)) {
				logger.warn("zero results for conID request for key {}", key);
				return;
			}
			else if (list.size() == 1) {
				ContractDetails d = list.get(0);
				conIDCache.put(key, d.conid());
				logger.trace("received conID {} for key {}", d.conid(), key);
				
				// TODO could add the exchange, mintick and price multiplier to the trace, or even dump the entire details structure
//				System.out.println(d.toString());
				
				return;
			}
			// else
			
			// If we got multiple results, it means we asked for a wildcard (like 0.00 strike) and got back
			// a list of all the options for this underlying for this expiry.
			// TODO: we could just cache them all, which would allow the robot to ask for them all in one shot.
			// TODO: if we allowed wildcards, we would have to parse results and create Security objects for each?
			logger.error("received wildcard response for contractDetails. array of {} length for key {}", list.size(), key);
		}
	}
}



