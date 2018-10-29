package com.elmocity.ss.database;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static singleton database object that manages a connection to a hyperSQL server via JDBC.
 * <p>
 * Individual queries or commands are handled by tearing off an interface object to manage queries/errors/resultsets.
 */
public class SQLCore
{
	static Logger logger = LoggerFactory.getLogger(SQLCore.class);

	// All object instances share one connection for the life of the application
	static Connection connection;
	
	// Each instance of this class
    Statement statement;
    ResultSet resultset;

    
    private static String DB_DRIVER =	"org.hsqldb.jdbc.JDBCDriver";
    private static String DB_URL =		"jdbc:hsqldb:hsql://localhost/StockStrangler";
	private static String DB_USER =		"sa";
	private static String DB_PASSWORD =	"";
  
	public static boolean connect()
	{
		if (connection != null) {
			// already connected
			return true;
		}

		try {
			Class.forName(DB_DRIVER);
		}
		catch (Exception e) {
			logger.error("Failed to load HSQLDB JDBC driver " + e.getMessage());
			return false;
		}

		try {
			// Connect to a separate dedicated server running on the localhost.
			// The name is published by the server when it starts.
			// username is case-sensitive
			connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
		}
		catch (Exception e) {
			logger.error("driver failed to connect. " + e.getMessage());
			return false;
		}
		
		// assert(connection != null);
		return true;
	}

	public static boolean isConnected()
	{
		if (connection == null) {
			return false;
		}
		return true;
	}
	
	public static String getDebugConnectionInfo()
	{
		connect();
		
		String result = "";
		
		// Add some information we have in the code, even if we fail to get a connection
		result += "DB Driver: " + DB_DRIVER + "\n";
		result += "DB URL: " + DB_URL + "\n";
		result += "DB USER: " + DB_USER + "\n";
		result += "DB PW: " + DB_PASSWORD + "\n";
		result += "\n";

		if (!isConnected()) {
			result += "Can't connect to database.\n";
			return result;
		}

		try {
			DatabaseMetaData md = connection.getMetaData();

			result += "JDBC: " + md.getDriverName() + "\n";
			result += "RDBS: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion() + "\n";
			result += "Supports BatchUpdates: " + md.supportsBatchUpdates() + "\n";
			result += "Supports ColumnAliasing: " + md.supportsColumnAliasing() + "\n";
			result += "Supports FullOuterJoins: " + md.supportsFullOuterJoins() + "\n";
			result += "Supports MixedCaseIdentifiers: " + md.supportsMixedCaseIdentifiers() + "\n";
			result += "\n";
			result += "Conn is autocommit: " + connection.getAutoCommit() + "\n";
			result += "Conn is isolation level: " + connection.getTransactionIsolation() + "\n";
			result += "Conn network timeout (ms): " + connection.getNetworkTimeout() + "\n";
		}
		catch (SQLException e) {
			result += "metadata call failed: " + e.toString();
			return result;
		}
		
		return result;
	}
	
//	public SSSelect getSelect(SSSelectParams params)
//	{
//		return new SSSelect(params);
//	}

	// ------------
	

	final static DateTimeFormatter SQLdf =  DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public static String getQuotedSQLDate(LocalDate date)
	{
		String sqldate = date.format(SQLdf);
		return "'" + sqldate + "'";
	}

	
	// ----------------------------

		
	// If the server was running embedded in our VM, we have to call SHUTDOWN to flush it as we exit.
	// For an external dedicated server, we leave that up to the BAT file or service host to flush.
//    public void shutdown() throws SQLException
//    {
//
//        Statement st = conn.createStatement();
//
//        // db writes out to files and performs clean shuts down
//        // otherwise there will be an unclean shutdown when program ends
//        st.execute("SHUTDOWN");
//        conn.close();    // if there are no other open connection
//    }
    
    /**
     * Used for SQL command: SELECT
     */
    public synchronized void query(String expression)
    {
    	if (!connect()) {
    		return;
    	}
    	
        // NOTE: statement objects can be reused, but we will just make one each time, TODO need to optimize this
        try {
			statement = connection.createStatement();
 
	        // Run the query
	        resultset = statement.executeQuery(expression);
		}
        catch (SQLException e) {
			e.printStackTrace();
		}

        // leave the result set in the object, the caller will make another call to get the data into Java row objects.
//        dump(rs);
//        st.close();  
        // NOTE!! if you close a statement the associated ResultSet is closed too
        // so you should copy the contents to some other object.
        // the result set is invalidated also  if you recycle an Statement
        // and try to execute some other query before the result set has been
        // completely examined.
    }

