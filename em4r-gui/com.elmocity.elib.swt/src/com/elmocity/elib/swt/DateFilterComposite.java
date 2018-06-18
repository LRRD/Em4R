package com.elmocity.elib.swt;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

import com.elmocity.elib.util.DOTWList;
import com.elmocity.elib.util.DateAndTime;

// Controls that filter based on the date.
// Date range (this week, max etc), day of the week (one or multiple)

public class DateFilterComposite
{
	Composite parent;

	// Widgets
	public DateTime startDateWidget;
	public DateTime endDateWidget;
	public ArrayList<Button> dotwButtons = new ArrayList<Button>();

	// NOTE: the Java TextStyle.NARROW for dotw is just a single character so you can't tell "T" Tuesday from "T" Thursday.  Brilliant.
	private static final String[] LETTERS = new String[] { "M", "Tu", "W", "Th", "F" };

	// Storage for caller to read the values when done
	public static class DateFilter
	{
		public LocalDate startDate;
		public LocalDate endDate;
		public DOTWList dotwList = new DOTWList();

		public String getRangeString()
		{
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy");

			String dates = startDate.format(formatter) + " to " + endDate.format(formatter);
			String dotws = "";
			int dotwCount = 0;
			for (int i = 0; i < 5; i++) {
				DayOfWeek d = DayOfWeek.MONDAY.plus(i);
				if (dotwList.getFlag(d)) {
					//						dotws += d.getDisplayName(TextStyle.NARROW, Locale.getDefault());
					dotws += LETTERS[i];
					dotwCount++;
				}
			}
			if (dotwCount == 5) {
				// All 5 days are selected, so we dont need to tell the user, they assume that.
				dotws = "";
			}

			String s = dates;
			if (dotws.length() > 0) {
				s = s + " " + dotws;
			}
			return s;
		}

	}

