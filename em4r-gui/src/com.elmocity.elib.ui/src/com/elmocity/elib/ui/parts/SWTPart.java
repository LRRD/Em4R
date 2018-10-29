
package com.elmocity.elib.ui.parts;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimmedWindow;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.model.application.ui.menu.MMenu;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.swt.ColorCache;
import com.elmocity.elib.swt.FontCache;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.ImageCache;
import com.elmocity.elib.swt.MonitorInfo;
import com.elmocity.elib.swt.SimpleConsole;

public class SWTPart
{
	SimpleConsole console;
	Button showDisplayInfo;
	Button showCacheButton;

	private static final Logger logger = LoggerFactory.getLogger(SWTPart.class);

//	@Inject
//	EModelService modelService;
//	
//	@Inject
//	MApplication application;


	
	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is text output console
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "ELib Main", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			showDisplayInfo = GridHelpers.addButtonToGrid(inputComposite, "Show Display Info");
			showDisplayInfo.addSelectionListener(new ShowDisplayInfoListener());

			showCacheButton = GridHelpers.addButtonToGrid(inputComposite, "Show Registry Cache");
			showCacheButton.addSelectionListener(new ShowCacheListener());
		}

		// RIGHT
		Composite consoleComposite = GridHelpers.addGridPanel(c, 1);
		{
			// Right side is a console text area, since the lines of text are short, but numerous
			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			consoleComposite.setLayoutData(gridData);

			console = new SimpleConsole(consoleComposite, SWT.NONE, 10);
		}

//		MonitorInfo.debugMonitors();
//		MonitorInfo.moveDebugShellToMonitor(1);
	}

	@PreDestroy
	public void preDestroy()
	{
		
	}

	@Focus
	public void onFocus() 
	{
		console.setFocus();
	}
	
	// -----------------
	
	class ShowDisplayInfoListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append(MonitorInfo.debugMonitors());

// We can get access to the main menu on the main window this way... but there doesn't appear to be a way to change color.
//			if (application != null) {
//				MTrimmedWindow window = (MTrimmedWindow)modelService.find("com.elmocity.elib.main.window.main", application);
//				
//				MMenu mainMenu = window.getMainMenu();
//				
//				mainMenu.setLabel("woof");
//			}
				
// If modifying the main menu, may need to force it to replot
//				// retrieve the main menu, containing the top-level menu element that needs to be refreshed
//				if (list != null && list.size() > 0) {
//					MMenu mainMenu = list.get(0);
////					// org.eclipse.e4.ui.workbench.renderers.swt.MenuManagerRenderer
////					MenuManagerRender renderer = (MenuManagerRenderer)mainMenu.getRenderer();
////					renderer.getManager(mainMenu).update(true);
//				}


			console.append("Done.");
		}
	}
	
	class ShowCacheListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			console.append("");
			console.append("Dumping SWT Registry contents...");

			// COLOR
			Set<String> colorKeys = ColorCache.getColorKeys();
			console.append("ColorCache contains " + colorKeys.size() + " keys.");
			for (String key : colorKeys) {
				console.append("   " + key );
			}

			// FONT
			Set<String> fontKeys = FontCache.getFontKeys();
			console.append("FontCache contains " + fontKeys.size() + " keys.");
			for (String key : fontKeys) {
				console.append("   " + key );
			}

			// IMAGE
			Set<String> imageKeys = ImageCache.getImageKeys();
			console.append("ImageCache contains " + imageKeys.size() + " keys.");
			for (String key : imageKeys) {
				console.append("   " + key );
			}

			console.append("Done.");
		}
	}

}