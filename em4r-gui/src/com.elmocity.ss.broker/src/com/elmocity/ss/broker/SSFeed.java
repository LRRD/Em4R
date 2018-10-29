package com.elmocity.ss.broker;

import java.time.LocalTime;

import com.elmocity.ss.fin.Security;

public class SSFeed
{
	// Caller data
	public Security security;
	
	// Provider data
	public SSQuote quote;
	
	SSFeed(Security security)
	{
		this.security = security;
		quote = new SSQuote();
	}

}
