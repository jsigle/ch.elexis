/*******************************************************************************
 * Copyright (c) 2005-2011, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    M. Descher - Declarative access to the contextMenu
 *******************************************************************************/

package ch.elexis.views;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.Desk;
import ch.elexis.Hub;
import ch.elexis.actions.ElexisEvent;
import ch.elexis.actions.ElexisEventDispatcher;
import ch.elexis.actions.ElexisEventListenerImpl;
import ch.elexis.actions.GlobalActions;
import ch.elexis.actions.GlobalEventDispatcher;
import ch.elexis.actions.GlobalEventDispatcher.IActivationListener;
import ch.elexis.actions.Heartbeat.HeartListener;
import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.core.data.ISticker;
import ch.elexis.data.Anschrift;
import ch.elexis.data.Anwender;
import ch.elexis.data.Kontakt;
//20120214js: Hier ist mglw. nicht data.Messages, sondern views.Messages nötig.
//Jedenfalls fehlt im ersten z.B. PatientenListeView.PatientNr etc.,
//während das im zweiten dann definiert ist. Ohne diese Strings erscheinen
//in der GUI von PatientenListeView die entsprechenden Identifier mit Ausdrufezeichen
//drumherum, jedoch nicht der gewünschte Inhalt.
//Nach dem Austausch fehlen allerdings Angaben wie Kontakt.Salutation,
//die von dem neuen Code zum Kopieren von Patientenanschriften und -Infos in die Zwischenablage
//benötigt würden! Deshalb funktionieren  die entsprechenden neuen Funktionen so nicht mehr.
//beide können nicht gleichzeitig (ohne Namensdiversifikation) importiert werden.
//Und JA, nach dem Austausch erscheinen die Labels in PatientListeView.java korrekt.
//import ch.elexis.data.Messages;
import ch.elexis.views.Messages;
//20120214js Ende.
import ch.elexis.data.Organisation;
import ch.elexis.data.Patient;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Person;
import ch.elexis.data.Query;
import ch.elexis.data.Reminder;
import ch.elexis.data.Sticker;
import ch.elexis.dialogs.PatientErfassenDialog;
import ch.elexis.preferences.PreferenceConstants;
import ch.elexis.util.SWTHelper;
import ch.elexis.util.ViewMenus;
import ch.elexis.util.viewers.CommonViewer;
import ch.elexis.util.viewers.DefaultControlFieldProvider;
import ch.elexis.util.viewers.DefaultLabelProvider;
import ch.elexis.util.viewers.SimpleWidgetProvider;
import ch.elexis.util.viewers.ViewerConfigurer;
import ch.elexis.util.viewers.ViewerConfigurer.ControlFieldListener;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

/**
 * Display of Patients
 * 
 * @author gerry
 * 
 */
