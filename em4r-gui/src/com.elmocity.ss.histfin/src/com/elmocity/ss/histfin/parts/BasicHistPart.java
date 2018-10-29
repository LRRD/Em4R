
package com.elmocity.ss.histfin.parts;

import java.time.LocalDate;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.e4.ui.di.Focus;
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
import org.swtchart.ILineSeries;
import org.swtchart.ISeries.SeriesType;

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
import com.elmocity.ss.database.HistQuote;
import com.elmocity.ss.database.HistQuoteDAO;
import com.elmocity.ss.fin.ExchangeDate;
import com.elmocity.ss.fin.OptionSpacing;
import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityRight;
import com.elmocity.ss.fin.Security.SecurityType;

public class BasicHistPart
{
	private static final Logger logger = LoggerFactory.getLogger(BasicHistPart.class);

	Text symbolText;
	Text startBarText;
	Text endBarText;
	DateFilterComposite dateFilterComposite;
	
	Button executeButton;
	Button normalizeButton;
	Button clearButton;
	
	GenericRowList viewerData;
	GenericTableViewer viewer;
	Chart chart;
	

	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 3 columns, left is input parameters, and right is chart output
		Composite c = GridHelpers.addGridPanel(parent, 3);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "Example Hist Fin Part", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			symbolText = GridHelpers.addEditorToGrid(inputComposite, "Symbol", "TSLA");

			startBarText = GridHelpers.addEditorToGrid(inputComposite, "Start Bar", "1");

			endBarText = GridHelpers.addEditorToGrid(inputComposite, "End Bar", "30");

			dateFilterComposite = new DateFilterComposite(inputComposite);
	
			executeButton = GridHelpers.addButtonToGrid(inputComposite, "Execute");
			executeButton.addSelectionListener(new ExecuteListener());

			normalizeButton = GridHelpers.addButtonToGrid(inputComposite, "Normalize");

			clearButton = GridHelpers.addButtonToGrid(inputComposite, "Clear");
		}

		// CENTER
		viewerData = new GenericRowList();
		viewer = PartUtils.makeTableComposite(c, getTableInfo(), viewerData);

		// RIGHT
		chart = PartUtils.makeChartComposite(c);
		
		normalizeButton.addSelectionListener(new PartUtils.NormalizeListener(chart));
		clearButton.addSelectionListener(new PartUtils.ClearListener(chart));
	}

	@PreDestroy
	public void preDestroy()
	{
	}

	@Focus
	public void onFocus()
	{
		symbolText.setFocus();
	}

	public GenericTableInfoList getTableInfo()
	{
		GenericTableInfoList list = new GenericTableInfoList();

		list.add(new GenericTableInfo("Underlying", 80, String.class, null, null));
		list.add(new GenericTableInfo("Date", 90, String.class, null, null));
		list.add(new GenericTableInfo("Strike", 75, Double.class, null, null));
		list.add(new GenericTableInfo("Buy $", 75, Double.class, null, null));

		return list;
	}	

	// This uses the input to generate a single new data series and adds it to the chart.
	// The user can clear the entire chart using another method.
	class ExecuteListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// Read/copy the values from widgets so we have a fixed copy if we run on a Job thread
			String symbol = symbolText.getText();
			DateFilter dateFilter = dateFilterComposite.getFilter();
			final int startBar = Integer.parseInt(startBarText.getText());
			final int endBar = Integer.parseInt(endBarText.getText());

			// Fix up the chart axis, title etc
			PartUtils.skinChart(chart, symbol);

			Security security = new Security(SecurityType.STOCK, symbol);
			String securityKey = security.getDBKey();
			
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
			
				// Using the symbol and date, we read the historical quotes to find the stock price at startBar time on that day
				List<HistQuote> stockQuote = HistQuoteDAO.findByDateAndBar(securityKey, date, startBar);
				if (stockQuote == null || stockQuote.size() != 1) {
					logger.debug("missing stock quote for {} {} bar {}", symbol, date, startBar);
					continue;
				}
				final double startingStockPrice = stockQuote.get(0).quote_open;
				
				// Identify the correct combo to buy at that time,
				final double startingComboStrike = OptionSpacing.getNearestStrike(symbol, stockQuote.get(0).quote_open);
				
				LocalDate expiry = DateAndTime.calcFridayThisWeek(date);
				Security callLeg = new Security(SecurityType.OPTION, symbol, expiry, startingComboStrike, SecurityRight.CALL);	// CALL
				String callLegKey = callLeg.getDBKey();
				Security putLeg = new Security(SecurityType.OPTION, symbol, expiry, startingComboStrike, SecurityRight.PUT);	// PUT
				String putLegKey = putLeg.getDBKey();
				
				// Create some storage for the X and Y data, eventually sending a copy to the chart
				double xData[] = new double[endBar - startBar + 1];
				double yData[] = new double[endBar - startBar + 1];
				
				// Pull the historical quotes for that combo for the time range.
				boolean abort = false;
				for (int bar = startBar; bar <= endBar; bar++) {
					// Call Leg
					List<HistQuote> callQuote = HistQuoteDAO.findByDateAndBar(callLegKey, date, bar);
					if (callQuote == null || callQuote.size() != 1) {
						logger.debug("missing leg quote for {} {} bar {}", callLegKey, date, bar);
						abort = true;
						break;
					}
					double callLegPrice = callQuote.get(0).quote_open;
	
					// Call Leg
					List<HistQuote> putQuote = HistQuoteDAO.findByDateAndBar(putLegKey, date, bar);
					if (putQuote == null || putQuote.size() != 1) {
						logger.debug("missing leg quote for {} {} bar {}", putLegKey, date, bar);
						abort = true;
						break;
					}
					double putLegPrice = putQuote.get(0).quote_open;
					
					// Combine and store
					double comboPrice = callLegPrice + putLegPrice;
					
					xData[bar - startBar] = bar;
					yData[bar - startBar] = comboPrice;
				}
				if (abort) {
					continue;
				}
				
				// Set up the series
				
				// We need a unique ID for this series, so we can't just use symbol or even symbol+strike, so include date too.
				// This is the "internal" name of the series, but it does get shown on the property page under "series" where the
				// user can change the colors or line thickness etc.
				final String seriesID = String.format("%s %s %.02f", symbol, Format.date(date), startingComboStrike);

				// We also need a string to show in the legend on the chart to the user.
				// Since the color of the line kinda tells them the date (at least the dotw), omit that so the legend takes less space.
				final String seriesLegendName = String.format("%s %.02f", symbol, startingComboStrike);
				
				// Create the series on the chart
				PartUtils.createSeries(chart, seriesID, seriesLegendName, xData, yData, date.getDayOfWeek());

				
				// Add an info line to the table viewer too
				GenericRow row = new GenericRow();
				row.addString(symbol);
				row.addString(date.toString());
				row.addDouble(startingComboStrike);
				row.addDouble(yData[0]);
				viewerData.addTask(row);
			}
		}
	}
}