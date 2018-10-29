package com.elmocity.elib.util;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO this needs to be updated to handle LocalTime and LocalDate values.

/**
 * Container class to hold actual preference values in memory.
 */
public class PreferenceStore
{
	static final Logger logger = LoggerFactory.getLogger(PreferenceStore.class);

	private class Preference
	{
		String m_tag;

		Class m_value_class;
		Object m_min_value;
		Object m_max_value;
		Object m_value;
		
		// TODO, might be some fancy way to template this method to assure that min, max and value are all of class value_class.
		Preference(String tag, Class value_class, Object min, Object max, Object value)
		{
			m_tag = tag;

			m_value_class = value_class;
			m_min_value = min;
			m_max_value = max;
			m_value = value;
		}
	}
	
	private HashMap<String, Preference> m_prefs = new HashMap<String, Preference>(30);
	
	
	public PreferenceStore()
	{
	}

	
	public void createPref(String tag, Class value_class, Object min, Object max, Object initial_value)
	{
		// Make sure this pref has not been created already
		if (m_prefs.get(tag) != null) {
			// Already have an entry for this tag, don't create a 2nd!
			logger.error("attempting to create duplicate pref tag {}", tag);
			return;
		}
		
		// Verify that all the values are of the same class type
		if ((min.getClass() != value_class) ||
			(max.getClass() != value_class) ||
			(initial_value.getClass() != value_class)) {
			// All the values should be the same type from the caller
			logger.error("prefs min, max and initial values are not all the same type, tag {}", tag);
			return;
		}
		
		// Add this to the store
		Preference p = new Preference(tag, value_class, min, max, initial_value);
		m_prefs.put(tag, p);
	}
	
	public void setPref(String tag, Object value)
	{
		// Find the pref in the store, and just update the value field.
		// Should error out if the pref was never created before, not auto-create.
		Preference p = m_prefs.get(tag);
		if (p == null) {
			// Missing tag, someone forgot to pre-create it for constraints
			logger.error("can't set pref value before it is created {} {}", tag, value);
			return;
		}

		// Verify the value is in range
		if (p.m_value_class == Boolean.class) {
			// No check, the class doesn't allow unknown values
		}
		else if (p.m_value_class == String.class) {
			// No check, could check length I guess		// TODO min max undefined, should be max length?
		}
		else if (p.m_value_class == Double.class) {
			Double v = (Double)value;
			if (v < (Double)p.m_min_value) {
				v = (Double)p.m_min_value;
			}
			else if (v > (Double)p.m_max_value) {
				v = (Double)p.m_max_value;
			}
			value = v;
		}
		else if (p.m_value_class == Integer.class) {
			Integer v = (Integer)value;
			if (v < (Integer)p.m_min_value) {
				v = (Integer)p.m_min_value;
			}
			else if (v > (Integer)p.m_max_value) {
				v = (Integer)p.m_max_value;
			}
			value = v;
		}
		else {
			logger.error("unchecked value class {} for tag {}", p.m_value_class.getName(), tag);
		}
		
		// Overwrite the old value
		p.m_value = value;
	}
	
	public Object getPref(String tag)
	{
		// Find the pref in the store
		Preference p = m_prefs.get(tag);
		if (p == null) {
			// Missing tag, someone forgot to pre-create it for constraints
			logger.error("can't get pref value before it is created {}", tag);
			return null;
		}

		return p.m_value;
	}
	
	public Class getValueClass(String tag)
	{
		// Find the pref in the store
		Preference p = m_prefs.get(tag);
		if (p == null) {
			// Missing tag, someone forgot to pre-create it for constraints
			logger.error("can't get pref value class before it is created {}", tag);
			return null;
		}

		return p.m_value_class;
	}
}
