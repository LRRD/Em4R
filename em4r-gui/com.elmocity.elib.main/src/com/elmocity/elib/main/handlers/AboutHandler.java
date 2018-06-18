package com.elmocity.elib.main.handlers;

import java.time.LocalTime;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.swt.ColorCache;
import com.elmocity.elib.swt.ImageCache;
import com.elmocity.elib.util.Calc;
import com.elmocity.elib.util.Format;

public class AboutHandler
{
	private final static Logger logger = LoggerFactory.getLogger(AboutHandler.class);

	@Execute
	public void execute(Shell shell)
	{
		String message = "ELib Test Product";
		
		String s = Format.time(LocalTime.now());
		logger.info("The current machine time is " + s);

		// Read some RAM values of the VM to verify the util plugin is loaded
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		long allocatedMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		message += "\n";
		message += "\nTotal RAM " + Format.bytesToGBString(maxMemory);
		message += "\nInUse RAM " + Format.bytesToGBString(allocatedMemory);
		message += "\n";

		MyCustomMessageDialog dialog = new MyCustomMessageDialog(shell, "About", null, message, MessageDialog.INFORMATION, 0, new String[] {"OK"});
		dialog.open();
		
//		MyCustomMessageDialog.open(MessageDialog.INFORMATION, shell, "About", message, SWT.NONE);
//		MessageDialog.openInformation(shell, "About", message);
	}
	
	class MyCustomMessageDialog extends MessageDialog
	{
		public MyCustomMessageDialog(Shell parentShell, String dialogTitle, Image dialogTitleImage,
				String dialogMessage, int dialogImageType, int defaultIndex, String[] dialogButtonLabels)
		{
			super(parentShell, dialogTitle, dialogTitleImage, dialogMessage, dialogImageType, defaultIndex, dialogButtonLabels);

			// TODO not clear if this gets set correctly since we overrode the constructor 			
			//setShellStyle(getShellStyle() | SWT.SHEET);
		}

		
		@Override
		protected Control createCustomArea(Composite parent)
		{
			// Load the image of the logo from the cache
			Image logo = ImageCache.get(ImageCache.LITTLE_RIVER_IMAGE);

			Composite c = new Composite(parent, SWT.NONE);
			GridLayout gridLayout = new GridLayout();
		    gridLayout.numColumns = 1;
		    gridLayout.makeColumnsEqualWidth = false;
		    c.setLayout(gridLayout);			

		    // The logo image that we grabbed from the web has transparent background, and wants to be on a white background.
		    // So use a CLabel to set some padding around the sides of the image, and toss on a border too.
			CLabel label = new CLabel(c, SWT.BORDER);
			int padding = 25;
			label.setMargins(padding, padding, padding, padding);
			label.setImage(logo);
			label.setBackground(ColorCache.getColor(ColorCache.WHITE));
			
		    Link link = new Link(c, SWT.NONE);
		    link.setText("Copyright and link <a>www.emriver.com</a>");

			// Event handling when users click on link.
			link.addSelectionListener(new SelectionAdapter() {
			    @Override
			    public void widgetSelected(SelectionEvent e) {
			        Program.launch("http:/www.emriver.com");
			    }
			});
			
			return c;
		}
	}
	
	
}
