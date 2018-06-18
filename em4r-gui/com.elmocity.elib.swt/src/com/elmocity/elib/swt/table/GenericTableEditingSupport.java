package com.elmocity.elib.swt.table;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;

public class GenericTableEditingSupport extends EditingSupport
{
	private final TableViewer viewer;
	private final TextCellEditor textEditor;
//	private final ComboBoxCellEditor accountEditor;
//
//	private String[] accountList = { "woof", "splort"};
	
	public GenericTableEditingSupport(TableViewer viewer)
	{
		super(viewer);
		this.viewer = viewer;
		
		textEditor = new TextCellEditor(viewer.getTable());
//		accountEditor = new ComboBoxCellEditor(viewer.getTable(), accountList);
	}

	@Override
	protected CellEditor getCellEditor(Object element)
	{
//		if (viewer.getTable().getColumnCount() == 2) {
//			if (element instanceof GenericRow) {
//				GenericRow row = (GenericRow) element;
//				if (row.getValue(0) == "account") {
//					return accountEditor;
//				}
//			}
//		
//		}
		return textEditor;
	}

	@Override
	protected boolean canEdit(Object element)
	{
		return true;
	}

	@Override
	protected Object getValue(Object element)
	{
		if (element instanceof GenericRow) {
			GenericRow row = (GenericRow) element;
			return row.getValue(1);
		}
		return null;
		
//		return ((Person) element).getFirstName();
	}

	@Override
	protected void setValue(Object element, Object userInputValue)
	{
		// TODO if there is any sort of validation error related to the value, then we can probably get a null value sent
		// in here (as the userInputValue?) and need to do something reasonable with that situation.
		if (element instanceof GenericRow) {
			GenericRow row = (GenericRow) element;
			row.setValue(1, userInputValue);
		}
		viewer.update(element, null);
	}
}