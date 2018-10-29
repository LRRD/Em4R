package com.elmocity.elib.swt;

import java.lang.reflect.Field;
import java.net.URL;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImageCache
{
	// One image registry for the whole application
	static final ExtendedImageRegistry imageRegistry = new ExtendedImageRegistry();
	
	// Path, relative to the main class (top of jar).
	// TODO verify that this works in both eclipse development and in a deployed jar file.
	// In ECLIPSE: the build path includes a folder called "res", which makes the contents appear at the top level of the application.
	// In Jar: TODO not sure.
	static final String iconPath = "/images/";
	
	// Name of each image used, including file type, since there can be jpg, png, etc..
	// Filenames are also used as the key for storing/lookup when used.  Handle for debugging too
	// Probably case-sensitive for the filenames, and watch the extensions... not all png. 

	// ICONS
	public static final String ADD_ICON = "add_obj.gif";
	public static final String CLOSE_ICON = "close_view.png";
	public static final String SAVE_ICON = "save_edit.png";
	public static final String SLEEPING_ICON = "sleeping.gif";
	public static final String WAITING_ICON = "waiting.gif";

	public static final String NAV_RIGHT_ICON = "nav/Button Next.png";
	public static final String NAV_LEFT_ICON = "nav/Button Previous.png";

	public static final String NAV_SHUTTLE_FIRST_ICON = "nav/Button First.png";
	public static final String NAV_SHUTTLE_BIG_DOWN_ICON = "nav/Button Previous.png";
	public static final String NAV_SHUTTLE_LITTLE_DOWN_ICON = "nav/Button Previous.png";
	public static final String NAV_SHUTTLE_STOP_ICON = "nav/Button Stop.png";
	public static final String NAV_SHUTTLE_LITTLE_UP_ICON = "nav/Button Next.png";
	public static final String NAV_SHUTTLE_BIG_UP_ICON = "nav/Button Next.png";
	public static final String NAV_SHUTTLE_LAST_ICON = "nav/Button Last.png";

	public static final String NAV_RESET_ICON = "nav/Button First.png";
	public static final String NAV_PAUSE_ICON = "nav/Button Pause.png";
	public static final String NAV_RUN_ICON = "nav/Button Play.png";

	
	// DOTW ICONS - dynamically created and inserted
	public static final String MONDAY_ICON = "mon_icon";	// no file representation
	public static final String TUESDAY_ICON = "tue_icon";	// no file representation
	public static final String WEDNESDAY_ICON = "wed_icon";	// no file representation
	public static final String THURSDAY_ICON = "thu_icon";	// no file representation
	public static final String FRIDAY_ICON = "fri_icon";	// no file representation

	// IMAGES (large)
	public static final String LITTLE_RIVER_IMAGE = "little_river_logo.png";	// white background right up to edge, so needs white container

	
	static final Logger logger = LoggerFactory.getLogger(ImageCache.class);

	
	static
	{
		String[] list = {
			ADD_ICON,
			CLOSE_ICON,
			SAVE_ICON,
			SLEEPING_ICON,
			WAITING_ICON,
			
			NAV_RIGHT_ICON,
			NAV_LEFT_ICON,
			
			NAV_SHUTTLE_FIRST_ICON,
			NAV_SHUTTLE_BIG_DOWN_ICON, 
			NAV_SHUTTLE_LITTLE_DOWN_ICON,
			NAV_SHUTTLE_STOP_ICON,
			NAV_SHUTTLE_LITTLE_UP_ICON,
			NAV_SHUTTLE_BIG_UP_ICON,
			NAV_SHUTTLE_LAST_ICON,

			NAV_RESET_ICON,
			NAV_PAUSE_ICON,
			NAV_RUN_ICON,

			LITTLE_RIVER_IMAGE,
		};
		
		int worked = 0;
		for (int i = 0; i < list.length; i++) {
			try {
//				String fullPath = iconPath + list[i];
//				URL imgURL = topClass.getResource(fullPath);
//				if (imgURL == null) {
//					logger.error("failed to find image resource '" + fullPath + "'");
//					continue;
//				}
//	
//				imageRegistry.put(list[i], ImageDescriptor.createFromURL(imgURL));
//				worked++;
				
				Image image = importImageFromBundle(list[i]);
				if (image != null) {
					imageRegistry.put(list[i], image);		// imageRegistry takes over management
					worked++;
				}
			}
			catch (Exception e)
			{
				logger.error("failed with exception on image resource {}", i);
			}
		}
		if (worked == list.length) {
			logger.info("loaded all {} requested resources from jar", list.length);
		}
		else {
			logger.error("loaded {} of {} resources from jar", worked, list.length);
		}
		
		createDayOfWeekImages();
		
//		imageRegistry.getTable();
	}
	
	public static Image get(String key)
	{
		Image image = imageRegistry.get(key);
		if (image == null) {
			logger.warn("caller asked for an image that doesn't exist in registry {}", key);
		}
		return image;
	}

	/**
	 * Fetch an image from the registry by key, then scale the image (usually down) to the provided size without trashing transparency.
	 * <p>
	 * Scaling the image is easy (using GC antialiasing) and maintaining transparency is easy (direct SWT use), but doing both at
	 * the same time is not trivial.
	 * <p>
	 * LIKELY LEAKs  The resulting image is not placed in the registry, the caller is responsible to dispose of it later.<br>
	 * LIKELY TRANSPARENCY FAILS  Any specific file could fail, as PNG/GIF/etc use pixel values, masks, etc differently.  Verify.
	 */
	public static Image getScaled(String key, int width, int height)
	{
		Image image = imageRegistry.get(key);
		if (image == null) {
			logger.warn("caller asked for an image that doesn't exist in registry {}", key);
			return null;
		}
		
		final int imageWidth = image.getBounds().width;
		final int imageHeight = image.getBounds().height;

		// TODO zoom not used, so aspect ratio not maintained..  Cry to someone else.
		
		// Compute our scale factor for the resize, we will maintain aspect ratio.
		final double zoom = Math.min((1.0 * width / imageWidth), (1.0 * height / imageHeight));

		// TODO this may or may not work on all variations of PNG/JPG/etc files, depending on if it uses different 
		// transparency modes like ALPHA, PIXEL or MASK.  It seems to always work, creating a mask if not natively present.
		ImageData imageData = image.getImageData();
		ImageData mask = imageData.getTransparencyMask();

		
		Image scaledImage = new Image(Display.getDefault(), width, height);
		{
			GC gc = new GC(scaledImage);
			gc.setAntialias(SWT.ON);
			gc.setInterpolation(SWT.HIGH);
			gc.drawImage(image, 0, 0, image.getBounds().width, image.getBounds().height, 0, 0, width, height);
			gc.dispose();
		}
		
		Image scaledMask = new Image(Display.getDefault(), width, height);
		{
			GC gc = new GC(scaledMask);
			gc.setAntialias(SWT.ON);
			gc.setInterpolation(SWT.HIGH);
			gc.drawImage(image, 0, 0, image.getBounds().width, image.getBounds().height, 0, 0, width, height);
			gc.dispose();
		}

		// Assemble the image and mask to re-create a transparent image;
		Image combined = new Image(Display.getDefault(), scaledImage.getImageData(), scaledMask.getImageData());
		return combined;
	}

	private static void createDayOfWeekImages()
	{
		final int SIZE_X = 10;
		final int SIZE_Y = 10;
	
		DayOfWeek[] dayOfWeeks = DayOfWeek.values();
		for (int i = 0; i < dayOfWeeks.length; i++) {
		    DayOfWeek dotw = dayOfWeeks[i];
		    if (dotw.equals(DayOfWeek.SATURDAY) || dotw.equals(DayOfWeek.SUNDAY)) {
		    	continue;
		    }
		    
			// This all assumes 24 bit color and 0xAABBGGRR
			PaletteData palette = new PaletteData(0xFF , 0xFF00 , 0xFF0000);
			ImageData imageData = new ImageData(SIZE_X, SIZE_Y, 24, palette);
	
			Color c = ColorCache.getColorByDOTW(dotw);
			int pixel = c.getRed() + (c.getGreen() << 8) + (c.getBlue() << 16);
			
			for (int x = 0; x < SIZE_X ; x++) {
				for(int y = 0; y < SIZE_Y; y++) {
					imageData.setPixel(x, y, pixel);
				}
			}
	
		    Image image = new Image(Display.getDefault(), imageData);
		    imageRegistry.put(getImageKey(dotw), image);
		}
		
		logger.info("created dotw images");
	}
	
	/**
	 * Given a day of the week, return the name of the image (color block) for that day of that week.
	 * <br>
	 * Useful for programmatically creating legends or 5 checkboxes etc. 
	 */
	public static String getImageKey(DayOfWeek dotw)
	{
		switch (dotw) {
		case MONDAY:	return MONDAY_ICON;
		case TUESDAY:	return TUESDAY_ICON;
		case WEDNESDAY:	return WEDNESDAY_ICON;
		case THURSDAY:	return THURSDAY_ICON;
		case FRIDAY:	return FRIDAY_ICON;
		}
		
		logger.warn("missing dotw image for {}", dotw);	// might be null?
		return null;
	}
	
	public static Set<String> getImageKeys()
	{
		
		return imageRegistry.getKeySet();
	}

	// ------------------------------------------------------
	
	/**
	 * Hunt down an image resource file that is inside our own bundle.  Create an image object and return it for placement
	 * into the registry.  The registry takes over management of the resource and will dispose internally as needed.
	 * <p>
	 * TODO this bundle should only have generic icons and images that would be useful for any given application.
	 * Application specific files (like logos and background images) need a methodology that can load those from
	 * other (app domain) bundles for management here.  HACK for now.
	 * <br>
	 * @param imageName   Just the plain name of the file (including extension) like "woof.jpg"
	 */
	private static Image importImageFromBundle(String imageName)
	{
		// Many reasons this could explode, from build issues, deploy mistakes, platform differences win vs linux, you name it.  Be safe.
		try {
			final String separator = "/";		// TODO I think this works on Linux too, since it is a Jar path, not OS
			
			// Path, relative to the main class (top of jar).
			// TODO verify that this works in both eclipse development and in a deployed jar file.
			// In ECLIPSE: the build path includes a folder called "res", which makes the contents appear at the top level of the application.
			// In Jar: TODO not sure.
			final String rootPath = "images" + separator;		// Assumes a top level folder in the jar named "images" (NO LEADING SLASH)
			final String fullPath = rootPath + separator + imageName;
	
			// TODO could pass in the bundle where we think the image is located
			final String pluginID = "com.elmocity.elib.swt";	// TODO
			Bundle bundle = Platform.getBundle(pluginID);
			if (bundle == null) {
				logger.warn("failed to load image {}, bad bundle {}", imageName, pluginID);
				return null;
			}
	
			// This will return a funny "bundle-resource" protocol
			URL url = FileLocator.find(bundle, new Path(fullPath), null);
			if (url == null) {
				logger.warn("failed to load image {}, bad path {}", imageName, fullPath);
				return null;
			}
	
			// NOTE createFromURL can't fail, as SWT internally makes up a MissingImageDescriptor with a blank image
			ImageDescriptor imageDesc = ImageDescriptor.createFromURL(url);
			if (imageDesc == null) {
				// Impossible?
				return null;
			}
	//		if (imageDesc instanceof org.eclipse.jface.resource.MissingImageDescriptor) {
	//			return null;
	//		}
	
			Image image = imageDesc.createImage();
			// Can return null on "extreme" failures, like out of memory or handles or maybe with embedded display LCD bit-depth etc.
			
			return image;
		}
		catch (Exception e)	{
			logger.warn("failed to load image {}, reason {}", imageName, e.getMessage());
		}
		
		return null;
	}
}
