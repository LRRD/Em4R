package com.emriver.geomodel.table;

public class Response
{
	public Device device;
	public String status;
	public double value;
	public int seconds;

	public Response()
	{
	}

	public Response(Device device, String status, double value, int seconds)
	{
		this.device = device;
		this.status = status;
		this.value = value;
		this.seconds = seconds;
	}
	
	public String debugString()
	{
		return String.format("%d %s %.02f %d", device.getNumValue(), status, value, seconds);
	}
}
