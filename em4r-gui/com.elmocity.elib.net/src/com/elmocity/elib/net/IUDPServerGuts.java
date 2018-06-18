package com.elmocity.elib.net;

import com.elmocity.elib.util.WireMessage;

public interface IUDPServerGuts
{
	/**
	 * Callback method used by the UDP server to have the application process a packet.
	 * <p>
	 * The server assumes a 1-to-1 mapping of requests and replies, and that no packets get fragmented.
	 * <p>
	 * @param request The WireMessage from the client.
	 * 
	 * @return The response (as another WireMessage) to send back to the client.
	 */
	WireMessage responseToPacket(WireMessage request);
}
