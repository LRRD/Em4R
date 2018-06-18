package com.elmocity.elib.swt;

import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

public class StatusAreaControl
{
	@Inject
	private IEventBroker eventBroker; 
	private static final String STATUSBAR ="statusbar";
	
	@Inject @Optional
	public void  getEvent(@UIEventTopic(STATUSBAR) String message) {
	    updateInterface(message); 
	}

	private final UISynchronize sync;

	@Inject
	public StatusAreaControl(UISynchronize sync) {
		this.sync = Objects.requireNonNull(sync);
	}	
	
	
	CLabel statusLabel;
	CLabel errorLabel;
	ProgressMonitorControl prog;
	
	
	
	@PostConstruct
	public void createControls(Composite parent)
	{
		final int desiredHeight = 20;
		
		// The parent is a raw trim bar that doesn't have a layout manager
		parent.setLayout(new FillLayout());
		
		Composite c = new Composite(parent, SWT.BORDER);
		GridLayout gridLayout = new GridLayout(3, false);
		c.setLayout(gridLayout);

//		c.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		statusLabel = new CLabel(c, SWT.BORDER);
		{
			GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
			gd.heightHint = desiredHeight;
//			gd.widthHint = 300;	// don't set width, since we asked for this control to grab excess horz space
			gd.grabExcessHorizontalSpace = true;
			statusLabel.setLayoutData(gd);
			statusLabel.setText("Status.");
		}
		
		errorLabel = new CLabel(c, SWT.BORDER);
		{
			GridData gd = new GridData(SWT.FILL, SWT.FILL, false, true);
			gd.heightHint = desiredHeight;
			gd.widthHint = 200;
			errorLabel.setLayoutData(gd);
			errorLabel.setText("No errors.");
		}
		
		prog = new ProgressMonitorControl(sync);
		prog.createControls(c);
		{
			GridData gd = new GridData(SWT.FILL, SWT.FILL, false, true);
			gd.heightHint = desiredHeight;
			gd.widthHint = 50;
			prog.setLayoutData(gd);
		}

	}
	
	public void updateInterface(String message)
	{
		try {
			sync.asyncExec(new Runnable() {
				@Override
				public void run() {
					try{
						statusLabel.setText(message);  
					}
					catch(Exception e) {
						System.out.println(e);
					}               
				}
			});
		}
		catch(Exception exception) {
			System.out.println(exception);
		}   
	}
}