package com.elmocity.elib.swt.table;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.RTFTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.elmocity.elib.util.Calc;



public class GenericTableViewer
{
	private Composite parent;
	private GenericTableInfoList info;
	private GenericRowList data;
	
	private Table table;
	private TableViewer tableViewer;
	private Button closeButton;
	

	// Set a GridLayout of BUTTON_GRID_COLUMNS on top container.
	// The table will span them all, but the buttons at the top/bottom will be able to choose a slot. 
	final int BUTTON_GRID_COLUMNS = 10;
//
//	// Set the table column property names
//	private final String COMPLETED_COLUMN 		= "completed";
//	private final String DESCRIPTION_COLUMN 	= "description";
//	private final String OWNER_COLUMN 			= "owner";
//	private final String PERCENT_COLUMN 		= "percent";
//
//	// Set column names
//	private String[] columnNames = new String[] { 
//			COMPLETED_COLUMN, 
//			DESCRIPTION_COLUMN,
//			OWNER_COLUMN,
//			PERCENT_COLUMN
//			};


	/**
	 * @param parent
	 */
	public GenericTableViewer(Composite parent, GenericTableInfoList info, GenericRowList data)
	{
		init(parent, info, data);
	}

	// TODO need to sort out injection, so we can reset the column info, and/or set bulk data.
	public void init(Composite parent, GenericTableInfoList info, GenericRowList data)
	{
		this.parent = parent;
		this.info = info;
		this.data = data;
	
		// -------------------
		// GUI
		
		// Create a composite to hold the children
		Composite c = new Composite(parent, SWT.BORDER);

		// Set GRID_COLUMNS on top container 
		GridLayout layout = new GridLayout(BUTTON_GRID_COLUMNS, false);
		c.setLayout(layout);

//		GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_BOTH);
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		c.setLayoutData(gridData);

		// Create the table 
		createTable(c);
		
		// Create and setup the TableViewer
		createTableViewer();
		tableViewer.setContentProvider(new GenericContentProvider());
		tableViewer.setLabelProvider(new GenericLabelProvider(info));

		GenericRowComparator comparator = new GenericRowComparator(tableViewer, info);

		// The input for the table viewer is the instance of ExampleTaskList
		tableViewer.setInput(data);

		// Add the buttons
		createButtons(c);
	}

	// TODO: hook up a close button and dispose issues
	
//		// Add a listener for the close button
//		closeButton.addSelectionListener(new SelectionAdapter() {
//       	
//			// Close the view i.e. dispose of the composite's parent
//			public void widgetSelected(SelectionEvent e) {
//				table.getParent().getParent().dispose();
//			}
//		});
		

	/**
	 * Release resources
	 */
	public void dispose()
	{
		// Tell the label provider to release its resources
		tableViewer.getLabelProvider().dispose();
	}

	/**
	 * Create the Table
	 */
	private void createTable(Composite parent)
	{
		// TODO add multiselect
		int style = SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | 
					SWT.FULL_SELECTION; // | SWT.HIDE_SELECTION;

		table = new Table(parent, style);
		
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalSpan = BUTTON_GRID_COLUMNS;	// span the entire width of the parent
		table.setLayoutData(gridData);		
					
		table.setLinesVisible(true);
		table.setHeaderVisible(true);

		// Create all the table columns and set them up from the info 
		for (int i = 0; i < info.size(); i++) {
			// Create each column
			TableColumn column = new TableColumn(table, SWT.LEFT, i);		
			column.setText(info.get(i).header);
			column.setWidth(info.get(i).width);
		}
		
		// We set the sizes of all the columns, but we need to soak up the extra space on the right edge (if any).
		// This resize listener just allocates all the extra space to the last column.  Could give a little to each col instead.
		table.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Table table = (Table)event.widget;
				int columnCount = table.getColumnCount();
				if (columnCount == 0) {
					return;
				}
				
				Rectangle area = table.getClientArea();
				int totalAreaWdith = area.width;
				int lineWidth = table.getGridLineWidth();
				int totalGridLineWidth = (columnCount - 1) * lineWidth; 
				int totalColumnWidth = 0;
				for(TableColumn column: table.getColumns()) {
					totalColumnWidth = totalColumnWidth + column.getWidth();
				}
				int diff = totalAreaWdith - (totalColumnWidth + totalGridLineWidth);

				TableColumn lastCol = table.getColumns()[columnCount - 1];

				// Check diff is valid or not. setting negative width doesn't make sense.
				lastCol.setWidth(diff + lastCol.getWidth() - 10 + columnCount);	// TODO seems each column is offbyone, so we add columnCount to "fix"

			}
		});
	}

	public void updateColumnTitle(int index, String title)
	{
		TableColumn col = table.getColumn(index);
		col.setText(title);
	}
	
	/**
	 * Create the TableViewer 
	 */
	private void createTableViewer()
	{
		tableViewer = new TableViewer(table);
		tableViewer.setUseHashlookup(true);
		
		for (int i = 0; i < table.getColumnCount(); i++) {
			TableViewerColumn woof = new TableViewerColumn(tableViewer, table.getColumn(i));
			woof.setEditingSupport(new GenericTableEditingSupport(tableViewer));
		}

// SOME SAMPLE CODE for making the cells editable
//		tableViewer.setColumnProperties(columnNames);	// used only for editors
//
//		// Create the cell editors
//		CellEditor[] editors = new CellEditor[columnNames.length];
//
//		// Column 1 : Completed (Checkbox)
//		editors[0] = new CheckboxCellEditor(table);
//
//		// Column 2 : Description (Free text)
//		TextCellEditor textEditor = new TextCellEditor(table);
//		((Text) textEditor.getControl()).setTextLimit(60);
//		editors[1] = textEditor;
//
//		// Column 3 : Owner (Combo Box) 
//		editors[2] = new ComboBoxCellEditor(table, taskList.getOwners(), SWT.READ_ONLY);
//
//		// Column 4 : Percent complete (Text with digits only)
//		textEditor = new TextCellEditor(table);
//		((Text) textEditor.getControl()).addVerifyListener(
//		
//			new VerifyListener() {
//				public void verifyText(VerifyEvent e) {
//					// Here, we could use a RegExp such as the following 
//					// if using JRE1.4 such as  e.doit = e.text.matches("[\\-0-9]*");
//					e.doit = "0123456789".indexOf(e.text) >= 0 ;
//				}
//			});
//		editors[3] = textEditor;
//
//		// Assign the cell editors to the viewer 
//		tableViewer.setCellEditors(editors);
//		// Set the cell modifier for the viewer
//		tableViewer.setCellModifier(new GenericCellModifier(this));
	}

