package com.elmocity.ss.trader;

import com.elmocity.elib.util.PrefsBase;

public class RobotPrefs extends PrefsBase
{
	public static String NODE = "ROBOT";

	public RobotPrefs()
	{
		super(RobotPrefsKey.class, NODE);
	}

	public enum RobotPrefsKey
	{
		// Real user editable values.  Typically editable via GUI controls.
		
		// BUY RELATED
//		account,
		underlying_symbol,
		exchange,
		quantity,
		delay_after_open,

		chain_width,
		also_buy_stangles,
		strangle_diff,
		also_buy_wide,
		wide_diff,

		projected_price,
		max_extra_to_pay,
		intial_decrease,
		extra_decrease_for_nonleader,
		
		algo,
		base_step,
		agg_spread_percent,
		external_agg_adjustment,

		
		// SELL RELATED
		sell_order_delay,				// seconds to wait from completion of BUY until we create the SELL orders
 		sell_scale_initial_setback,
 		sell_scale_block_count,			// total number of sales desired (including initial block)
 		sell_scale_stepback,			// $0.01 stepback on each block fill, required
 		sell_scale_auto_time,			// seconds, how often to move the lmt down
 		sell_scale_auto_price,			// -$0.01, how much to move down each time, always negative

		// DEBUG RELATED
		debug_non_rth,						// fake data so we can test during non-regular-trading-hours
		debug_do_not_transmit_sell_orders,	// create sell orders, but leave them sitting in TWS untransmitted.
		
	};
	
	@Override
	public void init()
	{
		// Pre-create all the keys that the stockstrangle scoreboard robot uses
		
//		store.createPref(RobotPrefsKey.account.name(), String.class, "?", "??", "DU12345");
		store.createPref(RobotPrefsKey.underlying_symbol.name(), String.class, "?", "??", "XXXX");
		store.createPref(RobotPrefsKey.quantity.name(), Integer.class, 1, 200, 8);
		store.createPref(RobotPrefsKey.exchange.name(), String.class, "?", "??", "XXX");
		store.createPref(RobotPrefsKey.delay_after_open.name(), Integer.class, 0, 60, 2);		// in seconds

		store.createPref(RobotPrefsKey.chain_width.name(), Integer.class, 0, 5, 1);
		store.createPref(RobotPrefsKey.also_buy_stangles.name(), Boolean.class, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
		store.createPref(RobotPrefsKey.strangle_diff.name(), Double.class, 0.10, 2.50, 1.20);
		store.createPref(RobotPrefsKey.also_buy_wide.name(), Boolean.class, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
		store.createPref(RobotPrefsKey.wide_diff.name(), Double.class, 0.10, 2.50, 2.40);

		store.createPref(RobotPrefsKey.projected_price.name(), Double.class, 0.10, 40.00, 10.00);
		store.createPref(RobotPrefsKey.max_extra_to_pay.name(), Double.class, 0.15, 20.00, 0.65);
		store.createPref(RobotPrefsKey.intial_decrease.name(), Double.class, 0.00, 3.00, 0.25);
		store.createPref(RobotPrefsKey.extra_decrease_for_nonleader.name(), Double.class, 0.00, 3.00, 0.25);
		
		store.createPref(RobotPrefsKey.algo.name(), Integer.class, 0, 9, 0);		// 0 = ratchet, 1 = scoreboard, TODO need enum
		store.createPref(RobotPrefsKey.base_step.name(), Double.class, 0.01, 1.00, 0.05);
		store.createPref(RobotPrefsKey.agg_spread_percent.name(), Double.class, 0.05, 1.00, 0.65);
		store.createPref(RobotPrefsKey.external_agg_adjustment.name(), Integer.class, -9, 9, 0);

		store.createPref(RobotPrefsKey.sell_order_delay.name(), Integer.class, 5, 180, 45);					// This should change to a time like 9:33:00 TODO
		store.createPref(RobotPrefsKey.sell_scale_initial_setback.name(), Double.class, 0.01, 5.00, 0.50);
		store.createPref(RobotPrefsKey.sell_scale_block_count.name(), Integer.class, 1, 20, 4);
		store.createPref(RobotPrefsKey.sell_scale_stepback.name(), Double.class, 0.05, 5.00, 0.50);
		store.createPref(RobotPrefsKey.sell_scale_auto_time.name(), Integer.class, 0, 180, 179);				// zero means to not do the autostep
		store.createPref(RobotPrefsKey.sell_scale_auto_price.name(), Double.class, -0.20, -0.01, -0.03);		// must always be negative for sell orders


		store.createPref(RobotPrefsKey.debug_non_rth.name(), Boolean.class, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
		store.createPref(RobotPrefsKey.debug_do_not_transmit_sell_orders.name(), Boolean.class, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
	}

	// TODO need to implement the HIVE or SS folder tree at the application level somewhere... not sure what plugin yet.
	private final String prefsLocation = "C:\\StockStrangler\\prefs\\";
	@Override
	protected String makePrefsFullPath(String prefsName)
	{
		String filename = NODE + "_" + prefsName + ".properties";
		String fullPath = prefsLocation + prefsName;
//		String fullPath = FileIO.getFullPath(SSFileIO.Subfolder.pref, filename);
		return fullPath;
	}

}
