package com.elmocity.elib.swt.table;

import java.time.LocalDate;
import java.util.ArrayList;

public class GenericRow
{
	public ArrayList<Object> data = new ArrayList<Object>(10);

	// Getters
	
	public Object getValue(int column)
	{
		return data.get(column);
	}
	
	public <T> T getValue(int column, Class<T> c)
	{
		try {
			Object obj = data.get(column);
			return c.cast(obj);
		}
		catch (Exception e)
		{
			System.out.println("eek" + e.getMessage());
		}
		return null;
	}

	// Setters
	
	public void setValue(int column, Object value)
	{
		Object oldValue = data.get(column);
		data.set(column, value);
	}
	
	// Adders
	
	public void addDouble(double v)
	{
		data.add(new Double(v));
	}
	public void addInteger(int v)
	{
		data.add(new Integer(v));
	}
	public void addBoolean(boolean v)
	{
		data.add(new Boolean(v));
	}
	public void addString(String s)
	{
		data.add(s);
	}
	public void addLocalDate(LocalDate date)
	{
		data.add(date);
	}
	
	
	public String debugString()
	{
		String debug = "";
		
		for (int i = 0; i < data.size(); i++) {
			if (i != 0) {
				debug += ", ";
			}
			debug += data.get(i).toString();
		}
		
		return debug;
	}
}
