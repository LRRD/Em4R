package com.elmocity.elib.swt.table;

import java.net.URL;
import java.time.LocalDate;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import com.elmocity.elib.util.Format;


//import org.eclipse.jface.viewers.ITableLabelProvider;

/**
 * Label provider for the TableViewerExample
 * 
 * @see org.eclipse.jface.viewers.LabelProvider 
 */
// extends LabelProvider
public class GenericLabelProvider implements ITableLabelProvider // extends CellLabelProvider // implements ITableLabelProvider
{
	// Names of images used to represent checkboxes
	public static final String UNCHECKED_IMAGE 	= "add_obj.gif";
	public static final String CHECKED_IMAGE  = "trash.gif";

	// For the checkbox images
	private static ImageRegistry imageRegistry = new ImageRegistry();

	/**
	 * Note: An image registry owns all of the image objects registered with it,
	 * and automatically disposes of them the SWT Display is disposed.
	 */ 
	static {
//		try {
//			// Can get the image for real, using this command.
//			Image woof = new Image(null, "res/red_x.gif");
//			Image woof2 = new Image(null, "src/table/red_x.gif");
//			int j = 10;
//		}
//		catch (Exception e) {
//			SS.log("Failed to load one of the images via new Image()");
//		}
		
		// This is going to be an absolute URL path from the top of our project tree or jar.
		// Need to have the build path of the project set so that the resource folder (outside the src tree) is included.
		// Subfolders in the resource folder work, so we will have images, sounds, etc in subfolders.
		String iconPath = "/images/"; 

		URL imgURL = GenericLabelProvider.class.getResource(iconPath + CHECKED_IMAGE);
		if (imgURL != null) {
			imageRegistry.put(CHECKED_IMAGE, ImageDescriptor.createFromURL(imgURL));
		}

		imgURL = GenericLabelProvider.class.getResource(iconPath + UNCHECKED_IMAGE);
		if (imgURL != null) {
			imageRegistry.put(UNCHECKED_IMAGE, ImageDescriptor.createFromURL(imgURL));
		}

//		imgURL = ExampleLabelProvider.class.getResource(iconPath + UNCHECKED_IMAGE);
//		imageRegistry.put(UNCHECKED_IMAGE, ImageDescriptor.createFromURL(imgURL));

//		imageRegistry.put(CHECKED_IMAGE, ImageDescriptor.createFromFile(
//				TableViewerExample.class, 
//				iconPath + CHECKED_IMAGE + ".gif"
//				)
//			);
//		imageRegistry.put(UNCHECKED_IMAGE, ImageDescriptor.createFromFile(
//				TableViewerExample.class, 
//				iconPath + UNCHECKED_IMAGE + ".gif"
//				)
//			);	
	}
	
	private final GenericTableInfoList info;
	public GenericLabelProvider(GenericTableInfoList info)
	{
		super();
		this.info = info;
	}


	/**
	 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
	 */
	@Override
	public String getColumnText(Object element, int columnIndex)
	{
		String result = "";
		GenericRow task = (GenericRow) element;

		Object value = task.getValue(columnIndex);
		if (info.get(columnIndex).dataType == String.class) {
			String v = (String) value;
			result = v;
		}
		else if (info.get(columnIndex).dataType == Integer.class) {
			Integer v = (Integer) value;
			result = "" + v;	// TODO formatter
		}
		else if (info.get(columnIndex).dataType == Double.class) {
			Double v = (Double) value;
			if (v == null) {
				result = "";
			}
			else {
				result = String.format("%.02f", v);	// TODO formatter
			}
		}
		else if (info.get(columnIndex).dataType == Boolean.class) {
			Boolean v = (Boolean) value;
			result = "" + v;	// TODO formatter
		}
		else if (info.get(columnIndex).dataType == LocalDate.class) {
			LocalDate v = (LocalDate) value;
			result = "" + Format.date(v);	// TODO formatter
		}
		
		return result;
	}

	/**
	 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
	 */
	public Image getColumnImage(Object element, int columnIndex)
	{
		// Based on the columnIndex and the value in this row, we could show an icon next to the text too
		String key = null;
//		if (check data value in this column) {
//			key = CHECKED_IMAGE;
//			key = UNCHECKED_IMAGE;
//		}
		if (key == null) {
			return null;
		}
		return imageRegistry.get(key);
	}
	
	
	// -- unused crap?

	@Override
	public void addListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub
	}
}