	// This overall panel tries to stay as small as possible, since it doesn't know much about the
	// parent panel.  It does fill horizontally to soak up unused area.
	public DateFilterComposite(Composite parent)
	{
		this.parent = parent;

		Composite g = GridHelpers.addGroupPanel(parent, "Filter dates", 2);

		GridData gridData = new GridData(SWT.NONE);
		gridData.horizontalSpan = 2;
		gridData.horizontalAlignment = SWT.FILL;
		//		    gridData.widthHint = WUBase.gridWidthHint * 2;
		g.setLayoutData(gridData);

		//		    g.setBackground(GColor.getColorByDOTW(DayOfWeek.THURSDAY));	// DEBUG background color

		{
			// Label in the first grid column
			Label lstart = new Label(g, SWT.NONE);
			lstart.setText("Start Date");
	
			startDateWidget = new DateTime(g, SWT.DROP_DOWN);
			setWidget(startDateWidget, DateAndTime.calcMondayThisWeek(LocalDate.now()));
	
			startDateWidget.setDate(2017, 9, 23);	// DANGER month is 0 to 11
	
			GridHelpers.adjustCustomToGrid(g, lstart, startDateWidget);
		}
		
		{
			// Label in the first grid column
			Label lend = new Label(g, SWT.NONE);
			lend.setText("End Date");
	
			endDateWidget = new DateTime(g, SWT.DROP_DOWN);
			setWidget(endDateWidget, DateAndTime.calcFridayThisWeek(LocalDate.now()));
			endDateWidget.setDate(2017, 9, 23);	// DANGER month is 0 to 11

			GridHelpers.adjustCustomToGrid(g, lend, endDateWidget);
		}

		// 7 days a week
		for (DayOfWeek c : DayOfWeek.values()) {
			// No need for weekend buttons
			if ((c == DayOfWeek.SATURDAY) || (c == DayOfWeek.SUNDAY)) {
				continue;
			}

			// Label in the first grid column
			CLabel l = new CLabel(g, SWT.NONE);
			l.setText(c.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
			l.setImage(ImageCache.get(ImageCache.getImageKey(c)));

			// Checkbox in the second grid column
			Button b = new Button(g, SWT.CHECK);
			b.setSelection(true);

			// Save the enum for this button in the custom widget data field, so we can read later
			b.setData(c);
			dotwButtons.add(b);
		}

		// Button row
		Composite c = new Composite(g, SWT.NONE);
		{
			RowLayout layout = new RowLayout();
			layout.type = SWT.HORIZONTAL;
			c.setLayout(layout);

			GridData gridData2 = new GridData(SWT.NONE);
			gridData2.horizontalSpan = 2;
			gridData2.horizontalAlignment = SWT.FILL;
			c.setLayoutData(gridData2);
		}

		// Add a button to set max range of dates
		Button maxRangeButton = new Button(c, SWT.PUSH);
		maxRangeButton.setFont(FontCache.getFont(FontCache.WU_PRESET_BUTTON));
		maxRangeButton.setText("Max");
		maxRangeButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				setWidget(startDateWidget, LocalDate.of(2016, 8, 1));	// epoch of data transferred into SQL
				setWidget(endDateWidget, LocalDate.now());
			}
		});

		Button thisWeekButton = new Button(c, SWT.PUSH);
		thisWeekButton.setFont(FontCache.getFont(FontCache.WU_PRESET_BUTTON));
		thisWeekButton.setText("This Week");
		thisWeekButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				setWidget(startDateWidget, DateAndTime.calcMondayThisWeek(LocalDate.now()));
				setWidget(endDateWidget, DateAndTime.calcFridayThisWeek(LocalDate.now()));
			}
		});

		Button backOneWeekButton = new Button(c, SWT.PUSH);
		backOneWeekButton.setFont(FontCache.getFont(FontCache.WU_PRESET_BUTTON));
		backOneWeekButton.setText("-1 Week");
		backOneWeekButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				setWidget(startDateWidget, getWidget(startDateWidget).minus(7, ChronoUnit.DAYS));
				setWidget(endDateWidget, getWidget(endDateWidget).minus(7, ChronoUnit.DAYS));
			}
		});

		//			// Add a button to set all the dotw checkboxes back to "ON"
		//			Button setAllDOTW = new Button(c, SWT.PUSH);
		//			setAllDOTW.setFont(GFont.getFont(GFont.WU_PRESET_BUTTON));
		//			setAllDOTW.setText("All DotW");
		//			setAllDOTW.addListener(SWT.Selection, new Listener() {
		//				public void handleEvent(Event e) {
		//					for (int i = 0; i < dotwButtons.size(); i++) {
		//						dotwButtons.get(i).setSelection(true);
		//					}
		//				}
		//			});
	}

	
	// The widgets use some useless/messy formats (pre Java 8), so provide a getter and setter to use LocalDate (Java 8)
	public static void setWidget(DateTime widget, LocalDate date)
	{
		widget.setDate(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());	// DANGER month convert from 1-12 into 0-11
	}

	public static LocalDate getWidget(DateTime widget)
	{
		return LocalDate.of(widget.getYear(), widget.getMonth()+1, widget.getDay());
	}

//	public static Composite addGroupPanel(Composite parent, String title, int span)
//	{
//		Group c = new Group(parent, SWT.NONE);
//		{
//			GridLayout layout = new GridLayout(2, true);
//			c.setLayout(layout);
//			
//			c.setText(title);
//			c.setFont(FontCache.getFont(FontCache.WU_GROUP));
//		}
//		
//		GridData gridData = new GridData();
//		gridData.horizontalSpan = span;
//	    gridData.horizontalAlignment = SWT.FILL;
////	    gridData.widthHint = gridWidthHint * 2;		// spans 2 columns
//		c.setLayoutData(gridData);
//		
//		return c;
//	}


	// Caller is ready to run, so read all the control widgets and put the values into a struct for them to use in a work unit.
	public DateFilter getFilter()
	{
		DateFilter filter = new DateFilter();

		// Read the dates from the calendar widgets
		// DANGER: the month value is 0-11, not 1-12. WTF.
		filter.startDate = getWidget(startDateWidget);
		filter.endDate = getWidget(endDateWidget);

		// Walk the dotw checkbox buttons and read their state
		for (int i = 0; i < dotwButtons.size(); i++) {
			filter.dotwList.setFlag((DayOfWeek)(dotwButtons.get(i).getData()), dotwButtons.get(i).getSelection());
		}

		return filter;

	}
}

