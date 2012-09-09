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
 * $Id: KontakteView.java 6238 2010-03-19 15:50:41Z rgw_ch $
 *******************************************************************************/

package ch.elexis.views;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.Desk;
import ch.elexis.StringConstants;
import ch.elexis.actions.ElexisEvent;
import ch.elexis.actions.ElexisEventDispatcher;
import ch.elexis.actions.FlatDataLoader;
import ch.elexis.actions.GlobalActions;
import ch.elexis.actions.PersistentObjectLoader;
import ch.elexis.actions.RestrictedAction;
import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.data.Artikel;
import ch.elexis.data.Kontakt;
import ch.elexis.data.Organisation;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Person;
import ch.elexis.data.Query;
import ch.elexis.data.Bestellung.Item;
import ch.elexis.dialogs.GenericPrintDialog;
import ch.elexis.dialogs.KontaktErfassenDialog;
import ch.elexis.util.SWTHelper;
import ch.elexis.util.ViewMenus;
import ch.elexis.util.viewers.CommonViewer;
import ch.elexis.util.viewers.DefaultControlFieldProvider;
import ch.elexis.util.viewers.DefaultLabelProvider;
import ch.elexis.util.viewers.SimpleWidgetProvider;
import ch.elexis.util.viewers.ViewerConfigurer;
import ch.elexis.util.viewers.ViewerConfigurer.ControlFieldListener;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

public class KontakteView extends ViewPart implements ControlFieldListener, ISaveablePart2 {
	public static final String ID = "ch.elexis.Kontakte"; //$NON-NLS-1$
	private CommonViewer cv;
	private ViewerConfigurer vc;
	IAction dupKontakt, delKontakt, createKontakt, printList,
			copySelectedContactInfosToClipboardAction,	//201202161250js: Added copySelectedContactInfosToClipboardAction
			copySelectedAddressesToClipboardAction;	//201201280152js: Added copySelectedAddressesToClipboardAction
	PersistentObjectLoader loader;
	
	private final String[] fields = {
		Kontakt.FLD_SHORT_LABEL + Query.EQUALS + Messages.getString("KontakteView.shortLabel"), //$NON-NLS-1$
		Kontakt.FLD_NAME1 + Query.EQUALS + Messages.getString("KontakteView.text1"), //$NON-NLS-1$
		Kontakt.FLD_NAME2 + Query.EQUALS + Messages.getString("KontakteView.text2"), //$NON-NLS-1$
		Kontakt.FLD_STREET + Query.EQUALS + Messages.getString("KontakteView.street"), //$NON-NLS-1$
		Kontakt.FLD_ZIP + Query.EQUALS + Messages.getString("KontakteView.zip"), //$NON-NLS-1$
		Kontakt.FLD_PLACE + Query.EQUALS + Messages.getString("KontakteView.place")}; //$NON-NLS-1$
	private ViewMenus menu;
	
	public KontakteView(){}
	
	@Override
	public void createPartControl(Composite parent){
		parent.setLayout(new FillLayout());
		cv = new CommonViewer();
		loader = new FlatDataLoader(cv, new Query<Kontakt>(Kontakt.class));
		loader.setOrderFields(new String[] {
			Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_STREET, Kontakt.FLD_PLACE
		});
		vc =
			new ViewerConfigurer(loader, new KontaktLabelProvider(),
				new DefaultControlFieldProvider(cv, fields),
				new ViewerConfigurer.DefaultButtonProvider(), new SimpleWidgetProvider(
					SimpleWidgetProvider.TYPE_LAZYLIST, SWT.MULTI, null));
		cv.create(vc, parent, SWT.NONE, getViewSite());
		makeActions();
		cv.setObjectCreateAction(getViewSite(), createKontakt);
		
		menu = new ViewMenus(getViewSite());
		menu.createViewerContextMenu(cv.getViewerWidget(), delKontakt, dupKontakt);
		
		menu.createMenu(copySelectedContactInfosToClipboardAction);				//201202161250js: Added copySelectedContactInfosToClipboardAction
		menu.createMenu(copySelectedAddressesToClipboardAction);			//201201280152js: Added copySelectedAddressesToClipboardAction
		menu.createMenu(printList);
		
		menu.createToolbar(copySelectedContactInfosToClipboardAction);			//201202161220js: Added copySelectedContactInfosToClipboardAction
		menu.createToolbar(copySelectedAddressesToClipboardAction);			//201201280152js: Added copySelectedAddressesToClipboardAction
		menu.createToolbar(printList);
		
		vc.getContentProvider().startListening();
		vc.getControlFieldProvider().addChangeListener(this);
		cv.addDoubleClickListener(new CommonViewer.DoubleClickListener() {
			public void doubleClicked(PersistentObject obj, CommonViewer cv){
				try {
					KontaktDetailView kdv =
						(KontaktDetailView) getSite().getPage().showView(KontaktDetailView.ID);
					kdv.kb.catchElexisEvent(new ElexisEvent(obj, obj.getClass(),
						ElexisEvent.EVENT_SELECTED));
				} catch (PartInitException e) {
					ExHandler.handle(e);
				}
				
			}
		});
	}
	