//	/*
//	 * Close the window and dispose of resources
//	 */
//	// TODO MGC delete this crap, was for example test to close whole app
//	public void close()
//	{
//		Shell shell = table.getShell();
//
//		if (shell != null && !shell.isDisposed()) {
//			shell.dispose();
//		}
//	}


	/**
	 * InnerClass that acts as a proxy for the ExampleTaskList 
	 * providing content for the Table. It implements the ITaskListViewer 
	 * interface since it must register changeListeners with the 
	 * ExampleTaskList 
	 */
	class GenericContentProvider implements IStructuredContentProvider, IGenericRowListViewer
	{
		public void inputChanged(Viewer v, Object oldInput, Object newInput)
		{
			if (newInput != null)
				((GenericRowList) newInput).addChangeListener(this);
			if (oldInput != null)
				((GenericRowList) oldInput).removeChangeListener(this);
		}

		public void dispose()
		{
			data.removeChangeListener(this);
		}

		// Return the tasks as an array of Objects
		public Object[] getElements(Object parent)
		{
			return data.getTasks().toArray();
		}

		/* (non-Javadoc)
		 * @see ITaskListViewer#addTask(ExampleTask)
		 */
		public void addTask(GenericRow task)
		{
			tableViewer.add(task);
		}

		/* (non-Javadoc)
		 * @see ITaskListViewer#removeTask(ExampleTask)
		 */
		public void removeTask(GenericRow task)
		{
			tableViewer.remove(task);			
		}

		/* (non-Javadoc)
		 * @see ITaskListViewer#updateTask(ExampleTask)
		 */
		public void updateTask(GenericRow task)
		{
			tableViewer.update(task, null);	
		}
		
		public void refresh()
		{
			tableViewer.refresh();
		}
	}
	
	/**
	 * Add the "Add", "Delete" and "Close" buttons
	 * @param parent the parent composite
	 */
	private void createButtons(Composite parent)
	{
		//	DELETE one row
		Button delete = new Button(parent, SWT.PUSH | SWT.CENTER);
		delete.setText("Delete");
		GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gridData.widthHint = 80; 
		delete.setLayoutData(gridData); 

		delete.addSelectionListener(new SelectionAdapter() {
       	
			//	Remove the selection and refresh the view
			public void widgetSelected(SelectionEvent e) {
				GenericRow task = (GenericRow) ((IStructuredSelection) 
						tableViewer.getSelection()).getFirstElement();
				if (task != null) {
					data.removeTask(task);
				} 				
			}
		});
	
		//	DELETE ALL
		Button deleteAll = new Button(parent, SWT.PUSH | SWT.CENTER);
		deleteAll.setText("Delete All");
		gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gridData.widthHint = 80; 
		deleteAll.setLayoutData(gridData); 

		deleteAll.addSelectionListener(new SelectionAdapter() {
       	
			//	Remove all rows and refresh the view
			public void widgetSelected(SelectionEvent e) {
				if (data != null) {
					data.removeAllTasks();
				}
				
//				GenericRow task = (GenericRow) ((IStructuredSelection) 
//						tableViewer.getSelection()).getFirstElement();
//				if (task != null) {
//					data.removeTask(task);
//				} 				
			}
		});
		
		//	ADD SUMMARY ROW
		Button addSummaryRow = new Button(parent, SWT.PUSH | SWT.CENTER);
		addSummaryRow.setText("Add Summary");
		gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gridData.widthHint = 80; 
		addSummaryRow.setLayoutData(gridData); 

		addSummaryRow.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e)
			{
				ArrayList<GenericRow> rows = data.getTasks();
				if (rows.size() == 0) {
					return;
				}

				// Create a pair of arrays (len = number of columns) and init to zeros.
				int count[] = new int[info.size()];
				double sum[] = new double[info.size()];
				for (int i = 0; i < info.size(); i++) {
					count[i] = 0;
					sum[i] = 0.00;
				}

				for (int i = 0; i < rows.size(); i++) {
					for (int j = 0; j < info.size(); j++) {
						// Only process floating point columns, not names and dates
						if (info.get(j).dataType != Double.class) {
							continue;
						}
						
						// Read the value (double) from the this row/column
						Object o = ((GenericRow)(rows.get(i))).data.get(j);
						double value = (Double)o;
						
						// Add the value to the running total and count
						if (!Calc.double_equals(value, 0.00)) {
							count[j] += 1;
							sum[j] += value;
						}
					}
				}

				GenericRow summaryRow = new GenericRow();
				for (int i = 0; i < info.size(); i++) {
					Class dt = info.get(i).dataType;
					if (dt == Double.class) {
						double mean = 0.00;
						if (count[i] > 0) {
							mean = sum[i] / count[i];
						}
						summaryRow.addDouble(mean);
					}
					else if (dt == String.class) {
						summaryRow.addString("");
					}
					else if (dt == Integer.class) {
						summaryRow.addInteger(0);
					}
					else if (dt == Boolean.class) {
						summaryRow.addBoolean(false);
					}
				}
				data.addTask(summaryRow);
			}
		});

		//	COPY ALL
		Button copyAll = new Button(parent, SWT.PUSH | SWT.CENTER);
		copyAll.setText("Copy All");
		gridData = new GridData();
		gridData.widthHint = 80; 
		copyAll.setLayoutData(gridData); 

		copyAll.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Clipboard clipboard = new Clipboard(Display.getDefault());
				
				// Walk the table info to create a header row
				
				// Walk all the rows of data, creating a tab? delimited line with EOL
				
				String plainText = "Hello World";
				String rtfText = "{\\rtf1\\b StockStrangler paste: use paste of plaintext not RTF}";
				
				plainText = "";
				// Rows
				ArrayList<GenericRow> rows = data.getTasks();
				for (int r = 0; r < rows.size(); r++) {
					GenericRow row = rows.get(r);
					// Columns 
					for (int c = 0; c < info.size(); c++) {
						Object obj = row.getValue(c);

						if (c != 0) {
							plainText += ",";
						}
						plainText += "" + obj;
					}
					plainText += "\r\n";
				}
				
				// DEBUG stuff throws illegal arg exc if the string objects are null or zero-length.
				if (plainText.length() == 0) {
					plainText = "cantbezerolen";	// TODO
				}
				
				// Put the various formats into the clipboard.
				TextTransfer textTransfer = TextTransfer.getInstance();
				RTFTransfer rftTransfer = RTFTransfer.getInstance();
				clipboard.setContents(new String[]{plainText, rtfText}, new Transfer[]{textTransfer, rftTransfer});
				clipboard.dispose();
			}
		});
//		//	Create and configure the "Close" button
//		closeButton = new Button(parent, SWT.PUSH | SWT.CENTER);
//		closeButton.setText("Close");
//		gridData = new GridData (GridData.HORIZONTAL_ALIGN_END);
//		gridData.widthHint = 80; 
//		closeButton.setLayoutData(gridData); 		
	}

//	/**
//	 * Return the column names in a collection
//	 * 
//	 * @return List  containing column names
//	 */
//	public java.util.List getColumnNames() {
//		return Arrays.asList(columnNames);
//	}

	/**
	 * @return currently selected item
	 */
	public ISelection getSelection() {
		return tableViewer.getSelection();
	}

	/**
	 * Return the ExampleTaskList
	 */
	public GenericRowList getTaskList() {
		return data;	
	}

	/**
	 * Return the parent composite
	 */
	public Control getControl() {
		return table.getParent();
	}

	/**
	 * Return the 'close' Button
	 */
	public Button getCloseButton() {
		return closeButton;
	}
}