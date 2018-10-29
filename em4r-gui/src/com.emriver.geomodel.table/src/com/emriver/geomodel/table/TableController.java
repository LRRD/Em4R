package com.emriver.geomodel.table;


import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller object class for a single Little River R & D EMRiver Table.
 * <br>
 * This is a proto implementation that assumes an EM4 and that we are talking to an on-table embedded device via UPD or serial port.
 *
 */
public class TableController implements ITableConnectionListener
{
	// TODO was using text strings for the protocol so tty connections could be debugged/simulated with terminal programs... these are obsolete?
	// COMMANDS
	public static final String CMD_SET = "SET";
	public static final String CMD_GET = "GET";
	public static final String CMD_STOP = "STOP";		// TODO STOPALL? REBOOT/RESET? etc

	// RESPONSE CODES
	public static final String RSP_OK = "OK";
	public static final String RSP_BADPARARM = "BADPARAM";
	public static final String RSP_FAILED = "FAILED";
	public static final String RSP_TIMEOUT = "TIMEOUT";


	// Internal connection to the table, handling wire connection and protocol
	private ITableConnection connection;

	// Cache of the last known data about each device on the table.
	// Users never directly query the table/device, they always just get these latest values we know about.
	private static ConcurrentHashMap<Device, Response> currentValues;
	

	private static final Logger logger = LoggerFactory.getLogger(TableController.class);

	

	
	/**
	 * Caller creates a connection object (UDP, serial, debug loopback, etc) and hands us the interface.
	 * @param connection
	 */
	public TableController(ITableConnection connection)
	{
		this.connection = connection;
		//assert(connection != null);
		// TODO auto connect?
		connect();
	}

	public void connect()
	{
		// Zero out the current values in the cache
		currentValues = new ConcurrentHashMap<Device, Response>(16);

		// Load up the cache with initial values
		currentValues.put(Device.DEV_PITCH, new Response(Device.DEV_PITCH, "uninitialized", 0.00, 0));
		currentValues.put(Device.DEV_ROLL, new Response(Device.DEV_ROLL, "uninitialized", 0.00, 0));
		currentValues.put(Device.DEV_UPPIPE, new Response(Device.DEV_UPPIPE, "uninitialized", 0.00, 0));
		currentValues.put(Device.DEV_DOWNPIPE, new Response(Device.DEV_DOWNPIPE, "uninitialized", 0.00, 0));
		currentValues.put(Device.DEV_PUMP, new Response(Device.DEV_PUMP, "uninitialized", 0.00, 0));


		// Add ourselves as the incoming data listener, then connect
		connection.addTableListener(this);
		connection.connect();
	}
	
	public void disconnect()
	{
		connection.disconnect();
	}
	
	public boolean isConnected()
	{
		return connection.isConnected();
	}
	
	// ------------------------------------------------------------------------------

	/**
	 *  Non-blocking, just sends the command off and will get a callback/update someday if the table responds... or not.
	 */
	public void sendRequest(Request request)
	{
		if (!connection.isConnected()) {
			return;
		}

		connection.sendRequest(request);
	}

	/**
	 *  Fetch the last value we know for a given device.
	 *  <p>
	 *  This might get updated from direct replies or possibly automated async bulk updates from the table.
	 * @param device
	 * @return  Never returns null... as the cache gets initialized with some default values and status.
	 */
	// Non-blocking since it doesn't talk to the table.
	public Response getCurrentValue(Device device)
	{
		// We cached fake initial values in the map for all devices, so this should never return null
		Response response = currentValues.get(device);
		return response;
	}


	/**
	 * Receive data from the underlying connection.
	 * <p>
	 * Overwrites the cache of existing data for that device.<br>
	 * Does not notify application level code.. they have to poll the current values. 
	 */
	@Override
	public void receiveResponses(Response[] responses)
	{
		for (Response response : responses) {
			// TODO it is unspecified if/how the underlying connection could notify us of faults/errors/brokenConnections
			// HACK TODO since this might be a null modem cable, we just get an echo back... fake it that "all is well"
			response.status = RSP_OK;
			
			double oldValue = getCurrentValue(response.device).value;
	
//			logger.debug("updating dev {} from old {} to new {}", response.device, oldValue, response.value);
			
			// Copy the response into the cache (overwrites old value)
			currentValues.put(response.device, response);
		}
	}
}


