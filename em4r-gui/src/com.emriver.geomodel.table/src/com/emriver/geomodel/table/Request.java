package com.emriver.geomodel.table;

public class Request
{
	public Device device;
	public String command;
	public double value;
	public int seconds;
	
	public Request(Device device, String command, double value, int seconds)
	{
		this.device = device;
		this.command = command;
		this.value = value;
		this.seconds = seconds;
	}
	
	public String debugString()
	{
		return String.format("%d %s %.02f %d", device.getNumValue(), command, value, seconds);
	}

}
