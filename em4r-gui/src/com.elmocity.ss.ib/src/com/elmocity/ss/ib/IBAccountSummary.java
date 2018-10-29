package com.elmocity.ss.ib;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ib.controller.AccountSummaryTag;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.IAccountSummaryHandler;


// TODO this accountsummary interface appears to be used for constant updates (live) from TWS.
// So if we just want to get one value, we have to start it, read the values until we see the one we want, and then shut it all down.
// Have never seen an End() message come in, either naturally from TWS (like a snapshot mode) or after we have canceled the request.

// TODO seems no way to specify an account.  You have to read back the request for all accounts and toss the unneeded values.
// In the Advisor accounts, there is a way in TWS to configure named "groups" of accounts and request those separately.

public class IBAccountSummary
{
	static ApiController controller;
	static long msTimeout;
	static MyAccountSummaryHandler handler = null;
	
	static ArrayList<AccountData> m_accounts = new ArrayList<AccountData>();

	static final Logger logger = LoggerFactory.getLogger(IBAccountSummary.class);

	
	static int updateAccountSummaryCacheBlocking(ApiController controller, long msTimeout)
	{
		LocalTime startTime = LocalTime.now();
		LocalTime timeoutTime = startTime.plus(msTimeout, ChronoUnit.MILLIS);

		// ------------------------------------------------------------------
		// Fire off the request, creating a handler for the callbacks
		
		// IB allows you to cluster accounts in Advisor accounts as "groups", but no way to specify just one account. odd.
		String group = "All";
		// Could ask for just one or two tags we are interested in, but this is easy to just get everything in one shot.
		AccountSummaryTag[] tags = AccountSummaryTag.values();
		
		MyAccountSummaryHandler handler = new MyAccountSummaryHandler();
		synchronized (controller)
		{
			controller.reqAccountSummary(group, tags, handler);
		}
		logger.info("Requesting all account summary tags for all accounts.");
		
		// ------------------------------------------------------------------
		// Sit around polling for the completion of the handler

		boolean done = false;
		while (LocalTime.now().isBefore(timeoutTime))
		{
			if (handler.complete) {
				// Normal exit, TWS told the handler it had all the data it was gonna get.
				logger.info("Account summary finished reading tags.");
				done = true;
				break;
			}

			// Wait a little and keep hoping the active handler finishes
			try {
				Thread.sleep(300);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (!done) {
			logger.info("Account summary timedout reading tags.");
		}

		// ------------------------------------------------------------------
		// Stop the request (probably only needed if we timedout) and remove the handler
		int count = handler.count;
		controller.cancelAccountSummary(handler);
		return count;
	}
		
	// returns null if we didn't get the value within the timeout
	public static String getCachedValue(String account, AccountSummaryTag tag)
	{
		// Find the account
		int account_slot = findAccountSlot(account);
		if (account_slot >= 0) {
			// Read the value of this tag
			String value = m_accounts.get(account_slot).getValue(tag);
			if (value != null) {
				// Got a string result of some kind, we are done.
				logger.trace("AccountSummary {} {} = {}.", account, tag, value);
				return value;
			}
		}
			
		logger.info("failed to find cached value for {} {}", account, tag);
		return null;
	}

	
	// --------------------------------

	private static int findAccountSlot(String account)
	{
		// Find an existing accountdata object, if already have some data
		int account_slot = -1;
		for (int i = 0; i < m_accounts.size(); i++) {
			if (m_accounts.get(i).m_account.compareTo(account) == 0) {
				account_slot = i;
				break;
			}
		}
		return account_slot;
	}

	// --------------------------------

	private static class MyAccountSummaryHandler implements IAccountSummaryHandler
	{
		boolean complete = false;
		int count = 0;
		
		@Override
		public void accountSummary(String account, AccountSummaryTag tag, String value, String currency)
		{
			// DEBUG, just show one tag to see it is getting callbacks without flooding log
			if (tag == AccountSummaryTag.ExcessLiquidity) {
				logger.debug("AccountSummary: account {} tag {} = {}, {}", account, tag.name(), value, currency);
			}
	
			// Find an existing accountdata object, if already have some data
			int account_slot = findAccountSlot(account);
	
			// Create accountdata if this is the first time
			if (account_slot < 0) {
				AccountData ad = new AccountData();
				ad.m_account = new String(account);
				m_accounts.add(ad);
				account_slot = m_accounts.size() - 1;
			}
			
			// Add or update this tag/value pair as needed
			m_accounts.get(account_slot).update(tag, value);
			
			// DEBUG
			count++;
		}
		
		// HACK: Never appears to be called.  Might get called from the TWS side if it is shutting down or loses connection to IB?
		@Override
		public void accountSummaryEnd()
		{
			complete = true;
			logger.debug("AccountSummary: end message after {} tags.", count);
		}
	}
	
	// Container to hold all the tags for a single account.
	private static class AccountData
	{
		public String m_account;
		HashMap<AccountSummaryTag,String> m_map = new HashMap<AccountSummaryTag,String>();

		public void update(AccountSummaryTag tag, String value)
		{
			m_map.put(tag, value);
		}
		
		public String getValue(AccountSummaryTag tag)
		{
			Object o = m_map.get(tag);
			if (o == null || !(o instanceof String)) {
				return null;
			}
			return (String)o;
		}
	}
}
