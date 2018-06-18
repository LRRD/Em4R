package com.elmocity.elib.util;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic file handling class that should be used by all application code to manipulate files that are outside the deployment jars, such as config or prefs files.
 * Should handle Windows and Linux.
 * <p>
 * Terminology:<br>
 * fullPath = complete path and filename like "C:\TEMP\woof.txt"<br>
 * filePath = just the path, usually not containing the trailing slash like "C:\TEMP"<br>
 * filename = just the filename itself, like "woof.txt"<br>
 * thus a "fullPath" is usually a "filePath" + a separator + "filename"
 */
public class FileIO
{
	// TODO this entire class could be rewritten using java.nio to get modern access to UNIX and POSIX file attributes etc
	// TODO this class assumes it is working with "small" preference or config files, not 10 GB database files. 
	
	// TODO verify and adjust for linux/mac too
	public final static String separator = File.separator;

	private static final Logger logger = LoggerFactory.getLogger(FileIO.class);
	
	
	public static BufferedWriter createFile(String fullPath)
	{
        BufferedWriter output = null;
        try {
            File file = new File(fullPath);
            if (file.exists()) {
            	// Already exists - which may be a file or a directory, so bail out
            	logger.warn("file already exists, aborting {}", fullPath);
            	return null;
            }
            output = new BufferedWriter(new FileWriter(file));
        }
        catch (IOException e) {
            e.printStackTrace();
        	logger.warn("file creation exception {}", fullPath);
            output = null;
        }
        
		return output;
	}
	
	public static void writeFile(BufferedWriter output, String text)
	{
        try {
            output.write(text);		// should could also be append(), same
        }
        catch (IOException e) {
           	logger.warn("file write exception {}", output.toString());
           	e.printStackTrace();
        }
	}
	
	public static void closeFile(BufferedWriter output)
	{
        if (output != null) {
			try {
				output.close();
			}
        	catch (IOException e) {
               	logger.warn("file close exception {}", output.toString());
				e.printStackTrace();
			}
        }
	}
	
	public static boolean fileExists(String fullPath)
	{
        File file = new File(fullPath);
		if (file.exists()) {
			// Already exists - which may be a file or a directory
			return true;
		}
        
		return false;
	}

	// ---------------

//	static final String sSharedDataFolder = "C:\\StockStranglerShare\\ROOT_FOLDER\\";
//
//	static void copyDataFile(Subfolder subfolder, String filename)
//	{
//		// Setup the source file
//		File input_file = new File(getFullPath(subfolder, filename));
//
//		// Setup the destination file
//		
//		// Get today's date, and create a folder with that name, in the share folder
//		String output_folder_name = SS1_DateTime.getToday(SS1_DateTime.df1) + "_dailydata";
//		File output_folder_path = new File(sSharedDataFolder + output_folder_name);
//		output_folder_path.mkdir();
//		
//		// Make the folder
//		String output_path = sSharedDataFolder + output_folder_name + sep + filename;
//		File output_file = new File(output_path);
//		output_file.getParentFile().mkdir();
//
//		
//		// Finally copy the real file
//		try {
//			rawCopyFile(input_file, output_file);
//		}
//		catch (IOException e) {
//			e.printStackTrace();
//		}
//		
//	}
	
	public static void rawCopyFile(File sourceFile, File destFile) throws IOException
	{
	    if(!destFile.exists()) {
	        destFile.createNewFile();
	    }

	    FileChannel source = null;
	    FileChannel destination = null;

	    try {
	        source = new FileInputStream(sourceFile).getChannel();
	        destination = new FileOutputStream(destFile).getChannel();
	        destination.transferFrom(source, 0, source.size());
	    }
	    finally {
	        if(source != null) {
	            source.close();
	        }
	        if(destination != null) {
	            destination.close();
	        }
	    }
	}
	
	// ----------------------------------------------------------
	public static String unitTest()
	{
		String fullPath = "C:\\TEMP";
		logger.debug("using path separator of {}", FileIO.separator);
		
		boolean exists = FileIO.fileExists(fullPath);
		logger.debug("does {} exist? {}", fullPath, exists);
		
		return "";
	}
	
}
