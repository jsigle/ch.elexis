/*******************************************************************************
 * Copyright (c) 2005-2010, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 * 
 * $Id: RnContentProvider.java 5688 2009-08-28 06:26:36Z rgw_ch $
 *******************************************************************************/
package ch.elexis.views.rechnung;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;

import ch.elexis.Hub;
import ch.elexis.StringConstants;
import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.data.Fall;
import ch.elexis.data.Konsultation;
import ch.elexis.data.Patient;
import ch.elexis.data.Query;
import ch.elexis.data.Rechnung;
import ch.elexis.data.RnStatus;
import ch.elexis.util.Log;
import ch.elexis.util.viewers.CommonViewer;
import ch.elexis.util.viewers.ViewerConfigurer;
import ch.elexis.util.viewers.ViewerConfigurer.ControlFieldListener;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.IFilter;
import ch.rgw.tools.Money;
import ch.rgw.tools.TimeTool;
import ch.rgw.tools.Tree;

/**
 * Contentprovider used in "RechnungsListeView" to display bills selected by some criteria
 * 
 * @author gerry
 * 
 */
class RnContentProvider implements ViewerConfigurer.ICommonViewerContentProvider,
		ITreeContentProvider, ControlFieldListener {
	private static final float PREVAL = 50000f;
	// private Query<Rechnung> q1;
	CommonViewer cv;
	Tree[] result;
	int iPat, iRn;
	Money mAmount, mOpen;
	TreeComparator treeComparator = new TreeComparator();
	PatientComparator patientComparator = new PatientComparator();
	RechnungsListeView rlv;
	String[] constraints;
	
	private final Log log = Log.get("Rechnungenlader"); //$NON-NLS-1$
	
	RnContentProvider(final RechnungsListeView l, final CommonViewer cv){
		this.cv = cv;
		rlv = l;
	}
	
	public void startListening(){
		cv.getConfigurer().getControlFieldProvider().addChangeListener(this);
	}
	
	public void stopListening(){
		cv.getConfigurer().getControlFieldProvider().removeChangeListener(this);
	}
	
	@SuppressWarnings("unchecked")
	public Object[] getElements(final Object inputElement){
		IProgressService progressService = PlatformUI.getWorkbench().getProgressService();
		try {
			progressService.runInUI(PlatformUI.getWorkbench().getProgressService(),
				new IRunnableWithProgress() {
					public void run(final IProgressMonitor monitor){
						reload(monitor);
					}
				}, null);
		} catch (Throwable ex) {
			ExHandler.handle(ex);
		}
		
		return result == null ? new Tree[0] : result;
	}
	
	public void dispose(){
	// TODO Automatisch erstellter Methoden-Stub
	
	}
	
	public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput){
	// TODO Automatisch erstellter Methoden-Stub
	
	}
	
	// Vom ControlFieldListener
	public void changed(HashMap<String, String> values){
		cv.notify(CommonViewer.Message.update);
	}
	
	public void reorder(final String field){
		cv.getViewerWidget().setSorter(new ViewerSorter() {
			
			@Override
			public int compare(final Viewer viewer, final Object e1, final Object e2){
				TimeTool t1 = getLatest((Tree) e1);
				TimeTool t2 = getLatest((Tree) e2);
				return t1.compareTo(t2);
			}
			
		});
	}
	
	public void selected(){
	// nothing to do
	}
	
	@SuppressWarnings("unchecked")
	private TimeTool getLatest(final Tree t){
		if (t.contents instanceof Rechnung) {
			return new TimeTool(((Rechnung) t.contents).getDatumRn());
		} else if (t.contents instanceof Fall) {
			return getLatestFromCase(t);
		} else if (t.contents instanceof Patient) {
			Tree runner = t.getFirstChild();
			TimeTool latest = new TimeTool();
			while (runner != null) {
				TimeTool lff = getLatestFromCase(runner);
				if (lff.isBefore(latest)) {
					latest.set(lff);
				}
				runner = runner.getNextSibling();
			}
			return latest;
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private TimeTool getLatestFromCase(final Tree c){
		List<Tree> tRn = (List<Tree>) c.getChildren();
		TimeTool tL = new TimeTool();
		for (Tree t : tRn) {
			Rechnung rn = (Rechnung) t.contents;
			TimeTool ttR = new TimeTool(rn.getDatumRn());
			if (ttR.isBefore(tL)) {
				tL.set(ttR);
			}
		}
		return tL;
	}
	
	@SuppressWarnings("unchecked")
	public Object[] getChildren(final Object parentElement){
		if (parentElement instanceof Tree) {
			Tree[] ret = (Tree[]) ((Tree) parentElement).getChildren().toArray(new Tree[0]);
			Arrays.sort(ret, treeComparator);
			return ret;
		}
		return new Object[0];
	}
	
	@SuppressWarnings("unchecked")
	public Object getParent(final Object element){
		if (element instanceof Tree) {
			return ((Tree) element).getParent();
		}
		return null;
	}
	
	public boolean hasChildren(final Object element){
		if (element instanceof Tree) {
			if (((Tree) element).contents instanceof Rechnung) {
				return false;
			}
		}
		return true;
	}
	
	public void setConstraints(final String[] constraints){
		this.constraints = constraints;
	}
	
	public Query<Rechnung> prepareQuery(){
		final String[] val = cv.getConfigurer().getControlFieldProvider().getValues();
		Query<Rechnung> q1 = new Query<Rechnung>(Rechnung.class);
		if (Hub.acl.request(AccessControlDefaults.ACCOUNTING_GLOBAL) == false) {
			if (Hub.actMandant == null) {
				return null;
			}
			q1.add(Rechnung.MANDATOR_ID, Query.EQUALS, Hub.actMandant.getId());
		}
		if (val[2] != null) {
			q1.add(Rechnung.BILL_NUMBER, Query.EQUALS, val[2]);
			
		} else if (val[3] != null) {
			q1.add(Rechnung.BILL_AMOUNT_CENTS, Query.EQUALS, val[3]);
		} else {
			if (Integer.parseInt(val[0]) == RnStatus.ZU_DRUCKEN) {
				q1.startGroup();
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer.toString(RnStatus.OFFEN));
				q1.or();
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer.toString(RnStatus.MAHNUNG_1));
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer.toString(RnStatus.MAHNUNG_2));
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer.toString(RnStatus.MAHNUNG_3));
				q1.endGroup();
				q1.and();
			} else if (Integer.parseInt(val[0]) == RnStatus.AUSSTEHEND) {
				q1.startGroup();
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer
					.toString(RnStatus.OFFEN_UND_GEDRUCKT));
				q1.or();
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer
					.toString(RnStatus.MAHNUNG_1_GEDRUCKT));
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer
					.toString(RnStatus.MAHNUNG_2_GEDRUCKT));
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, Integer
					.toString(RnStatus.MAHNUNG_3_GEDRUCKT));
				q1.endGroup();
				q1.and();
			} else if (!val[0].equals(StringConstants.ZERO)) {
				q1.add(Rechnung.BILL_STATE, Query.EQUALS, val[0]);
			}
			if (val[1] != null) {
				Patient act = Patient.load(val[1]);
				if (act.exists()) {
					Fall[] faelle = act.getFaelle();
					if ((faelle != null) && (faelle.length > 0)) {
						q1.startGroup();
						q1.insertFalse();
						q1.or();
						for (Fall fall : faelle) {
							// if (fall.isOpen()) {
							q1.add(Rechnung.CASE_ID, Query.EQUALS, fall.getId());
							// }
						}
						q1.endGroup();
					}
				}
			}
			if (constraints != null) {
				for (String line : constraints) {
					q1.addToken(line);
				}
			}
		}
		if (val[4] != null) {
			q1.addPostQueryFilter(new IFilter() {
				
				public boolean select(Object toTest){
					if (toTest instanceof Rechnung) {
						Rechnung rn = (Rechnung) toTest;
						Fall fall = rn.getFall();
						if (fall != null) {
							String abr = fall.getAbrechnungsSystem();
							if (abr != null) {
								if (abr.equals(val[4])) {
									return true;
								}
							}
						}
					}
					return false;
				}
				
			});
		}
		return q1;
	}
	
	@SuppressWarnings("unchecked")
	public void reload(final IProgressMonitor monitor){
		monitor.beginTask(
			Messages.getString("RnContentProvider.collectInvoices"), Math.round(PREVAL)); //$NON-NLS-1$
		monitor.subTask(Messages.getString("RnContentProvider.prepare")); //$NON-NLS-1$
		Tree<Patient> root = new Tree<Patient>(null, null);
		Hashtable<String, Tree<Patient>> hPats = new Hashtable<String, Tree<Patient>>(367, 0.75f);
		Hashtable<String, Tree<Fall>> hFaelle = new Hashtable<String, Tree<Fall>>(719, 0.75f);
		
		Query<Rechnung> q1 = prepareQuery();
		if (q1 == null) {
			return;
		}
		List<Rechnung> rechnungen = q1.execute();
		if (rechnungen == null) {
			log.log(Messages.getString("RnContentProvider.errorRetriveingBillds"), Log.ERRORS); //$NON-NLS-1$
			return;
		}
		monitor.worked(100);
		monitor.subTask(Messages.getString("RnContentProvider.databseRequest")); //$NON-NLS-1$
		int multiplyer = Math.round(PREVAL / rechnungen.size());
		monitor.subTask(Messages.getString("RnContentProvider.load")); //$NON-NLS-1$
		iPat = 0;
		iRn = rechnungen.size();
		mAmount = new Money();
		mOpen = new Money();
		for (Rechnung rn : rechnungen) {
			if ((rn == null) || (!rn.exists())) {
				log.log("Fehlerhafte Rechnung", Log.ERRORS); //$NON-NLS-1$
				continue;
			}
			mAmount.addMoney(rn.getOffenerBetrag());
			mOpen.addMoney(rn.getAnzahlung());
			Fall fall = rn.getFall();
			if (fall == null) {
				log.log("Rechnung " + rn.getId() + " hat keinen Fall", Log.WARNINGS); //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			Tree<Fall> tFall = hFaelle.get(fall.getId());
			if (tFall == null) {
				Patient pat = fall.getPatient();
				if (pat == null) {
					log.log("Fall " + fall.getId() + " hat keinen Patienten", Log.WARNINGS); //$NON-NLS-1$ //$NON-NLS-2$
					continue;
				}
				Tree<Patient> tPat = hPats.get(pat.getId());
				if (tPat == null) {
					tPat = new Tree<Patient>(root, pat, patientComparator);
					hPats.put(pat.getId(), tPat);
					iPat++;
				}
				tFall = new Tree(tPat, fall);
				hFaelle.put(fall.getId(), tFall);
			}
			Tree<Rechnung> tRn = new Tree(tFall, rn);
			monitor.worked(multiplyer);
		}
		
		if (rlv.tPat != null) {
			rlv.tPat.setText(Integer.toString(iPat));
			rlv.tRn.setText(Integer.toString(iRn));
			rlv.tSum.setText(mAmount.getAmountAsString());
			rlv.tOpen.setText(mOpen.getAmountAsString());
		}
		monitor.worked(1);
		monitor.subTask(Messages.getString("RnContentProvider.prepareSort")); //$NON-NLS-1$
		result = root.getChildren().toArray(new Tree[0]);
		monitor.worked(100);
		//monitor.subTask(Messages.getString("RnContentProvider.sort")); //$NON-NLS-1$
		// Arrays.sort(result,treeComparator);
		monitor.done();
		
	}
	
	private static class PatientComparator implements Comparator {
		public int compare(final Object o1, final Object o2){
			Patient p1 = (Patient) o1;
			Patient p2 = (Patient) o2;
			return p1.getLabel().compareTo(p2.getLabel());
		}
	}
	
	private static class TreeComparator implements Comparator {
		TimeTool tt0 = new TimeTool();
		TimeTool tt1 = new TimeTool();
		
		public int compare(final Object arg0, final Object arg1){
			Tree t0 = (Tree) arg0;
			Tree t1 = (Tree) arg1;
			if (t0.contents instanceof Patient) {
				Patient p0 = (Patient) t0.contents;
				Patient p1 = (Patient) t1.contents;
				String s0 = p0.getLabel();
				String s1 = p1.getLabel();
				return s0.compareTo(s1);
			} else if (t0.contents instanceof Fall) {
				Fall f0 = (Fall) t0.contents;
				Fall f1 = (Fall) t1.contents;
				tt0.set(f0.getBeginnDatum());
				tt1.set(f1.getBeginnDatum());
				return tt0.secondsTo(tt1);
			} else if (t0.contents instanceof Konsultation) {
				Konsultation b0 = (Konsultation) t0.contents;
				Konsultation b1 = (Konsultation) t1.contents;
				tt0.set(b0.getDatum());
				tt1.set(b1.getDatum());
				return tt0.secondsTo(tt1);
			} else {
				return 0;
			}
			
		}
	}
	
	@Override
	public void init(){
	// TODO Auto-generated method stub
	
	}
	
}