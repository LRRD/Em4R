/**
 * (c) Copyright Mirasol Op'nWorks Inc. 2002, 2003. 
 * http://www.opnworks.com
 * Created on Apr 2, 2003 by lgauthier@opnworks.com
 * 
 */

package com.elmocity.elib.swt.table;

import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.swt.widgets.TableItem;

/**
 * This class implements an ICellModifier
 * An ICellModifier is called when the user modifes a cell in the 
 * tableViewer
 */

public class GenericCellModifier implements ICellModifier
{
	private GenericTableViewer viewer;
	private String[] columnNames;
	
	/**
	 * Constructor 
	 * @param TableViewerExample an instance of a TableViewerExample 
	 */
	public GenericCellModifier(GenericTableViewer viewer) {
		super();
		this.viewer = viewer;
	}

	/**
	 * @see org.eclipse.jface.viewers.ICellModifier#canModify(java.lang.Object, java.lang.String)
	 */
	public boolean canModify(Object element, String property) {
		return true;
	}

	/**
	 * @see org.eclipse.jface.viewers.ICellModifier#getValue(java.lang.Object, java.lang.String)
	 */
	public Object getValue(Object element, String property) {

//		// Find the index of the column
//		int columnIndex = viewer.getColumnNames().indexOf(property);

		Object result = null;
		GenericRow task = (GenericRow) element;

//		switch (columnIndex) {
//			case 0 : // COMPLETED_COLUMN 
//				result = new Boolean(task.isCompleted());
//				break;
//			case 1 : // DESCRIPTION_COLUMN 
//				result = task.getDescription();
//				break;
//			case 2 : // OWNER_COLUMN 
//				String stringValue = task.getOwner();
//				String[] choices = viewer.getChoices(property);
//				int i = choices.length - 1;
//				while (!stringValue.equals(choices[i]) && i > 0)
//					--i;
//				result = new Integer(i);					
//				break;
//			case 3 : // PERCENT_COLUMN 
//				result = task.getPercentComplete() + "";
//				break;
//			default :
//				result = "";
//				break;
//		}
		
		return result;	
	}

	/**
	 * @see org.eclipse.jface.viewers.ICellModifier#modify(java.lang.Object, java.lang.String, java.lang.Object)
	 */
	public void modify(Object element, String property, Object value) {	

//		// Find the index of the column 
//		int columnIndex	= viewer.getColumnNames().indexOf(property);
			
		TableItem item = (TableItem) element;
		GenericRow task = (GenericRow) item.getData();
		String valueString;

//		switch (columnIndex) {
//		case 0 : // COMPLETED_COLUMN 
//		    task.setCompleted(((Boolean) value).booleanValue());
//			break;
//		case 1 : // DESCRIPTION_COLUMN 
//			valueString = ((String) value).trim();
//			task.setDescription(valueString);
//			break;
//		case 2 : // OWNER_COLUMN 
//			valueString = viewer.getChoices(property)[((Integer) value).intValue()].trim();
//			if (!task.getOwner().equals(valueString)) {
//				task.setOwner(valueString);
//			}
//			break;
//		case 3 : // PERCENT_COLUMN
//			valueString = ((String) value).trim();
//			if (valueString.length() == 0)
//				valueString = "0";
//			task.setPercentComplete(Integer.parseInt(valueString));
//			break;
//		default :
//			break;
//		}
		
		viewer.getTaskList().taskChanged(task);
	}
}
