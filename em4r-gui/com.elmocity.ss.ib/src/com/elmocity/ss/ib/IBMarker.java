package com.elmocity.ss.ib;

import java.time.LocalTime;
import com.elmocity.elib.util.Format;

public class IBMarker
{
	// Interesting events that we need to monitor during trading, or summarize after trading is complete.
	//
	// (RPS) REQUESTS PER SECOND
	// IB limits us to 50 requests per second (examples are looking up contract details, adding feeds and order management).
	//
	// (OER) ORDER EFFECIENCY RATIO 
	// IB can complain if we are spamming them with order creation or order modifications that have no realistic chance of filling.
	// Since this wastes their computing resources and generates no commissions (since they never fill), they have a formula and
	// policies to stop this kind of activity.. 
	//		http://ibkb.interactivebrokers.com/node/1343
	// 		"OER   =   (Order Submissions + Order Revisions + Order Cancellations) / (Executed Orders + 1)"
	//		"An OER above 20 is generally considered excessive and indicative of inefficient order management logic."

	// These events are used to manage requests per second
	static final String RPS_REQ_CONTRACT = "ReqContract";
	static final String RPS_CREATE_FEED  = "CreateFeed";

	// These events are used to compute a rough OER, after a trading session is complete
	static final String OER_CREATE_ORDER = "CreateOrder";
	static final String OER_MODIFY_ORDER = "ModifyOrder";
	static final String OER_CANCEL_ORDER = "CancelOrder";


	public String name;
	public LocalTime localTime;

	IBMarker(String name)
	{
		this.name = name;
		this.localTime = localTime.now();
	}

	public String debugString()
	{
		return String.format("%s %s", Format.time(localTime), name);
	}
}