	public void dispose(){
		vc.getContentProvider().stopListening();
		vc.getControlFieldProvider().removeChangeListener(this);
		super.dispose();
	}
	
	@Override
	public void setFocus(){
		vc.getControlFieldProvider().setFocus();
	}
	
	public void changed(HashMap<String, String> values){
		ElexisEventDispatcher.clearSelection(Kontakt.class);
	}
	
	public void reorder(String field){
		loader.reorder(field);
	}
	
	/**
	 * ENTER has been pressed in the control fields, select the first listed patient
	 */
	// this is also implemented in PatientenListeView
	public void selected(){
		StructuredViewer viewer = cv.getViewerWidget();
		Object[] elements = cv.getConfigurer().getContentProvider().getElements(viewer.getInput());
		
		if (elements != null && elements.length > 0) {
			Object element = elements[0];
			/*
			 * just selecting the element in the viewer doesn't work if the control fields are not
			 * empty (i. e. the size of items changes): cv.setSelection(element, true); bug in
			 * TableViewer with style VIRTUAL? work-arount: just globally select the element without
			 * visual representation in the viewer
			 */
			if (element instanceof PersistentObject) {
				// globally select this object
				ElexisEventDispatcher.fireSelectionEvent((PersistentObject) element);
			}
		}
	}
	
	/*
	 * Die folgenden 6 Methoden implementieren das Interface ISaveablePart2 Wir benötigen das
	 * Interface nur, um das Schliessen einer View zu verhindern, wenn die Perspektive fixiert ist.
	 * Gibt es da keine einfachere Methode?
	 */
	public int promptToSaveOnClose(){
		return GlobalActions.fixLayoutAction.isChecked() ? ISaveablePart2.CANCEL
				: ISaveablePart2.NO;
	}
	
	public void doSave(IProgressMonitor monitor){ /* leer */
	}
	
	public void doSaveAs(){ /* leer */
	}
	
	public boolean isDirty(){
		return true;
	}
	
	public boolean isSaveAsAllowed(){
		return false;
	}
	
	public boolean isSaveOnCloseNeeded(){
		return true;
	}
	
