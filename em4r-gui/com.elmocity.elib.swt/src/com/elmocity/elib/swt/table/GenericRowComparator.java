package com.elmocity.elib.swt.table;

import java.time.LocalDate;
import java.util.ArrayList;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class GenericRowComparator extends ViewerComparator
{
	public static final int ASC = 1;
	public static final int NONE = 0;
	public static final int DESC = -1;

	// Saved pointers to the table and column info from constructor
	final private TableViewer viewer;
	final private GenericTableInfoList infoList;

	// Current situation of which column we are sorted by
	private int direction = 0;
	private TableColumn column = null;
	private int columnIndex = 0;
	
	final private SelectionListener selectionHandler = new SelectionAdapter()
	{
		public void widgetSelected(SelectionEvent e)
		{
			GenericRowComparator sorter = (GenericRowComparator) GenericRowComparator.this.viewer.getComparator();
			TableColumn selectedColumn = (TableColumn) e.widget;
			GenericRowComparator.this.setTheSortColumn(selectedColumn);
		}
	};

	public GenericRowComparator(TableViewer viewer, GenericTableInfoList infoList)
	{
		this.viewer = viewer;
		this.infoList = infoList;
		
		// Attach ourselves to the viewer
		viewer.setComparator(this);

		// Attach ourselves to each column header to get clicks
		for (TableColumn tableColumn : viewer.getTable().getColumns()) {
			tableColumn.addSelectionListener(selectionHandler);
		}
	}

	public void setTheSortColumn(TableColumn selectedColumn)
	{
		// Decide which sort style they want
		
		if (column == selectedColumn) {
			// If the column is already selected, toggle between ASC and DESC.
			// TODO: would be nice to have a way to clear the sorting (set back to NONE)
			switch (direction) {
			case ASC:
				direction = DESC;
				break;
			case DESC:
				direction = ASC;
				break;
			default:
				direction = ASC;
				break;
			}
		}
		else {
			// If we are selecting a new/different column to sort by, default to ASC
			this.column = selectedColumn;
			this.direction = ASC;
		}

		// Tell the underlying Table object what is going on
		Table table = viewer.getTable();
		switch (direction) {
		case ASC:
			table.setSortColumn(selectedColumn);
			table.setSortDirection(SWT.UP);
			break;
		case DESC:
			table.setSortColumn(selectedColumn);
			table.setSortDirection(SWT.DOWN);
			break;
		default:
			table.setSortColumn(null);
			table.setSortDirection(SWT.NONE);
			break;
		}

		TableColumn[] columns = table.getColumns();
		for (int i = 0; i < columns.length; i++) {
			TableColumn theColumn = columns[i];
			if (theColumn == this.column) {
				columnIndex = i;
				break;
			}
		}
//		SS.log("sorting table on columnIndex " + columnIndex + " direction " + direction);
		System.out.println("sorting table on columnIndex " + columnIndex + " direction " + direction);

		viewer.refresh();
//		viewer.setComparator(this);
	}

	@Override
	public int compare(Viewer viewer, Object e1, Object e2)
	{
		return direction * doCompare(viewer, e1, e2);
	}

	protected int doCompare(Viewer v, Object e1, Object e2)
	{
		if (columnIndex < 0 || columnIndex > infoList.size()) {
//			SS.log("table column " + columnIndex + " outside infoList size of " + infoList.size());
			return 0;
		}
		if (!(e1 instanceof GenericRow)) {
			return 0;
		}
		if (!(e2 instanceof GenericRow)) {
			return 0;
		}
		
		GenericTableInfo info = infoList.get(columnIndex);
		if (info.dataType == String.class) {
			Object o1 = ((GenericRow)e1).getValue(columnIndex);
			if (!(o1 instanceof String)) {
				return 0;
			}
			Object o2 = ((GenericRow)e2).getValue(columnIndex);
			if (!(o2 instanceof String)) {
				return 0;
			}
			return ((String)o1).compareTo((String)o2);
		}
		else if (info.dataType == Double.class) {
			Object o1 = ((GenericRow)e1).getValue(columnIndex);
			if (!(o1 instanceof Double)) {
				return 0;
			}
			Object o2 = ((GenericRow)e2).getValue(columnIndex);
			if (!(o2 instanceof Double)) {
				return 0;
			}
			return ((Double)o1).compareTo((Double)o2);
		}
		else if (info.dataType == Integer.class) {
			Object o1 = ((GenericRow)e1).getValue(columnIndex);
			if (!(o1 instanceof Integer)) {
				return 0;
			}
			Object o2 = ((GenericRow)e2).getValue(columnIndex);
			if (!(o2 instanceof Integer)) {
				return 0;
			}
			return ((Integer)o1).compareTo((Integer)o2);
		}	
		else if (info.dataType == LocalDate.class) {
			Object o1 = ((GenericRow)e1).getValue(columnIndex);
			if (!(o1 instanceof LocalDate)) {
				return 0;
			}
			Object o2 = ((GenericRow)e2).getValue(columnIndex);
			if (!(o2 instanceof LocalDate)) {
				return 0;
			}
			return ((LocalDate)o1).compareTo((LocalDate)o2);
		}	
		
		// Don't know the data type of this column, so just give up and let the default sort (TEXT sort of the column label)
		ILabelProvider labelProvider = (ILabelProvider) viewer.getLabelProvider(columnIndex);
		String t1 = labelProvider.getText(e1);
		String t2 = labelProvider.getText(e2);
		if (t1 == null) t1 = "";
		if (t2 == null) t2 = "";
		return t1.compareTo(t2);
	}
}

