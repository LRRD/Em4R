package com.elmocity.elib.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for a set of preferences (defined by an enum).  This loads, holds and saves the set to disk.
 * <p>
 * To use, -extend- this class, creating an inner enum in your class, and feed that enum to this constructor via -super-.
 * <br>
 * Then override init() to create each pref definition, one per enum value.
 */
public abstract class PrefsBase <T extends Enum<T>>
{
	protected PreferenceStore store;
	private Class<T> enumClass;
	private String nodeName;

	// NOTE this is a little odd to have a static logger for a templated class.  This means all derived classes will share one logger.
	static final Logger logger = LoggerFactory.getLogger(PrefsBase.class);

	
	protected PrefsBase(Class<T> enumClass, String nodeName)
	{
		this.enumClass = enumClass;
		this.nodeName = nodeName;

		store = new PreferenceStore();
		init();
	}

	// This creates a definition for each key/value allowed in the store.
	// We cannot do this here in the base class, by iterating the enum, because the user needs to supply type info and default values etc.
	abstract protected void init();
	

	abstract protected String makePrefsFullPath(String prefsName);
//	{
//		String filename = nodeName + "_" + prefsName + ".properties";
//		String fullPath = FileIO.getFullPath(SSFileIO.Subfolder.pref, filename);
//		return fullPath;
//	}
	
	/**
	 * Given an enum value, figure out what Class of Object we expect to get/put/save in the store.
	 */
	public Class expectedValueClass(T e)
	{
		Class c = store.getValueClass(e.name());
		return c;
	}
	
	// -------------------------------

	// TODO LocalDate and LocalTime
	public void setPref(T key, Object value)
	{
		// Preferred data type
		Class c = store.getValueClass(key.name());
		if (c == null) {
			return;
		}
		if (value.getClass() != c) {

			if (c == Integer.class && value.getClass() == Double.class) {
				// The user entered Double data into an Integer field
				value = new Integer(((Double)value).intValue());
			}
			else if (c == Double.class && value.getClass() == Integer.class) {
				// The user entered Integer data into an Double field
				value = new Double(((Integer)value).doubleValue());
			}
			else if (c == Integer.class && value.getClass() == String.class) {
				value = Integer.valueOf((String) value);
			}
			else if (c == Double.class && value.getClass() == String.class) {
				value = Double.valueOf((String) value);
			}
			else if (c == Boolean.class && value.getClass() == String.class) {
				value = Boolean.valueOf((String) value);
			}
			else {
				logger.error("data type mismatch for key {}, expected {} but user passed in {}, no conversion",
												key.name(), c.getName(), value.getClass().getName());
				return;
			}
			
			// TODO this needs to check the min and max values? since a human could edit the text prop files with an illegal value
		}

		store.setPref(key.name(), value);
	}

	//		public Object getPref(SBPrefKey key)
	//		{
	//			return m_store.getPref(key.name());
	//		}

	public <TOBJ> TOBJ getPref(Enum<T> key)
	{
		// Find out what class the store is using for this pref
		Class c = store.getValueClass(key.name());
		if (c == null) {
			return null;
		}

		// Get the value inside a generic object, that will really be of class c internally
		Object o = store.getPref(key.name());
		// TODO test for null? not allowed to store nulls in the PrefStore?

		// Verify that class c (or object o) are really the same as the template class before we return it
		TOBJ woof = null;
		try {
			woof = (TOBJ) o;		// Totally unsafe.
		}
		catch (ClassCastException e) {
			logger.error("data type mismatch reading pref {}, should have been {}", key.name(), c.getName());
			logger.error("cast exception", e);
		}

		return woof;
	}
//
//
//	protected void log(String s)
//	{
//		SS.log(s);
//	}



	/**
	 * Save the store to disk.
	 * @param prefName
	 * A unique name string, usually human readable, not the actual full filename.  
	 */
	public void save(String prefName)
	{
		// Build up a properties object with all the data in pairs
		Properties props = new Properties();
		
		T[] keys = enumClass.getEnumConstants();
		for (int i = 0; i < keys.length; i++) {
			T key = keys[i];
			Object value = getPref(key);

			props.setProperty(key.name(), value.toString());
			// SS.log("" + key.name() + " = " + value.toString());
		}

		// Write the object out to a file
		String fullPath = makePrefsFullPath(prefName);
		try {
			// TODO should be FileIO calls
			File f = new File(fullPath);
			OutputStream out = new FileOutputStream(f);
			props.store(out, "SS Prefs File v3.0, Node " + nodeName);

			out.flush();
			out.close();
		}
		catch (Exception e)
		{
			logger.error("file exception", e);
		}
	}

	public void load(String prefName)
	{
		// Find the file from the prefs folder
		String fullPath = makePrefsFullPath(prefName);
		InputStream is = null;
		try {
			// TODO should be FileIO Calls
			File f = new File(fullPath);
			is = new FileInputStream(f);
		}
		catch (Exception e)
		{
			logger.error("file exception", e);
			return;
		}

		// Read the file into a Properties object as pairs
		Properties props = new Properties();
		try {
			props.load( is );
			is.close();
		}
		catch (Exception e)
		{
			logger.error("file exception", e);
			return;
		}

		T[] keys = enumClass.getEnumConstants();
		for (int i = 0; i < keys.length; i++) {
			T key = keys[i];
			String value = props.getProperty(key.name());
			if (value == null) {
				logger.warn("prefs file {} is missing key {}", prefName, key.name());		// Not really an error, since pref keys have default values
			}
			else {
				setPref(key, value);
			}
		}
	}
}
