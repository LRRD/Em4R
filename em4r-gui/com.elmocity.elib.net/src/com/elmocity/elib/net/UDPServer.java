package com.elmocity.elib.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.util.WireMessage;

public class UDPServer
{
	private IUDPServerGuts guts;
	private int serverPort = 4000;
	private volatile boolean keepRunning = true;
	private Thread worker = null;

	private final static Logger logger = LoggerFactory.getLogger(UDPServer.class);

	public UDPServer()
	{
		
	}

	public void start(int serverPort, IUDPServerGuts guts)
	{
		if (worker != null) {
			logger.debug("rejecting attempt to start server that is already running.");
			return;
		}

		this.guts = guts;
		this.serverPort = serverPort;

		keepRunning = true;
		worker = new ServerThread();
		worker.start();
	}

	public void stop()
	{
		if (worker == null) {
			logger.debug("rejecting attempt to stop server - none running.");
			return;
		}

		keepRunning = false;
		worker = null;
	}
	
	private class ServerThread extends Thread
	{
		@Override
		public void run()
		{
			DatagramSocket socket = null;
			
			Thread.currentThread().setName("UDPServer" + serverPort);
			logger.debug("UDP server starting on port {}", serverPort);

			try {
				socket = new DatagramSocket(serverPort);
				socket.setSoTimeout(10000);				// Can be zero to mean infinite block on receive()
			}
			catch (SocketException e1) {
				keepRunning = false;
			}
	
			while (keepRunning) {
				try {
					byte[] buf = new byte[1024];
	
					// receive request (blocking)
					DatagramPacket packet = new DatagramPacket(buf, buf.length);
					socket.receive(packet);
	
					// determine some information about the sender
					InetAddress clientAddress = packet.getAddress();
					int clientPort = packet.getPort();
	
					WireMessage request = new WireMessage(buf, packet.getLength());
					logger.debug("UDP server rcvd request {} bytes", packet.getLength());
					
					// have the application inspect packet payload to compute action required
					WireMessage response = guts.responseToPacket(request);
	
					// send the response to the client at the original address/port
					packet = new DatagramPacket(response.getData(), response.getLength(), clientAddress, clientPort);
					socket.send(packet);

					logger.debug("UDP server sent reply {} bytes", response.getLength());
				}
				catch (SocketTimeoutException e) {
					logger.info("UDP server periodic sanity check");
				}
				catch (IOException e) {
					e.printStackTrace();
					keepRunning = false;
				}
			}
			
			if (socket != null) {
				socket.close();
				socket = null;
			}
			logger.debug("UDP server shutting down on port {}", serverPort);
		}
	}
}
