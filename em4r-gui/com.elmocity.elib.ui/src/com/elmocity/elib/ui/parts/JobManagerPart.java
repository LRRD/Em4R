
package com.elmocity.elib.ui.parts;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobGroup;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elmocity.elib.swt.DateFilterComposite;
import com.elmocity.elib.swt.DateFilterComposite.DateFilter;
import com.elmocity.elib.swt.GridHelpers;
import com.elmocity.elib.swt.SimpleConsole;
import com.elmocity.elib.swt.table.GenericRow;
import com.elmocity.elib.swt.table.GenericRowList;
import com.elmocity.elib.swt.table.GenericTableInfo;
import com.elmocity.elib.swt.table.GenericTableInfoList;
import com.elmocity.elib.swt.table.GenericTableViewer;
import com.elmocity.elib.ui.PartUtils;
import com.elmocity.elib.util.Format;

public class JobManagerPart
{
	private static final Logger logger = LoggerFactory.getLogger(JobManagerPart.class);

	Text familyText;
	Text forcedUpdateTimerText;
	Button executeButton;
	Button clearButton;

	SimpleConsole console;
	
	GenericRowList viewerData;
	GenericTableViewer viewer;
	

	@PostConstruct
	public void postConstruct(Composite parent)
	{
		// 2 columns, left is input parameters, and right is chart output
		Composite c = GridHelpers.addGridPanel(parent, 2);

		// LEFT
		Composite inputComposite = GridHelpers.addGridPanel(c, 2);
		{
			GridHelpers.addTitlePanel(inputComposite, "ELib Jobs", 2);
			GridData gridData = new GridData(SWT.NONE, SWT.TOP, false, false);
			inputComposite.setLayoutData(gridData);
			
			familyText = GridHelpers.addEditorToGrid(inputComposite, "Family", "");

			forcedUpdateTimerText = GridHelpers.addEditorToGrid(inputComposite, "Forced Timer", "60");	// in seconds

			executeButton = GridHelpers.addButtonToGrid(inputComposite, "Execute");
			executeButton.addSelectionListener(new ExecuteListener());

			clearButton = GridHelpers.addButtonToGrid(inputComposite, "Clear");
			clearButton.addSelectionListener(new ClearListener());					// clear the table

			// Console jammed below the buttons instead of its own area.
			// Need a shim composite to span the 2 columns used by buttons/labels.
			Composite consoleComposite = new Composite(inputComposite, SWT.NONE);
			gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			gridData.horizontalSpan = 2;
			consoleComposite.setLayoutData(gridData);
			consoleComposite.setLayout(new GridLayout(1, false));
			
			console = new SimpleConsole(consoleComposite, SWT.NONE, 20);
		}

		// RIGHT
		viewerData = new GenericRowList();
		viewer = PartUtils.makeTableComposite(c, getTableInfo(), viewerData);
	}
	
	@PreDestroy
	public void preDestroy() {
		
	}

	@Focus
	public void onFocus()
	{
		familyText.setFocus();
	}
	
	public GenericTableInfoList getTableInfo()
	{
		GenericTableInfoList list = new GenericTableInfoList();

		list.add(new GenericTableInfo("Group", 85, String.class, null, null));
		list.add(new GenericTableInfo("Name", 160, String.class, null, null));
		list.add(new GenericTableInfo("Priority", 65, Integer.class, null, null));
		list.add(new GenericTableInfo("State", 55, Integer.class, null, null));
		list.add(new GenericTableInfo("System", 55, Boolean.class, null, null));

		return list;
	}	

	@Inject
	IJobManager jobManager;

	// This uses the values of the input composite to generate new data series and add info to the table and to the chart.
	class ClearListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
		}
	}
	
	// This uses the values of the input composite to generate new data series and add info to the table and to the chart.
	class ExecuteListener extends SelectionAdapter
	{
		@Override
		public void widgetSelected(SelectionEvent e)
		{
			// Read/copy the values from widgets so we have a fixed copy if we run on a Job thread
			String family = familyText.getText();
			final int updateTimer = Integer.parseInt(forcedUpdateTimerText.getText());

			if (jobManager == null) {
				logger.warn("IJobManager injection failed");
				return;
			}
			
			Job[] jobs = jobManager.find(null);
			for (int i = 0; i < jobs.length; i++) {
				Job job = jobs[i];
				
				JobGroup jg = job.getJobGroup();
				
				String group = "none";
				if (jg != null) {
					group = jg.getName();	// TODO groups also have a state - add a symbol
				}
				String name = job.getName();
				int priority = job.getPriority();
				int state = job.getState();
				boolean isSystem = job.isSystem();
				
				
				// Add an info line to the table viewer too
				GenericRow row = new GenericRow();
				row.addString(group);
				row.addString(name);
				row.addInteger(priority);
				row.addInteger(state);
				row.addBoolean(isSystem);
				viewerData.addTask(row);
			}
		}
	}
}