
package com.elmocity.ss.histfin.parts;

import java.time.DayOfWeek;
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
import com.elmocity.elib.util.Format;
import com.elmocity.ss.database.HistQuote;
import com.elmocity.ss.database.HistQuoteDAO;
import com.elmocity.ss.fin.ExchangeDate;
import com.elmocity.ss.fin.Security;
import com.elmocity.ss.fin.Security.SecurityType;

public class HistFinStockMovePart
{
	private static final Logger logger = LoggerFactory.getLogger(HistFinStockMovePart.class);

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
			GridHelpers.addTitlePanel(inputComposite, "Stock Move", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			symbolText = GridHelpers.addEditorToGrid(inputComposite, "Symbol", "TSLA");

			startBarText = GridHelpers.addEditorToGrid(inputComposite, "Start Bar", "1");

			endBarText = GridHelpers.addEditorToGrid(inputComposite, "End Bar", "30");

			dateFilterComposite = new DateFilterComposite(inputComposite);
	
			executeButton = GridHelpers.addButtonToGrid(inputComposite, "Execute");
			executeButton.addSelectionListener(new ExecuteListener());

			normalizeButton = GridHelpers.addButtonToGrid(inputComposite, "Normalize");
//			normalizeButton.addSelectionListener(new NormalizeListener());

			clearButton = GridHelpers.addButtonToGrid(inputComposite, "Clear");
//			clearButton.addSelectionListener(new ClearListener());
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

		list.add(new GenericTableInfo("Underlying", 80, String.class, null, null));
		list.add(new GenericTableInfo("Date", 90, String.class, null, null));
		list.add(new GenericTableInfo("Max Move", 75, Double.class, null, null));

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
				double maxMove = 0.00;
				
				// Create some storage for the X and Y data, eventually sending a copy to the chart
				double xData[] = new double[endBar - startBar + 1];
				double yData[] = new double[endBar - startBar + 1];
				
				// Pull the historical quotes for that stock for the time range.
				boolean abort = false;
				for (int bar = startBar; bar <= endBar; bar++) {
					// Stock
					stockQuote = HistQuoteDAO.findByDateAndBar(securityKey, date, bar);
					if (stockQuote == null || stockQuote.size() != 1) {
						logger.debug("missing leg quote for {} {} bar {}", securityKey, date, bar);
						abort = true;
						break;
					}
					double stockPrice = stockQuote.get(0).quote_open;
					double move = Math.abs(startingStockPrice - stockPrice);
					if (move > maxMove) {
						maxMove = move;
					}
	
					xData[bar - startBar] = bar;
					yData[bar - startBar] = stockPrice;
				}
				if (abort) {
					continue;
				}
				
				
				// Set up the series
				
				// We need a unique ID for this series, so use the symbol and date
				final String seriesID = String.format("%s %s", symbol, Format.date(date));
				// We also need a string to show in the legend on the chart to the user.
				final String seriesLegendName = seriesID;
				
				// Create the series on the chart
				PartUtils.createSeries(chart, seriesID, seriesLegendName, xData, yData, date.getDayOfWeek());

				
				// Add an info line to the table viewer too
				GenericRow row = new GenericRow();
				row.addString(symbol);
				row.addString(date.toString());
				row.addDouble(maxMove);
				viewerData.addTask(row);
			}
		}
	}
}