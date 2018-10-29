package com.elmocity.ss.database;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Historical Quote DAO layer.
 * <p>
 * Use Spring-like "standard" CRUD naming: create find(read) update delete
 * 
 */
public class HistQuoteDAO
{
	static Logger logger = LoggerFactory.getLogger(HistQuoteDAO.class);


	// TODO this is just used to get a count of the rows for a stock... over full date range!
	public static ArrayList<HistQuote> findBySecurityKey(String security_key)
	{
		String query = "select * from SS.IBQuote";
		query += " where security_key = '" + security_key + "'";
		
		return selectInternal(query);
	}

	// Find all rows on this date, should be 390 result rows.
	public static ArrayList<HistQuote> findByDate(String security_key, LocalDate date)
	{
		String query = "select * from SS.IBQuote";
		query += " where security_key = '" + security_key + "'";
		query += " and quote_date = " + SQLCore.getQuotedSQLDate(date);
		query += " order by quote_bar";		// otherwise would be random/implementation dependent
		
		return selectInternal(query);
	}

	// Find exactly one quote row, so no need for "order by" clause
	public static ArrayList<HistQuote> findByDateAndBar(String security_key, LocalDate date, int bar)
	{
		String query = "select * from SS.IBQuote";
		query += " where security_key = '" + security_key + "'";
		query += " and quote_date = " + SQLCore.getQuotedSQLDate(date);
		query += " and quote_bar = " + bar;
		
		return selectInternal(query);
	}

	// ---------------------
	
	private static ArrayList<HistQuote> selectInternal(String query)
	{
		ArrayList<HistQuote> result;

		SQLCore core = new SQLCore();
		core.query(query);
		result = copyQuotesToArray(core);
		core.close();
		
		return result;
	}

	// Suck the entire resultset into a Java array in one shot
	private static ArrayList<HistQuote> copyQuotesToArray(SQLCore core)
	{
		if (core.resultset == null) {
			logger.error("cannot copyToArray, resultset == null");
			return null;
		}

		// No way to know in advance how many rows we got back.  :-(
		ArrayList<HistQuote> result = new ArrayList<HistQuote>();

		// The resultset order is implementation dependent, so use the SQL ORDER statement, if you care.

		try {
			ResultSetMetaData meta = core.resultset.getMetaData();
			int cols = meta.getColumnCount();

//			logger.trace("column count = " + cols);

			// The resultset is a cursor into the data.  You can only point to one row at a time, and don't know the row count.

			// Assume we are pointing to BEFORE the first row rs.next() points to next row and returns true
			// or false if there is no next row, which breaks the loop.
			for (; core.resultset.next(); ) {
				HistQuote row = new HistQuote();
				// Programming col index, zero-based.
				for (int i = 0; i < cols; i++) {
					// SQL columns are 1-based, for humans
					int sql_col = i + 1;
					// Info about the column itself
					String sql_col_name = meta.getColumnName(sql_col);


					// Actual data value in this column
					Object sql_col_value = core.resultset.getObject(sql_col);
					if (sql_col_value != null) {
						//	                	System.out.print(sql_col_value.toString() + " ");
					}
					else {
						//	                	System.out.print("<null>" + " ");
					}

					// Put the value into the correct field of the row object
					copySQLColumnToJavaField(sql_col_name, sql_col_value, row);
				}
				//	            System.out.println(" ");

				result.add(row);
			}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}

		//      SS.log("copyToArray() time = " + woof.elapsed());
		return result;
	}
	
