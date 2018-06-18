package com.elmocity.elib.net;

import com.elmocity.elib.util.WireMessage;

public interface IUDPListener
{
	// Callback interface used by any object that creates a UDP connection.
	void receiveMessage(WireMessage message);
}
