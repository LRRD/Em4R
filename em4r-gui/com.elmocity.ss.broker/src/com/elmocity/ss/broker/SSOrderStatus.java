package com.elmocity.ss.broker;

public enum SSOrderStatus
{
	UNKNOWN, 		// creation/filling in the fields
	PENDING,		// sent to IB, waiting for ack, or possibly other "stuck" states - this happens each time we update the price too
	LIVE,			// a live working order, could be partially filled
	FILLED, 		// completed, filled (not partial)
	CANCELLED,		// completed, by someone cancelling it (the robot because another order filled etc)
}
