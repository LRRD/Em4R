package com.elmocity.elib.swt;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.resource.ImageRegistry;

/**
 * Total hack to get the list of keys from the -private- inner table of the ImageRegistry object.
 * <p>
 * The SWT authors provided keySets for colors and fonts, but forgot or were lazy about providing an image keyset.
 * <br>
 * This code will surely blow up on the next release of SWT.
 */
public class ExtendedImageRegistry extends ImageRegistry
{
	public Set<String> getKeySet()
	{
		HashMap<String, Object> innerTable = new HashMap<String, Object>();

		Set<String> emptyKeySet = new HashSet<String>();
		
		try {
			Field field = ImageRegistry.class.getDeclaredField("table");
			field.setAccessible(true);
			Object value = field.get(this);
			field.setAccessible(false);

			if (value == null) {
				return null;
			}
			else if (innerTable.getClass().isAssignableFrom(value.getClass())) {
				innerTable = (HashMap<String, Object>) value;
					return innerTable.keySet();
			}
			
			// The private exists, but not of the correct type.
			return null;
		}
		catch (NoSuchFieldException e) {
			// The private does not even exist.
		}
		catch (IllegalAccessException e) {
			// The private probably exists, but there is a security manager running in the VM to stop us gaining access?
		}
		catch (Exception e) {
			// Unknown failure.
		}
		
		return emptyKeySet;
	}

}
