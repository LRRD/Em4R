package com.elmocity.elib.util;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Using a binary format for data over a communications link (serial or UDP etc) is pretty ugly.  This class acts as a simple container
 * to get up through listerers and callbacks until some level can un-marshal the data and get it into an actual Java class.
 */
public class WireMessage
{
	ArrayList<Byte> data;
	
	private final static Logger logger = LoggerFactory.getLogger(WireMessage.class);

	
	public WireMessage(byte[] buf, int len)
	{
		setData(buf, len);
	}
	
	public void setData(byte[] buf, int len)
	{
		data = new ArrayList<Byte>(len);
		for (int i = 0; i < len; i++) {
			data.add(buf[i]);
		}
	}
	
	public int getLength()
	{
		if (data == null) {
			return 0;
		}
		return data.size();
	}
	
	public byte[] getData()
	{
		byte[] buf = new byte[data.size()];
		for(int i = 0; i < data.size(); i++) {
			buf[i] = data.get(i);
		}
		return buf;
	}
	
	public void debugHexDump()
	{
		final int bytesPerDisplayRow = 4;
		
		for (int i = 0; i < data.size(); i += bytesPerDisplayRow) {
			String hex = "0x";
			for (int j = 0; j < bytesPerDisplayRow; j++) {
				if (i + j < data.size()) {
					hex += String.format(" %02X", data.get(i + j));
				}
			}
			logger.trace("{} {}", String.format("%03d", i), hex);
		}

	}
}
