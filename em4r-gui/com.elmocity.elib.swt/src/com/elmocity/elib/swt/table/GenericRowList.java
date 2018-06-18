/**
 * (c) Copyright Mirasol Op'nWorks Inc. 2002, 2003. 
 * http://www.opnworks.com
 * Created on Apr 2, 2003 by lgauthier@opnworks.com
 * 
 */

package com.elmocity.elib.swt.table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

/**
 * Class that plays the role of the domain model in the TableViewerExample
 * In real life, this class would access a persistent store of some kind.
 * 
 */

public class GenericRowList
{
	private ArrayList<GenericRow> tasks = new ArrayList<>();
	private Set changeListeners = new HashSet();

	/**
	 * Constructor
	 */
	public GenericRowList()
	{
		super();
	}
	
	/**
	 * Return the collection of tasks
	 */
	public ArrayList<GenericRow> getTasks()
	{
		return tasks;
	}
	
	/**
	 * Add a new task to the collection of tasks
	 */
	public void addTask(GenericRow task)
	{
		tasks.add(tasks.size(), task);						// add to the end
		Iterator iterator = changeListeners.iterator();
		while (iterator.hasNext())
			((IGenericRowListViewer) iterator.next()).addTask(task);
	}

	/**
	 * @param task
	 */
	public void removeTask(GenericRow task) {
		tasks.remove(task);
		Iterator iterator = changeListeners.iterator();
		while (iterator.hasNext())
			((IGenericRowListViewer) iterator.next()).removeTask(task);
	}

	/**
	 * @param task
	 */
	public void removeAllTasks() {
		tasks.clear();
		Iterator iterator = changeListeners.iterator();
		while (iterator.hasNext())
			((IGenericRowListViewer) iterator.next()).refresh();
	}
	
	/**
	 * @param task
	 */
	public void taskChanged(GenericRow task) {
		Iterator iterator = changeListeners.iterator();
		while (iterator.hasNext())
			((IGenericRowListViewer) iterator.next()).updateTask(task);
	}

	/**
	 * @param viewer
	 */
	public void removeChangeListener(IGenericRowListViewer viewer) {
		changeListeners.remove(viewer);
	}

	/**
	 * @param viewer
	 */
	public void addChangeListener(IGenericRowListViewer viewer) {
		changeListeners.add(viewer);
	}
	
	public void refresh() {
		Iterator iterator = changeListeners.iterator();
		while (iterator.hasNext())
			((IGenericRowListViewer) iterator.next()).refresh();
		
	}

}
