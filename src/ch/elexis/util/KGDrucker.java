/*******************************************************************************
 * Copyright (c) 2006-2010, G. Weirich, Daniel Lutz and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Daniel Lutz - initial implementation, based on RechnungsDrucker
 *
 * $Id$
 *******************************************************************************/

package ch.elexis.util;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;
import org.eclipse.ui.statushandlers.StatusManager;

import ch.elexis.Hub;
import ch.elexis.actions.ElexisEventDispatcher;
import ch.elexis.data.Patient;
import ch.elexis.scripting.ScriptingException;
import ch.elexis.status.ElexisStatus;
import ch.elexis.views.KGPrintView;
import ch.rgw.tools.ExHandler;

@Deprecated
public class KGDrucker {
	KGPrintView kgp;
	IWorkbenchPage kgPage;
	// IProgressMonitor monitor;
	Patient patient;
	
	public void doPrint(Patient pat){
		this.patient = pat;
		kgPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IProgressService progressService = PlatformUI.getWorkbench().getProgressService();
		
		try {
			kgp = (KGPrintView) kgPage.showView(KGPrintView.ID);
			progressService.runInUI(PlatformUI.getWorkbench().getProgressService(),
				new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor){
						monitor.beginTask(Messages.getString("KGDrucker.printEMR"), 1); //$NON-NLS-1$
						// gw 23.7.2006 an neues Selectionmodell angepasst
						Patient actPatient = ElexisEventDispatcher.getSelectedPatient();
						if (kgp.doPrint(actPatient, monitor) == false) {
							ErrorDialog
								.openError(
									null,
									Messages.getString("KGDrucker.errorPrinting"), Messages.getString("KGDrucker.couldntprint") + patient.getLabel() + Messages.getString("KGDrucker.emr"), null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							
						}
						
						monitor.done();
					}
				}, null);
			
			kgPage.hideView(kgp);
			
		} catch (Exception ex) {
			ElexisStatus status =
				new ElexisStatus(ElexisStatus.ERROR, Hub.PLUGIN_ID, ElexisStatus.CODE_NONE, Messages
					.getString("KGDrucker.errorPrinting")
					+ ": " + Messages.getString("KGDrucker.couldntShow"), ex);
			StatusManager.getManager().handle(status);
		}
	}
}
