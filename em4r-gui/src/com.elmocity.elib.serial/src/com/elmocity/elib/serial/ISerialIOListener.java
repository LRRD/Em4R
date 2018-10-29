package com.elmocity.elib.serial;

import com.elmocity.elib.util.WireMessage;

public interface ISerialIOListener
{
	// Callback interface used by any object that creates a serial connection.
	void receiveMessage(WireMessage message);
}
