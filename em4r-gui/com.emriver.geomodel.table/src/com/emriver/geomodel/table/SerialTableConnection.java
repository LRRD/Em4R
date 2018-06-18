package com.emriver.geomodel.table;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.serial.ISerialIOListener;
import com.elmocity.elib.serial.SerialIO;
import com.elmocity.elib.util.WireMessage;

public class SerialTableConnection implements ITableConnection, ISerialIOListener
{
    private final String portName = "COM4";		// TODO param

    private SerialIO serial;
    
    private ArrayList<ITableConnectionListener> listeners = new ArrayList<ITableConnectionListener>();
	
    // Unique request ID, used for matching responses (or debugging)
	private static volatile Long id = new Long(100);
    
	// Last known sequence number that the table sent us in a message.  We are supposed to echo this back in the next request we send.
	// WARNING this is an unsigned byte for transport to and from the table, so it wraps constantly.
	private volatile long lastKnownTableSequenceNumber = 0;

	
	private static final Logger logger = LoggerFactory.getLogger(SerialTableConnection.class);

	
	@Override
	public void connect()
	{
		// DEBUG: show the status of the CommPorts before we try to connect in the debug log.
		// Crashes or Terminates can leave the ports "in use" from our prior application instance.  :-(
		SerialIO.logCommPorts();

		serial = new SerialIO();
		boolean worked = serial.startup(portName, this);
		if (!worked) {
			logger.warn("unable to startup comm port {}", portName);
			serial = null;
		}
	}

	@Override
	public void disconnect()
	{
		if (serial != null) {
			serial.shutdown();
			serial = null;
		}
	}

	@Override
	public boolean isConnected()
	{
		return serial != null;
	}

	@Override
	public void sendRequest(Request request)
	{
		if (serial == null) {
			return;
		}

		WireMessage message = makeRequestMessage(request);
		serial.write(message);
	}

	@Override
	public void addTableListener(ITableConnectionListener listener)
	{
		listeners.add(listener);
	}
	
	// ---------------------------------------------------------------------------------------
	
	// Callback from the serial object, each time we get a message from the COM port
	@Override
	public void receiveMessage(WireMessage message)
	{
		if (serial == null) {
			return;
		}

		Response response = parseResponseMessage(message);
		if (response == null) {
			return;
		}
		// HACK TODO since this is a null modem cable, we just get an echo back... fake it
		response.status = TableController.RSP_OK;

		// Check to find any listeners and give them the payload
		for (ITableConnectionListener l : listeners) {
			l.receiveResponses(new Response[] {response});
		}

		return;
	}

	// ---------------------------------------------------------------
	
	private WireMessage makeRequestMessage(Request request)
	{
		long requestID;
		synchronized (id)
		{
			requestID = id++;
		}

		// No EOL
		String m = String.format("%d %d %s %.2f %d", requestID, request.device.getNumValue(), request.command, request.value, request.seconds);
		
		byte[] buf = m.getBytes();
		WireMessage message = new WireMessage(buf, buf.length);
		return message;
	}

	// Parse the string from the serial line, the EOL has already been removed. 
	private Response parseResponseMessage(WireMessage message)
	{
		byte[] buf = message.getData();
		String payload = new String(buf);
		
		// Remove any leading and trailing whitespace, then break the string up into fields using any whitespace delimiter
		
		String[] split = payload.trim().split("\\s+");
		if (split.length == 0) {
			// Failed
			return null;
		}
		if (split.length != 5) {
			// Failed
			return null;
		}

		Response response = new Response();
		
		try {
			lastKnownTableSequenceNumber = Long.parseLong(split[0]);
			response.device = Device.getByValue(Integer.parseInt(split[1]));
			response.status = split[2];
			response.value = Double.parseDouble(split[3]);
			response.seconds = Integer.parseInt(split[4]);
		}
		catch (Exception e) {
			return null;
		}
		
		return response;
	}
}
