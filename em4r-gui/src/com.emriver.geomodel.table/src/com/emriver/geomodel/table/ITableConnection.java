package com.emriver.geomodel.table;

public interface ITableConnection
{
		public void connect();

		public void disconnect();

		public boolean isConnected();

		// -------------------------

		/**
		 *  Sends a command to the table, and hope to someday get a callback if the table responds... or not.
		 *  <p>
		 *  Non-blocking.
		 */
		public void sendRequest(Request request);
		
		/**
		 *  Hook for caller to receive the Response (and/or periodic updates) from the table
		 */
		public void addTableListener(ITableConnectionListener listener);

}
