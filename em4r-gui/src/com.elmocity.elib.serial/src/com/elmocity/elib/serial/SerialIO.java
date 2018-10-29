package com.elmocity.elib.serial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.serial.ISerialIOListener;
import com.elmocity.elib.serial.SerialIO;
import com.elmocity.elib.util.WireMessage;

import purejavacomm.*;
import jtermios.*;
import static jtermios.JTermios.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TooManyListenersException;

/**
 * Wrapper object for a single COM port.
 * <br>
 * Assumes that data passed back and forth is String data and uses an End Of Message character.
 * <br>
 * TODO needs to handle escape sequence in case the data contains the EOM char.
 * TODO partial "packets" fail. 
 */
public class SerialIO implements SerialPortEventListener
{
	private static final Logger logger = LoggerFactory.getLogger(SerialIO.class);

	// End of Message marker BYTE (not a CHAR, because BYTE is signed and CHAR is 16-bit?)
	public final static byte EOM = (byte) 0xFF;

	
	// Provided by caller
	private String portName;
	
	
	private boolean isConnected = false;
	private CommPortIdentifier portID;
	private InputStream inputStream;
	private OutputStream outputStream;
	private SerialPort serialPort;
	private ISerialIOListener responseListener = null;		// TODO could allow multiple listeners, like a network tap

	/**
	 * Connect to a given comm port and start a worker thread to process read/writes.
	 * <br>
	 * @return true if connection established.
	 * <br>
	 * @param portName operating system name of the port, like "COM4" on Windows
	 * @param responseListener  callback handler for response data
	 */
	public boolean startup(String portName, ISerialIOListener responseListener)
	{
		logger.debug("{} startup", portName);

		this.portName = portName;
		this.responseListener = responseListener;
		
		// Find this comm port by name
		try {
			portID = CommPortIdentifier.getPortIdentifier(portName);
			logger.debug("{} found in OS devices", portName);
		}
		catch (Exception e) {
			logger.warn("{} failed to locate port by name - check OS or VM config", portName);
			logger.warn("comm port", e);
			return false;
		}

		// Open the comm port
		try {
			serialPort = (SerialPort) portID.open("EMRIVER", 2000);	// owner and timeout ms
			logger.debug("{} opened", portName);
		}
		catch (PortInUseException e) {
			// NOTE: in most cases, the "in use" is fake, since it was likely our application that terminated badly, like in the debugger.
			logger.warn("{} appears to be in use already - ignoring error and trying anyway", portName);
			//return false;
		}

		// Get some IO buffers.  TODO: lots of Java choices for Buffered etc, might be better subclasses to use.
		try {
			inputStream = serialPort.getInputStream();
			outputStream = serialPort.getOutputStream();
			logger.debug("{} acquired in and out streams", portName);
		}
		catch (IOException e) {
			logger.warn("{} failed to open streams", portName);
			serialPort.close();
			return false;
		}

		try {
			serialPort.addEventListener(this);
		}
		catch (TooManyListenersException e) {
			// impossible
		}
		serialPort.notifyOnDataAvailable(true);

		try {
			// Needs to match the settings in the windows control panel PORTS settings.
			// TODO these might already be initialized for us by the Host OS/Java VM to default settings?
			serialPort.setSerialPortParams(
					9600,
					SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);
			
			// This setting is not included in the "params" method, so I assume it can be changed dynamically.
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			// These lines might get set false from the above call, but make sure to start with.
			serialPort.setDTR(false);
			serialPort.setRTS(false);
			
			logger.debug("{} set params OK", portName);
		}
		catch (UnsupportedCommOperationException e) {
			logger.warn("{} failed to set params", portName);
			serialPort.close();
			return false;
		}

		// All good
		isConnected = true;
		return true;
	}

	public void shutdown()
	{
		logger.debug("{} shutdown", portName);

		// Stop sending anything to the listener before we tear stuff apart
		responseListener = null;

		// Cleanup
		try {
			if (outputStream != null) {
				outputStream.close();
			}
			if (inputStream != null) {
				inputStream.close();
			}
			if (serialPort != null) {
				serialPort.close();
			}
		}
		catch (IOException e)
		{
			logger.warn("{} error while closing/releasing serial port", portName);
			e.printStackTrace();
		}
		
		// Reset in case the user wants to reuse this object, they should probably just -new- up another.
		outputStream = null;
		inputStream = null;
		serialPort= null;
		
		portID = null;
		isConnected = false;
	}

	// ---------------

