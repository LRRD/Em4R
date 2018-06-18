package com.elmocity.ss.ib;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ib.client.Contract;
import com.ib.client.Types.BarSize;
import com.ib.client.Types.DurationUnit;
import com.ib.client.Types.SecType;
import com.ib.client.Types.WhatToShow;
import com.ib.controller.ApiController.IHistoricalDataHandler;
import com.ib.controller.Bar;

public class IBHistoricalData
{
	ArrayList<Bar> resultBars = new ArrayList<Bar>();
	MyHistoricalDataHandler handler = new MyHistoricalDataHandler();
	
	Contract contract;
	String endDateTime;
	int duration;
	DurationUnit durationUnit;
	BarSize barSize;
	WhatToShow whatToShow;
	boolean rthOnly;
	
	public volatile boolean done = false;	// The caller thread is polling this for completion
	public String error = "";
	public String filename;

	static final Logger logger = LoggerFactory.getLogger(IBHistoricalData.class);
	
	
	public IBHistoricalData(Contract contract, LocalDate date)
	{
		this.contract = contract;
		this.endDateTime = makeIBClosingDateString(date);
		this.duration = 1;
		this.durationUnit = DurationUnit.DAY;
		this.barSize = BarSize._1_min;
		if (contract.secType() == SecType.STK) {
			this.whatToShow = WhatToShow.TRADES;
		}
		else if (contract.secType() == SecType.OPT) {
			this.whatToShow = WhatToShow.MIDPOINT;
		}
		else {
			logger.warn("unknown sectype {} {}", contract.secType(), contract.symbol());
		}
		this.rthOnly = true;
	}
	
	public void start()
	{
		// Fire off the query to the server
		done = false;
		IBDataProvider.getInstance().controller.reqHistoricalData(contract, endDateTime, duration, durationUnit, barSize, whatToShow, rthOnly, handler);
	}

	public void stop()
	{
		IBDataProvider.getInstance().controller.cancelHistoricalData(handler);
	}
	
	public ArrayList<IBHistQuote> getResults()
	{
		if (resultBars == null || resultBars.size() == 0) {
			return null;
		}
		
		ArrayList<IBHistQuote> results = new ArrayList<IBHistQuote>(390);
		
		for (int i = 0; i < resultBars.size(); i++) {
			Bar b = resultBars.get(i);
			
			// IB gives volumn in double format, I assume becuase of currency trade volume... we just truncate to long
			IBHistQuote quote = new IBHistQuote(i, b.open(), b.high(), b.low(), b.close(), (int)b.volume());
			results.add(quote);
		}
		
		logger.info("returning {} bars for contract {}", resultBars.size(), contract.description());		
		return results;
	}
	
	// -----------------------------------------------------------------------------------------------
	
	private String makeIBClosingDateString(LocalDate date)
	{
		// The request for hist data wants the END TIME of the span, not the beginning time.  very weird.
		// So we take the date from the caller, and set the time to the closing bell time of NYSE in Eastern Time = 4pm
		String s = date.format(DateTimeFormatter.ofPattern("yyyyMMdd 16:00:00"));	// "yyyyMMdd HH:mm:ss" (from sample code in TWSAPI)
		return s;
	}
	

	class MyHistoricalDataHandler implements IHistoricalDataHandler
	{
		MyHistoricalDataHandler()
		{
		}
	
//		// MGC - called to save data from the panel to a text file
//		protected void onSaveFile()
//		{
//			if (resultBars.size() <= 0) {
//				log("No data rows to save to file");
//				logger.warn("empty result set");
//				return;
//			}
//	
//			String fullSymbol = null;
//			if (contract.secType() == SecType.STK) {
//				// Base is just the stock, nothing to add
//				fullSymbol = contract.symbol();
//			}
//			else if (contract.secType() == SecType.OPT) {
//				fullSymbol = SS1_DataStore.generateOptionSymbol(contract.symbol(), contract.lastTradeDateOrContractMonth(), contract.right().toString(), contract.strike());
//			}
//			else {
//				logger.error("unknown secType {}, should have been STK or OPT", contract.secType());
//				return;
//			}
//			
//			filename = SS1_DataStore.makeFilename(fullSymbol, makeFileDateString(resultBars.get(0)), 60);	// 60 sec per bar = 1 minute bars
//			String fullpath = SSFileIO.getFullPath(Subfolder.data, filename);
//			
//			BufferedWriter output = FileIO.createFile(fullpath);
//			if (output == null) {
//				log("Failed to create file " + filename + ".  Might already exist, or other filesystem error.");
//				logger.error("failed to create file {}.  might already exist, or other filesystem error", filename);
//				return;
//			}
//			
//			for (int i = 0; i < resultBars.size(); i++) {
//				Thread.yield();				// slow this down a little
//				
//				Bar b = resultBars.get(i);
//				String s = "" + toFileString(b) + System.lineSeparator();
//				FileIO.writeFile(output, s);
//			}
//			FileIO.closeFile(output);		// this includes a flush() first
//	
//			// Everything seems to have worked
//			log("Saved to file '" + filename + "' " + resultBars.size() + " rows.");
//			logger.info("saved {} bars to file {}", resultBars.size(), filename);
//		}
//	
//		
//		
//		// Old arbitrary format of a date, used in the original disk files storing stock and option daily data.
//		// Can't change this, needs to be backward compatible with the SS1 client
//		private final SimpleDateFormat FILENAME_DATE = new SimpleDateFormat( "MMddyyyy"); // format for a filename part
//		private final SimpleDateFormat DATE_TIME = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss"); // format for display
//
//		// Convert the date into a string used in the filename
//		protected String makeFileDateString(Bar bar)
//		{
//			String ds = FILENAME_DATE.format(new Date(bar.time() * 1000));	// time() is in ms
//			return ds;
//		}
//		public String toFileString(Bar bar)
//		{
//			String date_time = DATE_TIME.format(new Date(bar.time() * 1000));
//			return String.format("%s\t%s\t%s\t%s\t%s\t%s", date_time, bar.open(), bar.high(), bar.low(), bar.close(), bar.volume());
//		}		

		
		
		@Override public void historicalData(Bar bar, boolean hasGaps)
		{
			resultBars.add(bar);
			if (resultBars.size() % 30 == 0) {
				logger.trace("recvd bar {}", bar);
			}
		}
		
		@Override public void historicalDataEnd()
		{
			logger.trace("recvd end");

			// NOTE: probably makes some send to async the file saving, since we want to flush the event queue, and the disk operation might lag the GUI.
			Thread.yield();
			
//			onSaveFile();
			
//			if (error != null && error.length() > 0) {
//				log(error);
//			}
			
			// Caller is polling us
			done = true;
		}
	
//		@Override public void realtimeBar(Bar bar) {
//			m_rows.add( bar); 
//			fire();
//		}
//		
//		private void fire() {
//			SwingUtilities.invokeLater( new Runnable() {
//				@Override public void run() {
//					m_model.fireTableRowsInserted( m_rows.size() - 1, m_rows.size() - 1);
//					m_chart.repaint();
//				}
//			});
//		}
	

	}

}
