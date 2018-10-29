package com.elmocity.elib.swt.table;

import java.util.ArrayList;

public class GenericTableInfoList extends ArrayList<GenericTableInfo>
{
	public static GenericTableInfoList getSampleInfo()
	{
		GenericTableInfoList list = new GenericTableInfoList();
		
		GenericTableInfo info;
		
		info = new GenericTableInfo("First Name", 60, String.class, null, null);
		list.add(info);
		info = new GenericTableInfo("Last Name", 100, String.class, null, null);
		list.add(info);
		
		return list;
	}
	public static GenericRowList getSampleData()
	{
		GenericRowList rows = new GenericRowList();
		GenericRow row;
		
		row = new GenericRow();
		row.data.add(new String("Bob"));
		row.data.add(new String("Smith"));
		rows.addTask(row);

		row = new GenericRow();
		row.data.add(new String("Sally"));
		row.data.add(new String("Struthers"));
		rows.addTask(row);

		return rows;
	}
}