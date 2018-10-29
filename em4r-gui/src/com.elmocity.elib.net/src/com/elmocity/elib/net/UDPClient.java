package com.elmocity.elib.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.util.WireMessage;

public class UDPClient
{
	InetAddress serverAddress;
	int serverPort; 
	IUDPListener responseListener;
	
	private DatagramSocket socket;
	private Thread incomingThread;
	
	
	private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);


	public boolean startup(String address, int serverPort, IUDPListener responseListener)
	{
		InetAddress serverAddress;
		try {
			serverAddress = InetAddress.getByName(address);
		}
		catch (UnknownHostException e) {
			return false;
		}

		return startup(serverAddress, serverPort, responseListener);
	}
	public boolean startup(InetAddress serverAddress, int serverPort, IUDPListener responseListener)
	{
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		this.responseListener = responseListener;
		
		try {
			socket = new DatagramSocket();
			int debugSize = socket.getSendBufferSize();
			logger.info("UDP client send buffer size = {}", debugSize);
		}
		catch (SocketException e) {
			logger.warn("UDP client socket create failed for {} {}", serverAddress.toString(), serverPort);
			return false;
		}
		logger.debug("UDP client created socket for {} {}", serverAddress.toString(), serverPort);
		
		incomingThread = new Thread( new Runnable() {
		    @Override
		    public void run()
		    {
				Thread.currentThread().setName("UDPWatcher");
		    	listenerLoop();
		    }
		});
		incomingThread.start();
		
		return true;
	}

	public void shutdown()
	{
		if (incomingThread != null) {
			incomingThread.interrupt();
			incomingThread = null;
		}
		
		this.serverAddress = null;
		this.serverPort = 0;
		this.responseListener = null;
		this.socket = null;
	}
	
	// ---------------------------------------------------------------------------
	
	public void sendRequest(WireMessage message)
	{
		DatagramPacket packet;

		try {
			packet = new DatagramPacket(message.getData(), message.getLength(), serverAddress, serverPort);
			socket.send(packet);
		}
		catch (SocketException e) {
			logger.warn("UDP client send failed {}", e);	
			return;
		}
		catch (IOException e) {
			logger.warn("UDP client send failed {}", e);	
			return;
		}
		logger.trace("UDP client sent {} bytes", message.getLength());	
	}
	
	private void listenerLoop()
	{
		DatagramPacket packet;
		String response;
		
		while (true) {
			try {
				// We expect the server to reply immediately
				if (socket == null) {
					break;
				}
				socket.setSoTimeout(10000);
				
				byte[] buf = new byte[256];
				packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);		// BLOCKING
				
				// NOTE: a worker thread sitting at a sleep() would throw a InterruptedException when told interrupt() was called from the master.
				// But since this worker is blocking at a socket call, it appears that the socket.receive just breaks and returns -1, leaving the
				// thread interrupted() flag set... so we have to check that here, instead of catching the exception like a normal thread exit.
				if (Thread.interrupted()) {
					break;
				}
				int receivedLength = packet.getLength();
				if (receivedLength < 0) {
					break;
				}
				WireMessage message = new WireMessage(packet.getData(), receivedLength);
//				logger.trace("UDP client rcvd response {} bytes", message.getLength());	
				
				// Tell the caller we got a message for them
				if (responseListener != null) {
					responseListener.receiveMessage(message);
				}
			}
			catch (SocketTimeoutException e) {
				// Sanity test to have show the thread is still running
				logger.trace("UDP client periodic sanity check");
				continue;
			}
			
			catch (SocketException e) {
				return;
			}
			catch (IOException e) {
				return;
			}
			// NOTE: a worker thread sitting at a sleep() would throw a InterruptedException when told interrupt() was called from the master.
			// But since this worker is blocking at a socket call, it appears that the socket.receive just breaks and returns -1, leaving the
			// thread interrupted() flag set... so we have to check that here, instead of catching the exception like a normal thread exit.
			if (Thread.interrupted()) {
				break;
			}
		}
	}
}
