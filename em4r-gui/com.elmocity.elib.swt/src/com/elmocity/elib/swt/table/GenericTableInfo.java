package com.elmocity.elib.swt.table;

import java.util.Formatter;

import org.eclipse.jface.viewers.ViewerComparator;

public class GenericTableInfo
{
	public String header;
	public int width;
	
	public Class dataType;
	public Formatter format;
	public ViewerComparator sorter;
	
	public <T> GenericTableInfo(String header, int width, Class dataType, Formatter format, ViewerComparator sorter)
	{
		this.header = header;
		this.width = width;
		this.dataType = dataType;
		this.format = format;
		this.sorter = sorter;
	}
}