public class PatientenListeView extends ViewPart implements IActivationListener, ISaveablePart2,
		HeartListener {
	public static final String ID = "ch.elexis.PatListView"; //$NON-NLS-1$
	private CommonViewer cv;
	private ViewerConfigurer vc;
	private ViewMenus menus;
	private IAction filterAction, newPatAction,
					copySelectedPatInfosToClipboardAction,  //201201280434js: Added copySelectedPatInfosToClipboardAction
					copySelectedAddressesToClipboardAction; //201201280434js: Added copySelectedAddressesToClipboardAction
	private Patient actPatient;
	private boolean initiated = false;
	PatListFilterBox plfb;
	PatListeContentProvider plcp;
	Composite parent;
	
	ElexisEventListenerImpl eeli_user =
		new ElexisEventListenerImpl(Anwender.class, ElexisEvent.EVENT_USER_CHANGED) {
			
			public void runInUi(ElexisEvent ev){
				UserChanged();
			}
		};
	
	@Override
	public void dispose(){
		plcp.stopListening();
		GlobalEventDispatcher.removeActivationListener(this, this);
		ElexisEventDispatcher.getInstance().removeListeners(eeli_user);
		super.dispose();
	}
	
	/**
	 * retrieve the patient that is currently selected in the list
	 * 
	 * @return the selected patient or null if none was selected
	 */
	public Patient getSelectedPatient(){
		Object[] sel = cv.getSelection();
		if (sel != null) {
			return (Patient) sel[0];
		}
		return null;
	}
	
	/**
	 * Refresh the contents of the list.
	 */
	public void reload(){
		cv.notify(CommonViewer.Message.update);
	}
	
	@Override
	public void createPartControl(final Composite parent){
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 0;
		
		this.parent = parent;
		this.parent.setLayout(layout);
		
		cv = new CommonViewer();
		ArrayList<String> fields = new ArrayList<String>();
		initiated = !("".equals(Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWPATNR, "")));
		if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWPATNR, false)) {
			fields.add(Patient.FLD_PATID + Query.EQUALS
				+ Messages.getString("PatientenListeView.PatientNr")); //$NON-NLS-1$
		}
		if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWNAME, true)) {
			fields.add(Patient.FLD_NAME + Query.EQUALS
				+ Messages.getString("PatientenListeView.PatientName")); //$NON-NLS-1$
		}
		if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWFIRSTNAME, true)) {
			fields.add(Patient.FLD_FIRSTNAME + Query.EQUALS
				+ Messages.getString("PatientenListeView.PantientFirstName")); //$NON-NLS-1$
		}
		if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWDOB, true)) {
			fields.add(Patient.BIRTHDATE + Query.EQUALS
				+ Messages.getString("PatientenListeView.PatientBirthdate")); //$NON-NLS-1$
		}
		plcp = new PatListeContentProvider(cv, fields.toArray(new String[0]), this);
		makeActions();
		plfb = new PatListFilterBox(parent);
		plfb.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		((GridData) plfb.getLayoutData()).heightHint = 0;
		
		vc =
			new ViewerConfigurer(
			// new LazyContentProvider(cv,loader,
				// AccessControlDefaults.PATIENT_DISPLAY),
				plcp, new PatLabelProvider(), new DefaultControlFieldProvider(cv, fields
					.toArray(new String[0])), new ViewerConfigurer.DefaultButtonProvider(), // cv,Patient.class),
				new SimpleWidgetProvider(SimpleWidgetProvider.TYPE_LAZYLIST, SWT.SINGLE, cv));
		cv.create(vc, parent, SWT.NONE, getViewSite());
		// let user select patient by pressing ENTER in the control fields
		cv.getConfigurer().getControlFieldProvider().addChangeListener(
			new ControlFieldSelectionListener());
		cv.getViewerWidget().getControl()
			.setFont(Desk.getFont(PreferenceConstants.USR_DEFAULTFONT));
		
		menus = new ViewMenus(getViewSite());

		menus.createToolbar(newPatAction, filterAction);

		menus.createToolbar(copySelectedPatInfosToClipboardAction);			//201201280435js: Added copySelectedAddressesToClipboardAction
		menus.createToolbar(copySelectedAddressesToClipboardAction);		//201201280435js: Added copySelectedAddressesToClipboardAction

		menus.createControlContextMenu(cv.getViewerWidget().getControl(), new PatientMenuPopulator(
			this));

		menus.createMenu(newPatAction, filterAction);						//201202161441js: Added drop down menu entries for these previously available actions
		menus.createMenu(copySelectedPatInfosToClipboardAction);			//201201280435js: Added copySelectedAddressesToClipboardAction
		menus.createMenu(copySelectedAddressesToClipboardAction);			//201201280435js: Added copySelectedAddressesToClipboardAction
		
		plcp.startListening();
		ElexisEventDispatcher.getInstance().addListeners(eeli_user);
		GlobalEventDispatcher.addActivationListener(this, this);
		
		StructuredViewer viewer = cv.getViewerWidget();
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			
			@Override
			public void doubleClick(DoubleClickEvent event){
				PropertyDialogAction pdAction = new PropertyDialogAction(new SameShellProvider(parent),
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart().getSite().getSelectionProvider());

				if (pdAction.isApplicableForSelection())
				    pdAction.run();
			}
		});
		getSite().registerContextMenu(menus.getContextMenu(), viewer);
		getSite().setSelectionProvider(viewer);
	}
	
	public PatListeContentProvider getContentProvider(){
		return plcp;
	}
	
	@Override
	public void setFocus(){
		vc.getControlFieldProvider().setFocus();
	}
	
	class PatLabelProvider extends DefaultLabelProvider implements ITableColorProvider {
		
		@Override
		public Image getColumnImage(final Object element, final int columnIndex){
			if (element instanceof Patient) {
				Patient pat = (Patient) element;
				
				if (Reminder.findRemindersDueFor(pat, Hub.actUser, false).size() > 0) {
					return Desk.getImage(Desk.IMG_AUSRUFEZ);
				}
				ISticker et = pat.getSticker();
				Image im = null;
				if (et != null && (im = ((Sticker)et).getImage()) != null) {
					return im;
				} else {
					if (pat.getGeschlecht().equals(Person.MALE)) {
						return Desk.getImage(Desk.IMG_MANN);
					} else {
						return Desk.getImage(Desk.IMG_FRAU);
					}
				}
			} else {
				return super.getColumnImage(element, columnIndex);
			}
		}
		
		public Color getBackground(final Object element, final int columnIndex){
			if (element instanceof Patient) {
				Patient pat = (Patient) element;
				ISticker et = pat.getSticker();
				if (et != null) {
					return ((Sticker)et).getBackground();
				}
			}
			return null;
		}
		
		public Color getForeground(final Object element, final int columnIndex){
			if (element instanceof Patient) {
				Patient pat = (Patient) element;
				ISticker et = pat.getSticker();
				if (et != null) {
					return ((Sticker)et).getForeground();
				}
			}
			
			return null;
		}
		
	}
	
	public void reset(){
		vc.getControlFieldProvider().clearValues();
	}
	
	private void makeActions(){
		
		filterAction =
			new Action(Messages.getString("PatientenListeView.FilteList"), Action.AS_CHECK_BOX) { //$NON-NLS-1$
				{
					setImageDescriptor(Desk.getImageDescriptor(Desk.IMG_FILTER));
					setToolTipText(Messages.getString("PatientenListeView.FilterList")); //$NON-NLS-1$
				}
				
				@Override
				public void run(){
					GridData gd = (GridData) plfb.getLayoutData();
					if (filterAction.isChecked()) {
						gd.heightHint = 80;
						plfb.reset();
						plcp.setFilter(plfb);
						
					} else {
						gd.heightHint = 0;
						plcp.removeFilter(plfb);
					}
					parent.layout(true);
					
				}
				
			};
		
		newPatAction = new Action(Messages.getString("PatientenListeView.NewPatientAction")) { //$NON-NLS-1$
				{
					setImageDescriptor(Desk.getImageDescriptor(Desk.IMG_NEW));
					setToolTipText(Messages.getString("PatientenListeView.NewPationtToolTip")); //$NON-NLS-1$
				}
				
				@Override
				public void run(){
					// access rights guard
					if (!Hub.acl.request(AccessControlDefaults.PATIENT_INSERT)) {
						SWTHelper
							.alert(
								Messages.getString("PatientenListeView.MissingRights"), Messages.getString("PatientenListeView.YouMayNotCreatePatient")); //$NON-NLS-1$ //$NON-NLS-2$
						return;
					}
					HashMap<String, String> ctlFields = new HashMap<String, String>();
					String[] fx = vc.getControlFieldProvider().getValues();
					int i = 0;
					if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWPATNR, false)) {
						if (i < fx.length) {
							ctlFields.put(Patient.FLD_PATID, fx[i++]);
						}
					}
					if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWNAME, true)) {
						if (i < fx.length) {
							ctlFields.put(Patient.FLD_NAME, fx[i++]);
						}
					}
					if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWFIRSTNAME, true)) {
						if (i < fx.length) {
							ctlFields.put(Patient.FLD_FIRSTNAME, fx[i++]);
						}
					}
					if (Hub.userCfg.get(PreferenceConstants.USR_PATLIST_SHOWDOB, true)) {
						if (i < fx.length) {
							ctlFields.put(Patient.FLD_DOB, fx[i++]);
						}
					}
					PatientErfassenDialog ped =
						new PatientErfassenDialog(getViewSite().getShell(), ctlFields);
					if (ped.open() == Dialog.OK) {
						vc.getControlFieldProvider().clearValues();
						actPatient = ped.getResult();
						plcp.invalidate();
						cv.notify(CommonViewer.Message.update);
						cv.setSelection(actPatient, true);
					}
				}
			};
			
			/*
			 * 201201280147js:
			 * Copy selected PatientInfos to the clipboard, so it/they can be easily pasted into a letter for printing.
			 * An actions with identical / similar code has also been added above, and to KontakteView.java.
			 * Detailed comments regarding field access, and output including used newline/cr characters are maintained only there.  
			 */
			copySelectedPatInfosToClipboardAction = new Action(Messages.getString("PatientenListeView.copySelectedPatInfosToClipboard")) { //$NON-NLS-1$
				{
					setImageDescriptor(Desk.getImageDescriptor(Desk.IMG_CLIPBOARD));
					setToolTipText(Messages.getString("PatientenListeView.copySelectedPatInfosToClipboard")); //$NON-NLS-1$
				}
				
				@Override
				public void run(){
					
					//Adopted from KontakteView.printList:			
					//Convert the selected addresses into a list
					
					/* 
					 * ToDo: OK, vielleicht wäre es schöner, in Person.java (+-Patient.java?) eine Funktion getPostAnschriftFaxEmail() zu ergänzen... 
					 */				
					System.out.print("jsToDo: PatientenListeView.java, Bitte in Person.java getPersonalia() durch abgewantdelte Fassung komplementieren und den entsprechenden Code dorthin verlagern.\n"); 
					System.out.print("jsToDo: PatientenListeView.java Für: Fehler: Bei diesem Patienten ist das Flag \"Person\" nicht gesetzt!\n"); 
					System.out.print("jsToDo:    Bitte Fehlermeldung Elexis-Konform gestalten, ggf. Automatik / assistierte Fehlerbehebung hinzufügen.\n"); 						
				
					StringBuffer selectedPatInfosText = new StringBuffer();
									
					Object[] sel = cv.getSelection();
					
					//js: If you enable the following line for debug output,
					//    you should also enable the selectedPatInfosText.setLength(0) line below,
					//    and enable output of selectedPatInfosText even for the case of an empty selection further below.
					//selectedPatInfosText.append("jsdebug: Sorry, your selection is empty.");
					
					if (sel != null && sel.length > 0) {
						//selectedPatInfosText.setLength(0);
						//selectedPatInfosText.append("jsdebug: Your selection includes "+sel.length+" element(s):"+System.getProperty("line.separator"));
						
						//In PateintenListeView.java, only zero or one patients can be selected at a time.
						//Consequently, the for-loop inherited from KontakteView.java is a bit of an overkill right here. 
						for (int i = 0; i < sel.length; i++) {
							
							/*
							 * Patient ist eine Person, das ist Kontakt mit zusätzlichen Feldern (Kontakt.java, Person.java)
							 * In KontakteView.java stand hier:
							 * Kontakt k = (Kontakt) sel[i]
							 * In PatientenListeView.java verwende ich dieselbe Variablenbezeichnung k,
							 * damit ich unten nicht alle Feldeinbindungen aktualisieren muss - und damit später
							 * Änderungen in KontakteView.java schnell hierher übernommen werden können.
							 */
											
							Patient k = (Patient) sel[i];
							
							/*
							 * Synthesize the lines to output from the entries in Patient (=includes fields from Kontakt) k.
							 * This time, we build a completely self-made block of text, instead of using getPostAnschrift() as above.
							 */

							//The following code is adopted from Kontakt.createStdAnschrift for a different purpose/layout:
							//ggf. hier zu Person.getPersonalia() eine abgewandelte Fassung hinzufügen und von hier aus aufrufen.
							
							//Highly similar (but still different) code is now added
							//to KontakteView.java CopySelectedContactInfoToClipboard... 201202161313js
							
							if (k.istPerson()) {
								// TODO default salutation might be configurable (or a "Sex missing!" Info might appear) js 
								String salutation;
								if (k.getGeschlecht().equals(Person.MALE)) {							
									salutation = Messages.getString("KontakteView.SalutationM"); //$NON-NLS-1$
								} else  //We do not use any default salutation for unknown sex to avoid errors!
								if (k.getGeschlecht().equals(Person.FEMALE)) {							
									salutation = Messages.getString("KontakteView.SalutationF"); //$NON-NLS-1$
								} else { salutation = ""; //$NON-NLS-1$
								}
								selectedPatInfosText.append(salutation);
								selectedPatInfosText.append(StringTool.space);
									
								String titel = k.get(k.TITLE); //$NON-NLS-1$
								if (!StringTool.isNothing(titel)) {
									selectedPatInfosText.append(titel).append(StringTool.space);
								}
								//js: A comma between Family Name and Given Name would be generally helpful to reliably tell them apart:
								//selectedPatInfosText.append(k.getName()+","+StringTool.space+k.getVorname());
								//js: But Jürg Hamacher prefers this in his letters without a comma in between:
								//selectedPatInfosText.append(k.getName()+StringTool.space+k.getVorname());
								//Now, I only use a spacer, if the first field is not empty!
								//SelectedContactInfosText.append(p.getVorname()+StringTool.space+p.getName());
								if (!StringTool.isNothing(k.getName())) {
									selectedPatInfosText.append(k.getName()+StringTool.space);
								}
								if (!StringTool.isNothing(k.getVorname())) {
									selectedPatInfosText.append(k.getVorname());
								}

								String thisPatientBIRTHDATE = (String) k.get(k.BIRTHDATE);
								if (!StringTool.isNothing(thisPatientBIRTHDATE)) {
								//js: This would add the term "geb." (born on the) before the date of birth:
								//	selectedPatInfosText.append(","+StringTool.space+"geb."+StringTool.space+new TimeTool(thisPatientBIRTHDATE).toString(TimeTool.DATE_GER));
								//js: But Jürg Hamacher prefers the patient information in his letters without that term:
								selectedPatInfosText.append(","+StringTool.space+new TimeTool(thisPatientBIRTHDATE).toString(TimeTool.DATE_GER));
								}

								String thisAddressFLD_STREET = (String) k.get(k.FLD_STREET);
								if (!StringTool.isNothing(thisAddressFLD_STREET)) {
									selectedPatInfosText.append(","+StringTool.space+thisAddressFLD_STREET);
								}

								String thisAddressFLD_COUNTRY = (String) k.get(k.FLD_COUNTRY);
								if (!StringTool.isNothing(thisAddressFLD_COUNTRY)) {
									selectedPatInfosText.append(","+StringTool.space+thisAddressFLD_COUNTRY+"-");
								}
								
								String thisAddressFLD_ZIP = (String) k.get(k.FLD_ZIP);
								if (!StringTool.isNothing(thisAddressFLD_ZIP)) {
										if (StringTool.isNothing(thisAddressFLD_COUNTRY)) {
												selectedPatInfosText.append(","+StringTool.space);
											};
									selectedPatInfosText.append(thisAddressFLD_ZIP);
								};
											
								String thisAddressFLD_PLACE = (String) k.get(k.FLD_PLACE);
								if (!StringTool.isNothing(thisAddressFLD_PLACE)) {
									if (StringTool.isNothing(thisAddressFLD_COUNTRY) && StringTool.isNothing(thisAddressFLD_ZIP)) {
										selectedPatInfosText.append(",");
									};
									selectedPatInfosText.append(StringTool.space+thisAddressFLD_PLACE);
								}

								String thisAddressFLD_PHONE1 = (String) k.get(k.FLD_PHONE1);
								if (!StringTool.isNothing(thisAddressFLD_PHONE1)) {
										selectedPatInfosText.append(","+StringTool.space+StringTool.space+thisAddressFLD_PHONE1);
								}
								
								String thisAddressFLD_PHONE2 = (String) k.get(k.FLD_PHONE2);
								if (!StringTool.isNothing(thisAddressFLD_PHONE2)) {
									selectedPatInfosText.append(","+StringTool.space+StringTool.space+thisAddressFLD_PHONE2);
								}
								
								String thisAddressFLD_MOBILEPHONE = (String) k.get(k.FLD_MOBILEPHONE);
								if (!StringTool.isNothing(thisAddressFLD_MOBILEPHONE)) {
									//With a colon after the label:
									//selectedPatInfosText.append(","+StringTool.space+k.FLD_MOBILEPHONE+":"+StringTool.space+thisAddressFLD_MOBILEPHONE);
									//Without a colon after the label:
									selectedPatInfosText.append(","+StringTool.space+k.FLD_MOBILEPHONE+StringTool.space+thisAddressFLD_MOBILEPHONE);
								}
								
								String thisAddressFLD_FAX = (String) k.get(k.FLD_FAX);
								if (!StringTool.isNothing(thisAddressFLD_FAX)) {
									//With a colon after the label:
									//selectedPatInfosText.append(","+StringTool.space+k.FLD_FAX+":"+StringTool.space+thisAddressFLD_FAX);
									//Without a colon after the label:
									selectedPatInfosText.append(","+StringTool.space+k.FLD_FAX+StringTool.space+thisAddressFLD_FAX);
								}
								
								String thisAddressFLD_E_MAIL = (String) k.get(k.FLD_E_MAIL);
								if (!StringTool.isNothing(thisAddressFLD_E_MAIL)) {
									selectedPatInfosText.append(","+StringTool.space+thisAddressFLD_E_MAIL);
								}							
							} else {
								selectedPatInfosText.append("Fehler: Bei diesem Patienten ist das Flag \"Person\" nicht gesetzt! Bitte korrigieren!\n");
								System.out.print("jsToDo: Fehler: Bei diesem Patienten ist das Flag \"Person\" nicht gesetzt!\n"); 
								System.out.print("jsToDo: Bitte Fehlermeldung Elexis-Konform gestalten, ggf. Automatik / assistierte Fehlerbehebung hinzufügen.\n"); 						
							}
													
							//Add another empty line (or rather: paragraph), if at least one more address will follow.
							if (i<sel.length-1) {
								selectedPatInfosText.append(System.getProperty("line.separator"));
											
							}
						}		//js: for each element in sel do
					
						/*
						 * 20120128js:
						 * The following code portions can be moved down behind the next } if you want to produce
						 * debugging output or empty the clipboard even when NO addresses have been selected.
						 * (However, I may disable the toolbar icon / menu entry for this action in that case later on.) 
					 	 */				 	 
						
					    //System.out.print("jsdebug: selectedPatInfosText: \n"+selectedPatInfosText+"\n");
						
						//Adopted from BestellView.exportClipboardAction:
						//Copy some generated object.toString() to the clipoard
						
						Clipboard clipboard = new Clipboard(Desk.getDisplay());
						TextTransfer textTransfer = TextTransfer.getInstance();
						Transfer[] transfers = new Transfer[] {
							textTransfer
						};
						Object[] data = new Object[] {
							selectedPatInfosText.toString()
						};
						clipboard.setContents(data, transfers);
						clipboard.dispose();

					}			//js: if sel not empty
				};  	//js: copyselectedPatInfosToClipboardAction.run()
			};

			/*
			 * 201201280147js:
			 * Copy selected address(es) to the clipboard, so it/they can be easily pasted into a letter for printing.
			 * An actions with identical / similar code has also been added below, and to KontakteView.java. 
			 * Detailed comments regarding field access, and output including used newline/cr characters are maintained only there.  
			 */
			copySelectedAddressesToClipboardAction = new Action(Messages.getString("PatientenListeView.copySelectedAddressesToClipboard")) { //$NON-NLS-1$
				{
					setImageDescriptor(Desk.getImageDescriptor(Desk.IMG_CLIPBOARD));
					setToolTipText(Messages.getString("PatientenListeView.copySelectedAddressesToClipboard")); //$NON-NLS-1$
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
					
						//In PateintenListeView.java, only zero or one patients can be selected at a time.
						//Consequently, the for-loop inherited from KontakteView.java is a bit of an overkill right here. 
						for (int i = 0; i < sel.length; i++) {
							
							/*
							 * Patient ist eine Person, das ist Kontakt mit zusätzlichen Feldern (Kontakt.java, Person.java)
							 * In KontakteView.java stand hier:
							 * Kontakt k = (Kontakt) sel[i]
							 * In PatientenListeView.java verwende ich dieselbe Variablenbezeichnung k,
							 * damit ich unten nicht alle Feldeinbindungen aktualisieren muss - und damit später
							 * Änderungen in KontakteView.java schnell hierher übernommen werden können.
							 */
											
							Patient k = (Patient) sel[i];
							
							/*
							 * Synthesize the address lines to output from the entries in Patient (=includes fields from Kontakt) k.
							 * A different, completely self-made block of text, is provided by a similar action defined further below.
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
	
	public void activation(final boolean mode){
		if (mode == true) {
			newPatAction.setEnabled(Hub.acl.request(AccessControlDefaults.PATIENT_INSERT));
			heartbeat();
			Hub.heart.addListener(this);
		} else {
			Hub.heart.removeListener(this);
			
		}
		
	}
	
	public void visible(final boolean mode){
	// TODO Auto-generated method stub
	
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
	
	public void doSave(final IProgressMonitor monitor){ /* leer */
	}
	
	public void doSaveAs(){ /* leer */
	}
	
	public boolean isDirty(){
		return GlobalActions.fixLayoutAction.isChecked();
	}
	
	public boolean isSaveAsAllowed(){
		return false;
	}
	
	public boolean isSaveOnCloseNeeded(){
		return true;
	}
	
	public void heartbeat(){
		cv.notify(CommonViewer.Message.update);
	}
	
	/**
	 * Select Patient when user presses ENTER in the control fields. If mor than one Patients are
	 * listed, the first one is selected. (This listener only implements selected().)
	 */
	class ControlFieldSelectionListener implements ControlFieldListener {
		public void changed(HashMap<String, String> values){
		// nothing to do (handled by LazyContentProvider)
		}
		
		public void reorder(final String field){
		// nothing to do (handled by LazyContentProvider)
		}
		
		/**
		 * ENTER has been pressed in the control fields, select the first listed patient
		 */
		// this is also implemented in KontakteView
		public void selected(){
			StructuredViewer viewer = cv.getViewerWidget();
			Object[] elements =
				cv.getConfigurer().getContentProvider().getElements(viewer.getInput());
			if ((elements != null) && (elements.length > 0)) {
				Object element = elements[0];
				/*
				 * just selecting the element in the viewer doesn't work if the control fields are
				 * not empty (i. e. the size of items changes): cv.setSelection(element, true); bug
				 * in TableViewer with style VIRTUAL? work-arount: just globally select the element
				 * without visual representation in the viewer
				 */
				if (element instanceof PersistentObject) {
					// globally select this object
					ElexisEventDispatcher.fireSelectionEvent((PersistentObject) element);
				}
			}
		}
	}
	
	public void UserChanged(){
		if (!initiated)
			SWTHelper.reloadViewPart(PatientenListeView.ID);
		if (!cv.getViewerWidget().getControl().isDisposed()) {
			cv.getViewerWidget().getControl().setFont(
				Desk.getFont(PreferenceConstants.USR_DEFAULTFONT));
			cv.notify(CommonViewer.Message.update);
		}
	}
	
}