	public void write(WireMessage message)
	{
		if (outputStream == null) {
			logger.warn("{} write - outputStream not initialized", portName);
			return;
		}
		
		logger.debug("{} write - payload of {} bytes", portName, message.getLength());
		
		try {
			byte[] buf = message.getData();
			outputStream.write(buf, 0, message.getLength());
			outputStream.write(EOM);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

//	public String read()
//	{
//		if (inputStream == null) {
//			logger.warn("{} read - inputStream not initialized", portName);
//			return "";
//		}
//
//		logger.debug("{} read", portName);
//
//		final int maxReplyLength = 100;
//		byte[] buffer = new byte[maxReplyLength];	// be careful, might end up full of chars with no null termination
//		
//		int replyCount = -1;
//		try {
//			// Blocking
//			int avail = inputStream.available();
//			logger.debug("{} read thinks {} bytes might be available", portName, avail);
//			
//			replyCount = inputStream.read(buffer, 0, maxReplyLength);
//			logger.debug("{} read {} bytes", portName, replyCount);
//		}
//		catch (IOException e) {
//			e.printStackTrace();
//		}
//
//		String result = "";
//		if (replyCount > 0) {
//			try {
//				result = new String(buffer, 0, replyCount, "UTF-8");
//			}
//			catch (UnsupportedEncodingException e) {
//			}
//		}
//		return result;
//	}


	// --------------------

//	// TODO Need to hide these in an impl class? 
//	public void run()
//	{
//		try {
//			Thread.sleep(100);
//		}
//		catch (InterruptedException e) {
//
//		}
//	}

	// TODO I assume this needs to have a character buffer to hold partial messages (partial string data that does not yet give us an EOM/EOL char).
	// We should not send partial messages up to the callers listener... we should just hold the partial string until we get another serialEvent with the
	// rest of the message.  This probably can't happen on a non-flow-controlled 10 ft cable from a laptop to a EM table, but would on a LA to NYC modem.
	public void serialEvent(SerialPortEvent event)
	{
		switch (event.getEventType()) {
		case SerialPortEvent.BI:
		case SerialPortEvent.OE:
		case SerialPortEvent.FE:
		case SerialPortEvent.PE:
		case SerialPortEvent.CD:
		case SerialPortEvent.CTS:
		case SerialPortEvent.DSR:
		case SerialPortEvent.RI:
		case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
			break;
			
		case SerialPortEvent.DATA_AVAILABLE:
			
			byte[] buf = new byte[1024];		// TODO this needs to be at least max message size
			
//			// DEBUG VOMIT HACK
//			// We are way too fast, and end up just reading a couple bytes of any response...
//			// So without a fixed sized expected response, or some form of End-of-Message marker, we are screwed.
//			try {
//				Thread.sleep(10);
//			}
//			catch (InterruptedException e1)
//			{
//				e1.printStackTrace();
//				return;
//			}
			
			try
			{
				int offset = 0;

				// Read one byte at a time
				while (true) {
					int receivedLength = inputStream.read(buf, offset, 1);
					if (receivedLength <= 0) {
						// EOF - serial port closed??
						return;
					}
//					logger.debug("{} READ {} bytes {}", portName, receivedLength, (char)(buf[offset]));

					// Check if the last character read was 
					if (buf[offset] != EOM) {
						// Update the pointer to where the next byte should be read
						offset = offset + receivedLength;
						// Go back to reading bytes
						continue;
					}

					// NOTE: discard the EOM character, since that was injected by this class on the send too.. the caller doesn't know.
					int payloadLength = offset;
					logger.debug("{} read  - payload of {} bytes", portName, payloadLength);
					
					// Feed the data string to the listener
					if (responseListener == null) {
						// NOTE: could log a warning to show that we received real data when the app had no listener to accept it.
					}
					else {
						WireMessage message = new WireMessage(buf, payloadLength);
						responseListener.receiveMessage(message);
					}
					break;
				}
			}
			catch (IOException e)
			{
				logger.debug("rcvd data excp", e);
			}

			break;
		}
	}
	
	// --------------------

	// NOTE: "CommPort" is any port, which has a Type value of either SerialPort or ParallelPort.
	
	// DEBUG
	public static void logCommPorts()
	{
		logger.debug("debug show of registered comm ports:");

		Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();
		while (ports.hasMoreElements()) {
			CommPortIdentifier cpi = ports.nextElement();
			logger.debug("  Name {}, Type {}, Owner {}", cpi.getName(), cpi.getPortType(), cpi.getCurrentOwner());
		}
	}
	
	// For GUI to get a list of available ports
	public static List<String> getKnownSerialPorts()
	{
		ArrayList<String> knownSerialPorts = new ArrayList<String>(4);

		Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();
		while (ports.hasMoreElements()) {
			CommPortIdentifier cpi = ports.nextElement();
			
			if (cpi.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				knownSerialPorts.add(cpi.getName());
			}
		}

		logger.debug("found {} serial comm ports that the user could choose from", knownSerialPorts.size());

		return knownSerialPorts;
	}

}
