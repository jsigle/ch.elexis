/*******************************************************************************
 * Copyright (c) 2005-2009, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    
 * $Id$
 *******************************************************************************/
package ch.elexis.data;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.IFilter;

import ch.elexis.Hub;
import ch.elexis.dialogs.SelectFallNoObligationDialog;
import ch.elexis.preferences.Leistungscodes;
import ch.elexis.util.IOptifier;
import ch.rgw.tools.Money;
import ch.rgw.tools.Result;
import ch.rgw.tools.TimeTool;

/**
 * Das Leistungskonzept ist "pluggable" definiert. Dies, damit neue Abrechnungssysteme jederzeit
 * leicht integriert werden können. Ein Leistungssystem muss nur das Interface Verrechenbar
 * implementieren, um von Elexis ohne weitere Modifikationen genutzt werden zu können.
 * 
 * @author gerry
 * 
 */
public interface IVerrechenbar extends ICodeElement {
	/**
	 *  Definition von Informationen zu der Leistung welche für die
	 *  MWSt relevant sind.
	 *  <p>
	 *  Schweizer MWSt (at.medevit.medelexis.vat_ch):
	 *  <li>VAT_DEFAULT ... Standard MWST Satz laut Einstellungsseite</li>
	 *  <li>VAT_NONE ... Keine MWST</li>
	 *  <li>VAT_CH_ISMEDICAMENT ... Artikel ist als Medikament anerkannt</li>
	 *  <li>VAT_CH_NOTMEDICAMENT ... Artikel ist nicht als Medikament anerkannt</li>
	 *  <li>VAT_CH_ISTREATMENT ... Leistung ist als Heilbehandlung anerkannt</li>
	 *  <li>VAT_CH_NOTTREATMENT ... Leistung ist nicht als Heilbehandlung anerkannt</li>
	 *  </p>
	 */
	public enum VatInfo{
		VAT_DEFAULT, VAT_NONE,
		VAT_CH_ISMEDICAMENT, VAT_CH_NOTMEDICAMENT,
		VAT_CH_ISTREATMENT, VAT_CH_NOTTREATMENT;
		
		/**
		 * Get a String representation of a set of {@link VatInfo}
		 * elements for persisting the information.
		 * 
		 * @param set
		 * @return
		 */
		public static String encodeAsString(EnumSet<VatInfo> set) {
			StringBuilder sb = new StringBuilder();
			
			for(VatInfo info : set) {
				if(sb.length() == 0)
					sb.append(info.name());
				else
					sb.append("," + info.name());
			}
			return sb.toString();
		}
		
		/**
		 * Get an EnumSet of {@link VatInfo} from a String representation
		 * produced with {@link VatInfo#encodeAsString(EnumSet)}.
		 * 
		 * @param code
		 * @return
		 */
		public static EnumSet<VatInfo> decodeFromString(String code) {
			String[] names = code.split(",");
			EnumSet<VatInfo> ret = EnumSet.noneOf(VatInfo.class);
			
			for(int i = 0; i < names.length; i++) {
				ret.add(VatInfo.valueOf(names[i]));
			}
			return ret;
		}
	};
	
	public static IOptifier optifier = new DefaultOptifier();
	public static Comparator<IVerrechenbar> comparator = new IVerrechenbar.DefaultComparator();
	public static IFilter ifilter = new IVerrechenbar.DefaultFilter();
	
	public IOptifier getOptifier();
	
	/** Einen Comparator zum Sortieren von Leistungen dieses Typs liefern */
	public Comparator<IVerrechenbar> getComparator();
	
	/** Einen Filter liefern, um Elemente dieses Typs nach Mandant zu filtern */
	public IFilter getFilter(Mandant m);
	
	/**
	 * Betrag dieser Verrechenbar (in TP*100) an einem bestimmten Datum liefern
	 */
	public int getTP(TimeTool date, Fall fall);
	
	public double getFactor(TimeTool date, Fall fall);
	
	/**
	 * Eigene Kosten für diese Leistung
	 * 
	 * @param dat
	 *            Datum, für das die Kosten geliefert werden sollen
	 */
	public Money getKosten(TimeTool dat);
	
