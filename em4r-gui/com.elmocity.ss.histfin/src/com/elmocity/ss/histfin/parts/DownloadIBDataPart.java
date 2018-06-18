package com.elmocity.ss.histfin.parts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swtchart.Chart;
import com.elmocity.elib.swt.DateFilterComposite;
import com.elmocity.elib.swt.DateFilterComposite.DateFilter;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.table.GenericRow;
import com.elmocity.elib.swt.table.GenericRowList;
import com.elmocity.elib.swt.table.GenericTableInfo;
import com.elmocity.elib.swt.table.GenericTableInfoList;
import com.elmocity.elib.swt.table.GenericTableViewer;
import com.elmocity.elib.ui.PartUtils;
import com.elmocity.elib.util.DateAndTime;
import com.elmocity.elib.util.Format;
import com.elmocity.ss.broker.DataBroker;
import com.elmocity.ss.database.HistQuote;
import com.elmocity.ss.database.HistQuoteDAO;
import com.elmocity.ss.fin.ExchangeDate;
import com.elmocity.ss.fin.OptionSpacing;
import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityRight;
import com.elmocity.ss.fin.Security.SecurityType;

public class DownloadIBDataPart
{
	private static final Logger logger = LoggerFactory.getLogger(HistFinStockMovePart.class);

	Text symbolText;
	DateFilterComposite dateFilterComposite;
	
	Button executeButton;
	Button clearButton;
	
	GenericRowList viewerData;
	GenericTableViewer viewer;
	Chart chart;
	
	// get UISynchronize injected as field
	@Inject UISynchronize sync;

	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 3 columns, left is input parameters, and right is chart output
		Composite c = GridHelpers.addGridPanel(parent, 3);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "Download Data", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			symbolText = GridHelpers.addEditorToGrid(inputComposite, "Symbol", "TSLA");

			dateFilterComposite = new DateFilterComposite(inputComposite);
	
			executeButton = GridHelpers.addButtonToGrid(inputComposite, "Execute");
			executeButton.addSelectionListener(new ExecuteListener());

