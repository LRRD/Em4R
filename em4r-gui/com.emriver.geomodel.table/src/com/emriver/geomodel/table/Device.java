package com.emriver.geomodel.table;

public enum Device
{
	// This enum is hard coded to match the numeric values of the remote software on the table.
	
	DEV_UNKNOWN(-1),	// TODO might not be needed
	DEV_PITCH(0),
	DEV_ROLL(1),
	DEV_UPPIPE(2),
	DEV_DOWNPIPE(3),
	DEV_PUMP(4);

	private int numValue;

	Device(int numValue)
	{
		this.numValue = numValue;
	}

	public int getNumValue()
	{
		return numValue;
	}

	public static Device getByValue(int value)
	{
		for (Device d : Device.values()) {
			if (d.numValue == value) {
				return d;
			}
		}
		throw new IllegalArgumentException("Device not found.");
	}

	@Override
	public String toString()
	{
		switch (this) {
		case DEV_UNKNOWN:   		return "UNKNOWN";
		case DEV_PITCH:	    		return "Pitch";
		case DEV_ROLL:	    		return "Roll";
		case DEV_UPPIPE:	    	return "Upper";
		case DEV_DOWNPIPE:	    	return "Lower";
		case DEV_PUMP:	    		return "Pump";
		default:
			throw new IllegalArgumentException();
		}
	}
	
	// -----------------------------------------------------------------------------
	
	// TODO Hacky, nice to be embedded in the enum here tho
	public double getMin()
	{
		switch (this) {
		case DEV_UNKNOWN:   		return  -1.00;	// degrees
		case DEV_PITCH:	    		return   0.00;
		case DEV_ROLL:	    		return  -3.00;
		case DEV_UPPIPE:	    	return   0.00;	// mm
		case DEV_DOWNPIPE:	    	return   0.00;
		case DEV_PUMP:	    		return   0.00;	// ml/sec
		default:
			throw new IllegalArgumentException();
		}
	}
	public double getMax()
	{
		switch (this) {
		case DEV_UNKNOWN:   		return   1.00;
		case DEV_PITCH:	    		return   4.00;
		case DEV_ROLL:	    		return   3.00;
		case DEV_UPPIPE:	    	return 100.00;
		case DEV_DOWNPIPE:	    	return 100.00;
		case DEV_PUMP:	    		return 900.00;
		default:
			throw new IllegalArgumentException();
		}
	}
	public double getStep()
	{
		switch (this) {
		case DEV_UNKNOWN:   		return   0.10;
		case DEV_PITCH:	    		return   0.10;
		case DEV_ROLL:	    		return   0.10;
		case DEV_UPPIPE:	    	return   1.00;
		case DEV_DOWNPIPE:	    	return   1.00;
		case DEV_PUMP:	    		return  10.00;
		default:
			throw new IllegalArgumentException();
		}
	}

	public String getUnit()
	{
		switch (this) {
		case DEV_UNKNOWN:   		return   "?";
		case DEV_PITCH:	    		return   "degrees";
		case DEV_ROLL:	    		return   "degrees";
		case DEV_UPPIPE:	    	return   "mm";
		case DEV_DOWNPIPE:	    	return   "mm";
		case DEV_PUMP:	    		return   "mL/s";
		default:
			throw new IllegalArgumentException();
		}
	}
}