	/** Zeitanrechnung für diese Leistung (in Minuten) */
	public int getMinutes();
	
	public String getXidDomain();
	
	/** Die MWSt Informationen zu dieser Leistung */
	public VatInfo getVatInfo();
	
	// public AbstractDataLoaderJob getDataloader();
	// public String[] getDisplayedFields();
	
	public static class DefaultComparator implements Comparator<IVerrechenbar> {
		public int compare(final IVerrechenbar v1, final IVerrechenbar v2){
			int i = v1.getCodeSystemName().compareTo(v2.getCodeSystemName());
			if (i == 0) {
				i = v1.getCode().compareTo(v2.getCode());
			}
			return i;
		}
		
	}
	
	public static class DefaultFilter implements IFilter {
		public boolean select(final Object toTest){
			return true;
		}
		
	}
	
	public static class DefaultOptifier implements IOptifier {
		
		public Result<Object> optify(final Konsultation kons){
			return new Result<Object>(kons);
		}
		
		public Result<IVerrechenbar> add(final IVerrechenbar code, final Konsultation kons){
			List<Verrechnet> old = kons.getLeistungen();
			Verrechnet foundVerrechnet = null;
			for (Verrechnet verrechnet : old) {
				if (verrechnet.getVerrechenbar().getId().equals(code.getId())) {
					if (verrechnet.getText().equals(code.getText())) {
						foundVerrechnet = verrechnet;
						break;
					}
				}
			}
			
			if (foundVerrechnet != null) {
				foundVerrechnet.changeAnzahl(foundVerrechnet.getZahl() + 1);
			} else {
				old.add(new Verrechnet(code, kons, 1));
			}
			return new Result<IVerrechenbar>(code);
		}
		
		public Result<Verrechnet> remove(final Verrechnet v, final Konsultation kons){
			List<Verrechnet> old = kons.getLeistungen();
			old.remove(v);
			v.delete();
			return new Result<Verrechnet>(null);
		}
		
	}
	
	public static class NoObligationOptifier extends DefaultOptifier {
		@Override
		public Result<IVerrechenbar> add(IVerrechenbar code, Konsultation kons){
			String gesetz = kons.getFall().getRequiredString("Gesetz");
			
			boolean forceObligation = Hub.userCfg.get(Leistungscodes.OBLIGATION, false);
			
			if (forceObligation && gesetz.equalsIgnoreCase("KVG")) {
				SelectFallNoObligationDialog dlg =
					new SelectFallNoObligationDialog(kons.getFall(), code);

				if (dlg.open() == Dialog.OK) {
					Fall noOblFall = dlg.getFall();
					// check if there is a Konsultation in the selected Fall on the same date
					Konsultation noOblKons = getKonsFromFallByDate(noOblFall, kons.getDatum());
					// create new Konsultation if there is none matching
					if (noOblKons == null) {
						noOblKons = noOblFall.neueKonsultation();
						// transfer diagnoses to the Konsultation
						List<IDiagnose> diagnoses = kons.getDiagnosen();
						for (IDiagnose diag : diagnoses)
							noOblKons.addDiagnose(diag);
					}
					// add the no obligation IVerrechenbar to the new Konsultation
					noOblKons.addLeistung(code);
					// return ok
					return new Result<IVerrechenbar>(code);
				}
				return new Result<IVerrechenbar>(
					Result.SEVERITY.WARNING,
					0,
					"Auf diesen Fall können nur Pflichtleistungen verrechnet werden. Bitte einen separaten Fall für Nichtpflichtleistungen anlegen.",
					null, false);
			}
			
			return super.add(code, kons);
		}
		
		private Konsultation getKonsFromFallByDate(Fall fall, String date){
			Konsultation[] konsen = fall.getBehandlungen(false);
			for (int i = 0; i < konsen.length; i++) {
				Konsultation kons = konsen[i];
				if (kons.getDatum().equals(date)) {
					return kons;
				}
			}
			return null;
		}
	}
}