			clearButton = GridHelpers.addButtonToGrid(inputComposite, "Clear");
		}

		// CENTER
		viewerData = new GenericRowList();
		viewer = PartUtils.makeTableComposite(c, getTableInfo(), viewerData);

		// RIGHT
		chart = PartUtils.makeChartComposite(c);
		
		clearButton.addSelectionListener(new PartUtils.ClearListener(chart));
	}
	
	@PreDestroy
	public void preDestroy() {
		
	}

	@Focus
	public void onFocus()
	{
		symbolText.setFocus();
	}
	
	public GenericTableInfoList getTableInfo()
	{
		GenericTableInfoList list = new GenericTableInfoList();

		list.add(new GenericTableInfo("Underlying", 140, String.class, null, null));
		list.add(new GenericTableInfo("Date", 90, String.class, null, null));
		list.add(new GenericTableInfo("Open", 75, Double.class, null, null));

		return list;
	}	

	// This uses the values of the input composite to generate new data series and add info to the table and to the chart.
	class ExecuteListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// Read/copy the values from widgets so we have a fixed copy if we run on a Job thread
			String symbol = symbolText.getText();
			DateFilter dateFilter = dateFilterComposite.getFilter();

			// Fix up the chart axis, title etc
			PartUtils.skinChart(chart, symbol);

			Security security = new Security(SecurityType.STOCK, symbol);
			String securityKey = security.getDBKey();
			
			ArrayList<JobParams> jobs = new ArrayList<JobParams>(10);
			
			// Loop all dates from filter.
			for (LocalDate date = dateFilter.startDate; !date.isAfter(dateFilter.endDate); date = date.plusDays(1)) {
				// Filter out any weekends and holidays in the range
				if (!ExchangeDate.isTradingDay(date)) {
					continue;
				}
				// Filter on the dotw, based on the checkboxes selected
				if (!dateFilter.dotwList.getFlag(date.getDayOfWeek())) {
					continue;
				}

				// TODO: blocking!
				JobParams params = new JobParams();
				params.jobName = "hist stock " + symbol;
				params.date = date;
				params.security = security;
//				jobs.add(params);
				DownloadJob jobx = new DownloadJob(params);
				jobx.setUser(true);
				jobx.schedule();

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				// Using the symbol and date, we read the historical quotes to find the stock price at startBar time on that day
				List<HistQuote> stockQuote = HistQuoteDAO.findByDateAndBar(securityKey, date, 1);
				if (stockQuote == null || stockQuote.size() != 1) {
					logger.debug("missing stock quote for {} {} bar {}", symbol, date, 1);
					continue;
				}
				final double startingStockPrice = stockQuote.get(0).quote_open;
				
				final double bestOpeningStrike = OptionSpacing.getNearestStrike(symbol, startingStockPrice);
				LocalDate expiry = DateAndTime.calcFridayThisWeek(date);
				double nos = OptionSpacing.getNativeOptionSpacing(symbol);
				
				for (int offset = -2; offset <= 2; offset++) {
					// Skip one of the 5 possible option strikes, depending on which side of the "best" fit we are on
					if (startingStockPrice < bestOpeningStrike) {
						if (offset == 2) {
							continue;
						}
					}
					else {
						if (offset == -2) {
							continue;
						}
					}
					
					double strike = OptionSpacing.getNearestStrike(symbol, startingStockPrice + (offset * nos));
					LocalDate xxx = date;
					long msDelay = (offset + 2) * 1000;
					
					Security call = new Security(SecurityType.OPTION, symbol, expiry, strike, SecurityRight.CALL);

					params = new JobParams();
					params.jobName = "hist call " + offset;
					params.date = date;
					params.security = call;
					jobs.add(params);

					Security put = new Security(SecurityType.OPTION, symbol, expiry, strike, SecurityRight.PUT);

					params = new JobParams();
					params.jobName = "hist put " + offset;
					params.date = date;
					params.security = put;
					jobs.add(params);
				}

				// Create and queue all the jobs, which will process in order, one at a time
				for (int i = 0; i < jobs.size(); i++) {
					DownloadJob job = new DownloadJob(jobs.get(i));
					job.setUser(true);
					job.schedule();
				}
				
			}
		}
	}
	
	// ------------------------------------------------------------------------------
	
	/*
	 * Parameters needed by a historical download job to fetch the data.
	 */
	class JobParams
	{
		String jobName;
		Security security;
		LocalDate date;
	}
	
	// Create a mutex that all the download jobs share, so that only 1 can run at a time.
	// This stops us from overrunning the IB TWS connection by spamming it with 50 downloads at once.
	class Mutex implements ISchedulingRule
	{
		public boolean isConflicting(ISchedulingRule rule)
		{
			return rule == this;
		}
		public boolean contains(ISchedulingRule rule)
		{
			return rule == this;
		}
	}
	final Mutex rule = new Mutex();		// shared by all download jobs
	
	class DownloadJob extends Job
	{
		JobParams params;
		DownloadJob(JobParams params)
		{
			super(params.jobName);
			setRule(rule);
			this.params = params;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor)
		{
			// Calm this down a little, so we don't just spam TWS, which can't handle it.
			try {
				Thread.sleep(300);
			}
			catch (InterruptedException e) {
			}
			
			boolean ok = downloadAndShow(params.security, params.date);
			return Status.OK_STATUS;
		}
	}
	
	private boolean downloadAndShow(Security security, LocalDate date)
	{
		String securityKey = security.getDBKey();

		// Pull the historical quotes for that option for the time range.
		ArrayList<HistQuote> hist = DataBroker.getInstance().downloadHistData(security, date);
		if (hist == null || hist.size() < 390) {
			logger.debug("bad hist {} {}", securityKey, date);
			return false;
		}				
		
		// Store this data in the database
		HistQuoteDAO.insert(hist);
		
		// Create some storage for the X and Y data, eventually sending a copy to the chart
		double xData[] = new double[390];
		double yData[] = new double[390];
		
		for (int bar = 0; bar <= 389; bar++) {
			xData[bar] = bar;
			yData[bar] = hist.get(bar).quote_open;
		}
		
		// Set up the series
		
		// We need a unique ID for this series, so use the symbol and date
		final String seriesID = String.format("%s %s", securityKey, Format.date(date));
		// We also need a string to show in the legend on the chart to the user.
		final String seriesLegendName = seriesID;
		
		sync.asyncExec(() -> {

			// Create the series on the chart
			PartUtils.createSeries(chart, seriesID, seriesLegendName, xData, yData, date.getDayOfWeek());
			
			// Add an info line to the table viewer too
			GenericRow row = new GenericRow();
			row.addString(securityKey);
			row.addString(date.toString());
			row.addDouble(yData[0]);
			viewerData.addTask(row);
			
			logger.debug("hist - created series and table row ok");
		});
		
		return true;
	}
}