    /**
     * Used for SQL commands: CREATE, DROP, INSERT and UPDATE
     */
    public synchronized String update(String expression)
    {
    	if (!connect()) {
    		return "connection failed";
    	}

    	int return_count = -2;
    	String error = "";
    	
    	statement = null;
        try {
			statement = connection.createStatement();

			return_count = statement.executeUpdate(expression);    // run the query
	        if (return_count < 0) {
	        	error = "update failed: " + return_count + ", query: " + expression.substring(0, 20);
	        }
	
	        statement.close();
		}
        catch (SQLException e) {
            error = "update exception : " + e.getMessage() + " " + expression.substring(0, 20);
		}
        
        if (return_count < 0) {
        	logger.error(error);
        	return error;
        }
        return "" + return_count + " row(s) affected";
   }
    
//    // If the resultSet is IBQuoteRow, suck it all out at once shot into a Java array
//    protected ArrayList<IBQuoteRow> copyQuotesToArray()
//    {
//    	if (resultset == null) {
//            logger.error("cannot copyToArray, resultset == null");
//    		return null;
//    	}
//    	
////       	ElapsedTime woof = new ElapsedTime();
//
//    	ArrayList<IBQuoteRow> result = new ArrayList<IBQuoteRow>();
//    	
//        // The resultset order is implementation dependent, so use the SQL ORDER statement, if you care.
//    	
//		try {
//	        ResultSetMetaData meta = resultset.getMetaData();
//	        int cols = meta.getColumnCount();
//
////	        System.out.println("column count = " + cols);
//	        
//	        // The resultset is a cursor into the data.  You can only point to one row at a time, and don't know the row count.
//	        
//	        // Assume we are pointing to BEFORE the first row rs.next() points to next row and returns true
//	        // or false if there is no next row, which breaks the loop
//	        for (; resultset.next(); ) {
//	        	IBQuoteRow row = new IBQuoteRow();
//	        	// Programming col index, zero-based.
//	            for (int i = 0; i < cols; i++) {
//	            	// SQL columns are 1-based, for humans
//	            	int sql_col = i + 1;
//	            	// Info about the column itself
//	            	String sql_col_name = meta.getColumnName(sql_col);
//
//	            	
//	            	// Actual data value in this column
//	                Object sql_col_value = resultset.getObject(sql_col);	    // SQL columns start at "1" not "0"
//	                if (sql_col_value != null) {
////	                	System.out.print(sql_col_value.toString() + " ");
//	                }
//	                else {
////	                	System.out.print("<null>" + " ");
//	                }
//	                
//	                // Put the value into the correct field of the row object
//	                copySQLColumnToJavaField(sql_col_name, sql_col_value, row);
//	            }
////	            System.out.println(" ");
//	            
//	            result.add(row);
//	        }
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//		}
//
////        SS.log("copyToArray() time = " + woof.elapsed());
//        return result;
//    }
//
    public void close()
    {
    	try {
    		if (statement != null) {
    			statement.close();
    		}
		}
    	catch (SQLException e) {
			e.printStackTrace();
		}
    }
//    
//    
//    // If the resultSet is a count, or admin table, toss it into a generic flex array instead of the IBQuoteRow specific Java obj fields
//    public GenericRowList copyFlexToList(GenericTableInfoList info)
//    {
//    	if (resultset == null) {
//            logger.error("cannot copyFlexToList, resultset == null");
//    		return null;
//    	}
//    	
////       	ElapsedTime woof = new ElapsedTime();
//
//    	GenericRowList result = new GenericRowList();
//    	
//        // The resultset order is implementation dependent, so use the SQL ORDER statement, if you care.
//    	
//		try {
//	        ResultSetMetaData meta = resultset.getMetaData();
//	        int cols = meta.getColumnCount();
//
////	        System.out.println("flex column count = " + cols);
//	        
//	        // The resultset is a cursor into the data.  You can only point to one row at a time, and don't know the row count.
//	        
//	        // Assume we are pointing to BEFORE the first row rs.next() points to next row and returns true
//	        // or false if there is no next row, which breaks the loop
//	        for (; resultset.next(); ) {
//	        	GenericRow row = new GenericRow();
//	        	// Programming col index, zero-based.
//	            for (int i = 0; i < cols; i++) {
//	            	// SQL columns are 1-based, for humans
//	            	int sql_col = i + 1;
//	            	// Info about the column itself
//	            	String sql_col_name = meta.getColumnName(sql_col);
//
//	            	
//	            	// Actual data value in this column
//	                Object sql_col_value = resultset.getObject(sql_col);	    // SQL columns start at "1" not "0"
//	                if (sql_col_value != null) {
////	                	System.out.print(sql_col_value.toString() + " ");
//	                }
//	                else {
////	                	System.out.print("<null>" + " ");
//	                }
//	                
//	                // Put the value into the correct field of the row object.
//	                // In this case, we have the info structure.
//	                copySQLColumnToGenericField(sql_col_name, sql_col_value, row, info);
//	            }
////	            System.out.println(" ");
//	            
//	            result.addTask(row);
//	        }
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//		}
//
////        SS.log("copyToArray() time = " + woof.elapsed());
//        return result;
//    }
//    
//    void copySQLColumnToGenericField(String sql_col_name, Object sql_col_value, GenericRow row, GenericTableInfoList info)
//    {
//    	// SQL field names are in all upper case (at least in hyperSQL implementation)
//    	// Generic fields might be caps or not
//    	
//    	// Look at the Java type the caller expected for this column.
//    	int column = row.data.size();
//    	if (info.size() <= column) {
//    		// Trouble, the caller didn't expect this many columns.
//    		logger.error("Flex failed, expected " + info.size() + " columns only. " + sql_col_name);
//    		return;
//    	}
//    	
//    	Class c = info.get(column).dataType;
//    	if (c.equals(String.class)) {
//    		if (sql_col_value instanceof String) {
//    			row.data.add((String)sql_col_value);
//    		}
//    		else {
//    			logger.error("Flex failed, expected String for " + sql_col_name);
//    			return;
//    		}
//    	}
//    	else if (c.equals(Integer.class)) {
//    		if (sql_col_value instanceof Integer) {
//    			row.data.add((Integer)sql_col_value);
//    		}
//    		else if (sql_col_value instanceof Long) {
//    			row.data.add(((Long)sql_col_value).intValue());
//    		}
//    		// TODO add short/long/etc
//    		else {
//    			logger.error("Flex failed, expected Integer for " + sql_col_name);
//    			return;
//    		}
//    	}
//    	else if (c.equals(LocalDate.class)) {
//    		if (sql_col_value instanceof java.sql.Date) {
//    			LocalDate woof = ((java.sql.Date)(sql_col_value)).toLocalDate();
//    			row.data.add(woof);
//    		}
//    		else {
//    			logger.error("Flex failed, expected Date for " + sql_col_name);
//    			return;
//    		}
//    	}
//    	else {
//    		logger.error("Flex failed, unknown info dataType " + c.getSimpleName());
//    		return;
//    	}
//
//    }
//    
//    public static void dump(ResultSet rs) throws SQLException {
//
//        // the order of the rows in a cursor
//        // are implementation dependent unless you use the SQL ORDER statement
//        ResultSetMetaData meta   = rs.getMetaData();
//        int               colmax = meta.getColumnCount();
//        int               i;
//        Object            o = null;
//
//        System.out.println("column count = " + meta.getColumnCount());
//        
//        // the result set is a cursor into the data.  You can only
//        // point to one row at a time
//        // assume we are pointing to BEFORE the first row
//        // rs.next() points to next row and returns true
//        // or false if there is no next row, which breaks the loop
//        for (; rs.next(); ) {
//            for (i = 0; i < colmax; ++i) {
//                o = rs.getObject(i + 1);    // SQL columns start at "1" not "0"
//                System.out.print(o.toString() + " ");
//            }
//
//            System.out.println(" ");
//        }
//    }              
//
//
//    void copySQLColumnToJavaField(String sql_col_name, Object sql_col_value, IBQuoteRow row)
//    {
//    	// SQL field names are in all upper case (at least in hyperSQL implementation)
//    	// Java fields in the row are all lower case (MGC preference)
//    	Class c = IBQuoteRow.class;
//    	Field field;
//		try {
//			String java_col_name = sql_col_name.toLowerCase();
//			field = c.getDeclaredField(java_col_name);
//			
//			if (sql_col_value == null) {
//				// Assume that any column in the SQL that has a null means to use the default value in the Java struct.
//				// This would typically be zero for int, null for string, etc.
//				return;
//			}
//			
//			// Fix up types
//			if (sql_col_value instanceof BigDecimal) {
//				// SQL is in fixed point decimal, so convert to native double
//		  	    field.set(row, ((BigDecimal)sql_col_value).doubleValue());
//			}
//			else {
//				// The internal code does a decent job of converting long/int/short, and char/string stuff.	 TODO may need other conversions DATE -> LocalDate??
//				field.set(row, sql_col_value);
//			}
//		}
//		catch (Exception e)
//		{
//			e.printStackTrace();
//		}
//    }
//   
}