	private void makeActions(){
		delKontakt =
			new RestrictedAction(AccessControlDefaults.KONTAKT_DELETE, Messages
				.getString("KontakteView.delete")) { //$NON-NLS-1$
				@Override
				public void doRun(){
					Object[] o = cv.getSelection();
					if (o != null) {
						Kontakt k = (Kontakt) o[0];
						
						if (SWTHelper.askYesNo("Wirklich löschen?", k.getLabel())) {
							k.delete();
							cv.getConfigurer().getControlFieldProvider().fireChangedEvent();
						}
					}
				}
			};
		dupKontakt = new Action(Messages.getString("KontakteView.duplicate")) { //$NON-NLS-1$
				@Override
				public void run(){
					Object[] o = cv.getSelection();
					if (o != null) {
						Kontakt k = (Kontakt) o[0];
						Kontakt dup;
						if (k.istPerson()) {
							Person p = Person.load(k.getId());
							dup =
								new Person(p.getName(), p.getVorname(), p.getGeburtsdatum(), p
									.getGeschlecht());
						} else {
							Organisation org = Organisation.load(k.getId());
							dup =
								new Organisation(org.get(Organisation.FLD_NAME1), org
									.get(Organisation.FLD_NAME2));
						}
						dup.setAnschrift(k.getAnschrift());
						cv.getConfigurer().getControlFieldProvider().fireChangedEvent();
						// cv.getViewerWidget().refresh();
					}
				}
			};
		createKontakt = new Action(Messages.getString("KontakteView.create")) { //$NON-NLS-1$
				@Override
				public void run(){
					String[] flds = cv.getConfigurer().getControlFieldProvider().getValues();
					String[] predef = new String[] {
						flds[1], flds[2], StringConstants.EMPTY, flds[3], flds[4], flds[5]
					};
					KontaktErfassenDialog ked =
						new KontaktErfassenDialog(getViewSite().getShell(), predef);
					ked.open();
				}
			};
		
		printList = new Action("Markierte Adressen drucken") {
			{
				setImageDescriptor(Desk.getImageDescriptor(Desk.IMG_PRINTER));
				setToolTipText("Die in der Liste markierten Kontakte als Tabelle ausdrucken");
			}
			
			public void run(){
				Object[] sel = cv.getSelection();
				String[][] adrs = new String[sel.length][];
				if (sel != null && sel.length > 0) {
					GenericPrintDialog gpl =
						new GenericPrintDialog(getViewSite().getShell(), "Adressliste",
							"Adressliste");
					gpl.create();
					for (int i = 0; i < sel.length; i++) {
						Kontakt k = (Kontakt) sel[i];
						String[] f =
							new String[] {
								Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_NAME3,
								Kontakt.FLD_STREET, Kontakt.FLD_ZIP, Kontakt.FLD_PLACE,
								Kontakt.FLD_PHONE1
							};
						String[] v = new String[f.length];
						k.get(f, v);
						adrs[i] = new String[4];
						adrs[i][0] =
							new StringBuilder(v[0]).append(StringConstants.SPACE).append(v[1])
								.append(StringConstants.SPACE).append(v[2]).toString();
						adrs[i][1] = v[3];
						adrs[i][2] =
							new StringBuilder(v[4]).append(StringConstants.SPACE).append(v[5])
								.toString();
						adrs[i][3] = v[6];
					}
					gpl.insertTable("[Liste]", adrs, null);
					gpl.open();
				}
			}
		};
		
		/*
		 * 201202161220js:
		 * Copy selected contact data (complete) to the clipboard, so it/they can be easily pasted into a target document
		 * for various further usage. This variant produces a more complete data set than copySelectedAddresses... below;
		 * it also includes the phone numbers and does not use the postal address, but all the individual data fields.
		 * Two actions with identical / similar code has also been added to PatientenListeView.java 
		 */
		copySelectedContactInfosToClipboardAction = new Action(Messages.getString("KontakteView.copySelectedContactInfosToClipboard")) { //$NON-NLS-1$
			{
				setImageDescriptor(Desk.getImageDescriptor(Desk.IMG_CLIPBOARD));
				setToolTipText(Messages.getString("KontakteView.copySelectedContactInfosToClipboard")); //$NON-NLS-1$
			}
			
			@Override
			public void run(){
				
				//Adopted from KontakteView.printList:			
				//Convert the selected contacts into a list

				StringBuffer SelectedContactInfosText = new StringBuffer();
								
				Object[] sel = cv.getSelection();
				
				//js: If you enable the following line for debug output,
				//    you should also enable the SelectedContactInfosText.setLength(0) line below,
				//    and enable output of SelectedContactInfosText even for the case of an empty selection further below.
				//SelectedContactInfosText.append("jsdebug: Sorry, your selection is empty.");
				
				if (sel != null && sel.length > 0) {
					//SelectedContactInfosText.setLength(0);
					//SelectedContactInfosText.append("jsdebug: Your selection includes "+sel.length+" element(s):"+System.getProperty("line.separator"));
					
					for (int i = 0; i < sel.length; i++) {
						Kontakt k = (Kontakt) sel[i];
						
						//System.out.print("jsdebug: SelectedContactInfos.k.toString(): \n"+k.toString()+"\n");

						//The following code is adopted from Kontakt.createStdAnschrift for a different purpose/layout:
						//ggf. hier zu Person.getPersonalia() eine abgewandelte Fassung hinzufügen und von hier aus aufrufen.
						
						//This highly similar (but still different) code has been adopted from my addition
						//to PatientenListeView.java CopySelectedPatInfosToClipboard... 201202161313js

						//optional code; this could be made configurable. For now: disabled by if (false)...
						if (false) {
							//I put the field of "Kürzel" in front. It contains a Patient ID number,
							//and optionally kk... for health insurances, or vn initials as Vorname Nachname for physicians. 
							String thisKontaktFLD_SHORT_LABEL = k.get(k.FLD_SHORT_LABEL); //$NON-NLS-1$
							if (!StringTool.isNothing(thisKontaktFLD_SHORT_LABEL)) {
								SelectedContactInfosText.append(thisKontaktFLD_SHORT_LABEL).append(",").append(StringTool.space);
							}
						}					
						
						if (k.istPerson()) {
							// Here, we need to look at the Person variant of a Kontakt to obtain their sex; 201202161326js
							// Kontakt cannot simply be cast to Person - if we try, we'll throw an error, and the remainder of this action will be ignored.
							// Person p = (Person) sel[i]; //THIS WILL NOT WORK.
							// So obtain the corresponding Person for a Kontakt via the ID:
							Person p = Person.load(k.getId());

							String salutation;
							// TODO default salutation might be configurable (or a "Sex missing!" Info might appear) js 
							if (p.getGeschlecht().equals(Person.MALE)) {							
								salutation = Messages.getString("KontakteView.SalutationM"); //$NON-NLS-1$
							} else  //We do not use any default salutation for unknown sex to avoid errors!
							if (p.getGeschlecht().equals(Person.FEMALE)) {							
								salutation = Messages.getString("KontakteView.SalutationF"); //$NON-NLS-1$
							} else { salutation = ""; //$NON-NLS-1$
							}
							
							if (!StringTool.isNothing(salutation)) {	//salutation should currently never be empty, but paranoia...
								SelectedContactInfosText.append(salutation);
								SelectedContactInfosText.append(StringTool.space);
							}
								
							String titel = p.get(p.TITLE); //$NON-NLS-1$
							if (!StringTool.isNothing(titel)) {
								SelectedContactInfosText.append(titel).append(StringTool.space);
							}
							//js: A comma between Family Name and Given Name would be generally helpful to reliably tell them apart:
							//SelectedContactInfosText.append(k.getName()+","+StringTool.space+k.getVorname());
							//js: But Jürg Hamacher prefers this in his letters without a comma in between:
							//SelectedContactInfosText.append(p.getName()+StringTool.space+p.getVorname());
							//Whereas I use the above variant for PatientenListeView.java;
							//I put the Vorname first in KontakteView. And I only use a spacer, if the first field is not empty!
							//SelectedContactInfosText.append(p.getVorname()+StringTool.space+p.getName());
							if (!StringTool.isNothing(p.getVorname())) {
								SelectedContactInfosText.append(p.getVorname()+StringTool.space);
							}
							if (!StringTool.isNothing(p.getName())) {
								SelectedContactInfosText.append(p.getName());
							}
							
							//Also, in KontakteView, I copy the content of fields "Bemerkung" and "Zusatz" as well.
							//"Zusatz" is mapped to "Bezeichnung3" in Person.java.
							String thisPersonFLD_REMARK = p.get(p.FLD_REMARK); //$NON-NLS-1$
							if (!StringTool.isNothing(thisPersonFLD_REMARK)) {
								SelectedContactInfosText.append(",").append(StringTool.space).append(thisPersonFLD_REMARK);
							}
							String thisPersonFLD_NAME3 = p.get(p.FLD_NAME3); //$NON-NLS-1$
							if (!StringTool.isNothing(thisPersonFLD_NAME3)) {
								SelectedContactInfosText.append(",").append(StringTool.space).append(thisPersonFLD_NAME3);
							}						

							String thisPatientBIRTHDATE = (String) p.get(p.BIRTHDATE);
							if (!StringTool.isNothing(thisPatientBIRTHDATE)) {
							//js: This would add the term "geb." (born on the) before the date of birth:
							//	SelectedContactInfosText.append(","+StringTool.space+"geb."+StringTool.space+new TimeTool(thisPatientBIRTHDATE).toString(TimeTool.DATE_GER));
							//js: But Jürg Hamacher prefers the patient information in his letters without that term:
							SelectedContactInfosText.append(","+StringTool.space+new TimeTool(thisPatientBIRTHDATE).toString(TimeTool.DATE_GER));
							}
						} else {	//if (k.istPerson())... else...
							String thisAddressFLD_NAME1 = (String) k.get(k.FLD_NAME1);
							String thisAddressFLD_NAME2 = (String) k.get(k.FLD_NAME2);
							String thisAddressFLD_NAME3 = (String) k.get(k.FLD_NAME3);
							if (!StringTool.isNothing(thisAddressFLD_NAME1)) {
								SelectedContactInfosText.append(thisAddressFLD_NAME1);
								if (!StringTool.isNothing(thisAddressFLD_NAME2+thisAddressFLD_NAME3)) {
									SelectedContactInfosText.append(StringTool.space);
								}
							}
							if (!StringTool.isNothing(thisAddressFLD_NAME2)) {
								SelectedContactInfosText.append(thisAddressFLD_NAME2);
							}
							if (!StringTool.isNothing(thisAddressFLD_NAME3)) {
								SelectedContactInfosText.append(thisAddressFLD_NAME3);
							}
							if (!StringTool.isNothing(thisAddressFLD_NAME3)) {
								SelectedContactInfosText.append(StringTool.space);
							}
						}

						String thisAddressFLD_STREET = (String) k.get(k.FLD_STREET);
						if (!StringTool.isNothing(thisAddressFLD_STREET)) {
							SelectedContactInfosText.append(","+StringTool.space+thisAddressFLD_STREET);
						}

						String thisAddressFLD_COUNTRY = (String) k.get(k.FLD_COUNTRY);
						if (!StringTool.isNothing(thisAddressFLD_COUNTRY)) {
							SelectedContactInfosText.append(","+StringTool.space+thisAddressFLD_COUNTRY+"-");
						}
							
						String thisAddressFLD_ZIP = (String) k.get(k.FLD_ZIP);
						if (!StringTool.isNothing(thisAddressFLD_ZIP)) {
								if (StringTool.isNothing(thisAddressFLD_COUNTRY)) {
										SelectedContactInfosText.append(","+StringTool.space);
									};
							SelectedContactInfosText.append(thisAddressFLD_ZIP);
						};
										
						String thisAddressFLD_PLACE = (String) k.get(k.FLD_PLACE);
						if (!StringTool.isNothing(thisAddressFLD_PLACE)) {
							if (StringTool.isNothing(thisAddressFLD_COUNTRY) && StringTool.isNothing(thisAddressFLD_ZIP)) {
								SelectedContactInfosText.append(",");
							};
							SelectedContactInfosText.append(StringTool.space+thisAddressFLD_PLACE);
						}

						String thisAddressFLD_PHONE1 = (String) k.get(k.FLD_PHONE1);
						if (!StringTool.isNothing(thisAddressFLD_PHONE1)) {
								SelectedContactInfosText.append(","+StringTool.space+StringTool.space+thisAddressFLD_PHONE1);
						}
							
						String thisAddressFLD_PHONE2 = (String) k.get(k.FLD_PHONE2);
						if (!StringTool.isNothing(thisAddressFLD_PHONE2)) {
							SelectedContactInfosText.append(","+StringTool.space+StringTool.space+thisAddressFLD_PHONE2);
						}
							
						String thisAddressFLD_MOBILEPHONE = (String) k.get(k.FLD_MOBILEPHONE);
						if (!StringTool.isNothing(thisAddressFLD_MOBILEPHONE)) {
							//With a colon after the label:
							//SelectedContactInfosText.append(","+StringTool.space+k.FLD_MOBILEPHONE+":"+StringTool.space+thisAddressFLD_MOBILEPHONE);
							//Without a colon after the label:
							SelectedContactInfosText.append(","+StringTool.space+k.FLD_MOBILEPHONE+StringTool.space+thisAddressFLD_MOBILEPHONE);
						}
							
						String thisAddressFLD_FAX = (String) k.get(k.FLD_FAX);
						if (!StringTool.isNothing(thisAddressFLD_FAX)) {
							//With a colon after the label:
							//SelectedContactInfosText.append(","+StringTool.space+k.FLD_FAX+":"+StringTool.space+thisAddressFLD_FAX);
							//Without a colon after the label:
							SelectedContactInfosText.append(","+StringTool.space+k.FLD_FAX+StringTool.space+thisAddressFLD_FAX);
						}
							
						String thisAddressFLD_E_MAIL = (String) k.get(k.FLD_E_MAIL);
						if (!StringTool.isNothing(thisAddressFLD_E_MAIL)) {
							SelectedContactInfosText.append(","+StringTool.space+thisAddressFLD_E_MAIL);
						}							

						//Add another empty line (or rather: paragraph), if at least one more address will follow.
						if (i<sel.length-1) {
							SelectedContactInfosText.append(System.getProperty("line.separator"));
						}
						
					}		//js: for each element in sel do

					/*
					 * 20120130js:
					 * I would prefer to move the following code portions down behind the "if sel not empty" block,
					 * so that (a) debugging output can be produced and (b) the clipboard will be emptied
					 * when NO Contacts have been selected. I did this to avoid the case where a user would assume
					 * they had selected some address, copied data to the clipboard, and pasted them - and, even
					 * when they erred about their selection, which was indeed empty, they would not immediately
					 * notice that because some (old, unchanged) content would still come out of the clipboard.
					 * 
					 * But if I do so, and there actually is no address selected, I get an error window:
					 * Unhandled Exception ... not valid. So to avoid that message without any further research
					 * (I need to get this work fast now), I move the code back up and leave the clipboard
					 * unchanged for now, if no Contacts had been selected to process.
					 * 
					 * (However, I may disable the toolbar icon / menu entry for this action in that case later on.) 
				 	 */				 	 
					
				    //System.out.print("jsdebug: SelectedContactInfosText: \n"+SelectedContactInfosText+"\n");
					
					//Adopted from BestellView.exportClipboardAction:
					//Copy some generated object.toString() to the clipoard
					
					Clipboard clipboard = new Clipboard(Desk.getDisplay());
					TextTransfer textTransfer = TextTransfer.getInstance();
					Transfer[] transfers = new Transfer[] {
						textTransfer
					};
					Object[] data = new Object[] {
						SelectedContactInfosText.toString()
					};
					clipboard.setContents(data, transfers);
					clipboard.dispose();
				}			//js: if sel not empty
			};  	//js: copySelectedContactInfosToClipboardAction.run()
		};

		/*
		 * 201201280147js:
		 * Copy selected address(es) to the clipboard, so it/they can be easily pasted into a letter for printing.
		 * Two actions with identical / similar code has also been added to PatientenListeView.java 
		 */
		copySelectedAddressesToClipboardAction = new Action(Messages.getString("KontakteView.copySelectedAddressesToClipboard")) { //$NON-NLS-1$
			{
				setImageDescriptor(Desk.getImageDescriptor(Desk.IMG_CLIPBOARD));
				setToolTipText(Messages.getString("KontakteView.copySelectedAddressesToClipboard")); //$NON-NLS-1$
			}
			
			@Override
			public void run(){
				
				//Adopted from KontakteView.printList:			
				//Convert the selected addresses into a list

				StringBuffer selectedAddressesText = new StringBuffer();
								
				Object[] sel = cv.getSelection();
				
				//js: If you enable the following line for debug output,
				//    you should also enable the selectedAddressesText.setLength(0) line below,
				//    and enable output of selectedAddressesText even for the case of an empty selection further below.
				//selectedAddressesText.append("jsdebug: Sorry, your selection is empty.");
				
				if (sel != null && sel.length > 0) {
					//selectedAddressesText.setLength(0);
					//selectedAddressesText.append("jsdebug: Your selection includes "+sel.length+" element(s):"+System.getProperty("line.separator"));
					
					for (int i = 0; i < sel.length; i++) {
						Kontakt k = (Kontakt) sel[i];

						/*
						 * Synthesize the address lines to output from the entries in Kontakt k;
						 * added to implement the output format desired for the copyAddressToClipboard()
						 * buttons added to version 2.1.6.js as of 2012-01-28ff
					 	 *
						 * We might synthesize our own "Anschrift" for each Kontakt,
						 * completely according to our own requirements,
						 * OR use any of the methods defined for Kontakt like:
						 * getLabel...(), getPostAnschrift, createStandardAnschrift, List<BezugsKontakt>... -
						 * 
						 * The Declaration of Kontakt with field definitions is available in Kontakt.java, please look
						 * therein for additional details, please. Click-Right -> Declaration on Kontakt in Eclipse works.
						 * You can also look above to see the fields that printList would use.
						 */ 

						//selectedAddressesText.append("jsdebug: Item "+Integer.toString(i)+" "+k.toString()+System.getProperty("line.separator"));

						//getPostAnschriftPhoneFaxEmail() already returns a line separator after the address
						//The first parameter controls multiline or single line output
						//The second parameter controls whether the phone numbers shall be included
						selectedAddressesText.append(k.getPostAnschriftPhoneFaxEmail(true,true));

						//Add another empty line (or rather: paragraph), if at least one more address will follow.
						if (i<sel.length-1) {
							selectedAddressesText.append(System.getProperty("line.separator"));
										
						}
					}		//js: for each element in sel do

					/*
					 * 20120130js:
					 * I would prefer to move the following code portions down behind the "if sel not empty" block,
					 * so that (a) debugging output can be produced and (b) the clipboard will be emptied
					 * when NO addresses have been selected. I did this to avoid the case where a user would assume
					 * they had selected some address, copied data to the clipboard, and pasted them - and, even
					 * when they erred about their selection, which was indeed empty, they would not immediately
					 * notice that because some (old, unchanged) content would still come out of the clipboard.
					 * 
					 * But if I do so, and there actually is no address selected, I get an error window:
					 * Unhandled Exception ... not valid. So to avoid that message without any further research
					 * (I need to get this work fast now), I move the code back up and leave the clipboard
					 * unchanged for now, if no addresses had been selected to process.
					 * 
					 * (However, I may disable the toolbar icon / menu entry for this action in that case later on.) 
				 	 */				 	 
					
				    //System.out.print("jsdebug: selectedAddressesText: \n"+selectedAddressesText+"\n");
					
					//Adopted from BestellView.exportClipboardAction:
					//Copy some generated object.toString() to the clipoard
					
					Clipboard clipboard = new Clipboard(Desk.getDisplay());
					TextTransfer textTransfer = TextTransfer.getInstance();
					Transfer[] transfers = new Transfer[] {
						textTransfer
					};
					Object[] data = new Object[] {
						selectedAddressesText.toString()
					};
					clipboard.setContents(data, transfers);
					clipboard.dispose();
				}			//js: if sel not empty
			};  	//js: copySelectedAddressesToClipboardAction.run()

		};
	}
	
	class KontaktLabelProvider extends DefaultLabelProvider {
		
		@Override
		public String getText(Object element){
			String[] fields =
				new String[] {
					Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_NAME3, Kontakt.FLD_STREET,
					Kontakt.FLD_ZIP, Kontakt.FLD_PLACE, Kontakt.FLD_PHONE1
				};
			String[] values = new String[fields.length];
			((Kontakt) element).get(fields, values);
			return StringTool.join(values, StringConstants.COMMA);
		}
		
		@Override
		public Image getColumnImage(Object element, int columnIndex){
			// TODO Auto-generated method stub
			return null;
		}
		
		/*
		 * @Override public String getColumnText(Object element, int columnIndex) { Kontakt
		 * k=(Kontakt)element; switch(columnIndex){ case 0: return k.get(Kontakt.FLD_NAME1); case 1:
		 * return k.get(Kontakt.FLD_NAME2); case 2: return k.get(Kontakt.FLD_NAME3); case 3: return
		 * k.get(Kontakt.FLD_STREET); case 4: return k.get(Kontakt.FLD_ZIP); case 5: return
		 * k.get(Kontakt.FLD_PLACE); case 6: return k.get(Kontakt.FLD_PHONE1); } return "?"; }
		 */
	}
}