	// TODO templatize this for other SQLTable/JavaRow pairs.
	private static void copySQLColumnToJavaField(String sql_col_name, Object sql_col_value, HistQuote row)
	{
		// SQL field names are in all upper case (at least in hyperSQL implementation)
		// Java fields in the row are all lower case (MGC preference)
		Class c = HistQuote.class;
		Field field;
		try {
			String java_col_name = sql_col_name.toLowerCase();
			field = c.getDeclaredField(java_col_name);

			if (sql_col_value == null) {
				// Assume that any column in the SQL that has a null means to use the default value in the Java struct.
				// This would typically be zero for int, null for string, etc.
				return;
			}

			// Fix up types
			if (sql_col_value instanceof BigDecimal) {
				// SQL is in fixed point decimal, so convert to native double
				field.set(row, ((BigDecimal)sql_col_value).doubleValue());
			}
			else if (sql_col_value instanceof java.sql.Date) {
				// SQL is in crappy old date, so convert to modern Java 8 LocalDate
				field.set(row, ((java.sql.Date)sql_col_value).toLocalDate());
			}
			else {
				// The internal code does a decent job of converting long/int/short, and char/string stuff.
				field.set(row, sql_col_value);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// -------------
	
	final DateFormat df =  new SimpleDateFormat("yyyy-MM-dd");

	public static void insert(ArrayList<HistQuote> rows)
	{
		for (int i = 0; i < rows.size(); i++) {
			String query = "INSERT INTO SS.IBQUOTE (security_key, quote_date, quote_bar, quote_open, quote_high, quote_low, quote_close, quote_volume) VALUES " +
					makeValueString(rows.get(i));

			SQLCore core = new SQLCore();
			String result = core.update(query);
			// TODO HACK TO stop failing 390 times
			if (result.startsWith("db ")) {
				return;
			}
			// TODO need a way to return an error to the called, or even to -break- this loop to not generate 390 of the same error
//			SS.log(result);
		}
	}
	
	private static String makeValueString(HistQuote row)
	{
		String date = SQLCore.SQLdf.format(row.quote_date);
			
		// Create something like this:  "('AAPL', '2017-12-31', 4, 101.00, 101.00, 99.00, 100.00, 33)"
		String value = String.format("('%s', '%s', %d, %.02f, %.02f, %.02f, %.02f, %d)",
				row.security_key, date, row.quote_bar, row.quote_open, row.quote_high, row.quote_low, row.quote_close, row.quote_volume);
				
		return value;
	}
	
//	public void test()
//	{
//		String query_head = "INSERT INTO SS.IBSTOCKQUOTE (underlying, quote_date, quote_bar, quote_open, quote_high, quote_low, quote_close) ";
//		
//		SQLCore core;
//		String query, data;
//
//		data = "VALUES ('AAPL', '2017-12-31', 1, 101.00, 101.00, 99.00, 100.00)";
//		query = query_head + data;
//		core = new SQLCore();
//		core.update(query);
//	
//		data = "VALUES ('AAPL', '2017-12-31', 2, 102.00, 102.00, 99.00, 100.00)";
//		query = query_head + data;
//		core = new SQLCore();
//		core.update(query);
//
//		data = "VALUES ('AAPL', '2017-12-31', 3, 103.00, 103.00, 99.00, 100.00)";
//		query = query_head + data;
//		core = new SQLCore();
//		core.update(query);
//	}
	
	public void test()
	{
		String query = "INSERT INTO SS.IBQUOTE (security_key, quote_date, quote_bar, quote_open, quote_high, quote_low, quote_close, quote_volume) VALUES " +
					"('AAPL', '2017-12-31', 4, 101.00, 101.00, 99.00, 100.00, 333), " +
					"('AAPL', '2017-12-31', 5, 102.00, 102.00, 99.00, 100.00, 366), " +
					"('AAPL', '2017-12-31', 6, 103.00, 103.00, 99.00, 100.00, 399)";

		SQLCore core = new SQLCore();
		core.update(query);
	}
	
//	public void test2()
//	{
//		connect();
//		
//        try {
////			update("INSERT INTO SS.IBSTOCKQUOTE (underlying, quote_date, quote_bar, quote_open, quote_high, quote_low, quote_close) " +
////							"VALUES('AAPL', '2017-12-31', 0, 100.00, 101.00, 99.00, 100.00)");
//
////        	update(	"create table SS.WOOF (" +
////					"id integer IDENTITY PRIMARY KEY," +
////					"underlying VARCHAR(8) not null" +
////					")" );
//			update("INSERT INTO SS.WOOF (underlying) " + "VALUES('XOM')");
//		    query("select * from SS.WOOF");
//			
////		    query("select * from SS.IBSTOCKQUOTE");
//		}
//        catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//	}
//	    
//    //use for SQL command SELECT
//    public synchronized void query(String expression) throws SQLException {
//
//        Statement st = null;
//        ResultSet rs = null;
//
//        st = conn.createStatement();         // statement objects can be reused with
//
//        // repeated calls to execute but we
//        // choose to make a new one each time
//        rs = st.executeQuery(expression);    // run the query
//
//        // do something with the result set.
//        dump(rs);
//        st.close();  
//        // NOTE!! if you close a statement the associated ResultSet is closed too
//        // so you should copy the contents to some other object.
//        // the result set is invalidated also  if you recycle an Statement
//        // and try to execute some other query before the result set has been
//        // completely examined.
//    }
//
//    //use for SQL commands CREATE, DROP, INSERT and UPDATE
//    public synchronized void update(String expression) throws SQLException {
//
//        Statement st = null;
//
//        st = conn.createStatement();    // statements
//
//        int i = st.executeUpdate(expression);    // run the query
//
//        if (i == -1) {
//            System.out.println("db error : " + expression);
//        }
//
//        st.close();
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
//                o = rs.getObject(i + 1);    // Is SQL the first column is indexed
//
//                // with 1 not 0
//                System.out.print(o.toString() + " ");
//            }
//
//            System.out.println(" ");
//        }
//    }              

	public static final double FAILURE = 0.00;
	
//	/**
//	 * Return the OPENING price quote from the database for the given security/date/bar.
//	 */
//	static double getQuote(Security security, LocalDate date, int bar)
//	{
//		IBQuote woof = new IBQuote();
//		ArrayList<IBQuoteRow> rows = woof.querySecurity(security.getDBKey(), date, bar);
//		if (rows == null) {
//			logger.warn("getQuote null resultset: {} {} {}", security.underlying , date.toString(), bar);
//			return FAILURE;
//		}
//		if (rows.size() == 0) {
//			logger.warn("getQuote zero-length resultset: {} {} {}", security.underlying , date.toString(), bar);
//			return FAILURE;
//		}
//		
//		// Find the row that has our bar, return the OPENING value.
//		for (int i = 0; i < rows.size(); i++) {
//			if (rows.get(i).quote_bar == bar) {
//				return rows.get(i).quote_open;
////				return rows.get(i).quote_close;
//			}
//		}
//		
//		return FAILURE;
//	}
	
//	/**
//	 * Return the OPENING price quote from the database for the given combo/date/bar.
//	 * <p>
//	 * This returns a 0.00 price as a failure if any of the combo legs are missing from the database.
//	 */
//	static double getQuote(ComboDesc comboDesc, LocalDate date, int bar)
//	{
//		double comboPrice = 0.00;
//		for (int i = 0; i < comboDesc.legs.size(); i++) {
//			double legPrice = getQuote(comboDesc.legs.get(i), date, bar);
//			if (Helpers.double_equals(legPrice, FAILURE)) {
//				return FAILURE;	// missing data for this leg
//			}
//			comboPrice += legPrice;
//			logger.trace("getQuote combo leg: {} {}", comboDesc.legs.get(i).debugString(), Helpers.price(legPrice));
//		}
//		comboPrice = Helpers.round_to_penny(comboPrice);
//		
//		return comboPrice;
//	}
	
}

