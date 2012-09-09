/*******************************************************************************
 * Copyright (c) 2006-2009, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    
 *    $Id: Bestellung.java 5317 2009-05-24 15:00:37Z rgw_ch $
 *******************************************************************************/

package ch.elexis.data;

import java.text.SimpleDateFormat; //20120126js re. timestamp validity checking for GetLabel() below.
import java.util.ArrayList;
import java.util.List;

import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

public class Bestellung extends PersistentObject {
	private static final String ITEM_FIELD = "Liste"; //$NON-NLS-1$
	private static final String TABLENAME = "HEAP2"; //$NON-NLS-1$
	private List<Item> alItems;
	
	public enum ListenTyp {
		PHARMACODE, NAME, VOLL
	};
	
	static {
		addMapping(TABLENAME, "Liste=S:C:Contents"); //$NON-NLS-1$
	}
	
	public Bestellung(String name, Anwender an){
		TimeTool t = new TimeTool();
		create(name + ":" + t.toString(TimeTool.TIMESTAMP) + ":" + an.getId()); //$NON-NLS-1$ //$NON-NLS-2$
		alItems = new ArrayList<Item>();
	}
	
	@Override
	public String getLabel(){
		//System.out.print("jsdebug: Bestellung.java 1\n");
		String[] i = getId().split(":"); //$NON-NLS-1$
		//System.out.print("jsdebug: i[]:  "+i.toString()+"\n");
		//System.out.print("jsdebug: i[].length:  "+i.length+"\n");
		//System.out.print("jsdebug: i[0]:  "+i[0]+"\n");
		//System.out.print("jsdebug: i[1]:  "+i[1]+"\n");
		//System.out.print("jsdebug: TimeTool(i[0]):  "+new TimeTool(i[0]).toString(TimeTool.FULL_GER)+"\n");
		//System.out.print("jsdebug: TimeTool(i[1]):  "+new TimeTool(i[1]).toString(TimeTool.FULL_GER)+"\n");
		
		//System.out.print("jsdebug: Bestellung.java 2\n");
		/*
		 * 201201260750js
		 * After the upgrade from 1.4.1 to 2.1.6.js, an error message would occur whenever the dialog
		 * to select a preceding order was opened. I found that one entriy is returned in the query
		 * for previous orders, that have only one element, instead of two. I.e. the timestamp is missing.
		 * The first element, which would usually contain something like "Automatisch:", contains
		 * "MedikamentATCache" instead. And as far as I remember, the same behaviour occured, when I
		 * started with an empty database and version 2.1.6.
		 * 
		 * Moreover, some entries contain hash values instead of timestams. The TimeTool will return
		 * the current date in that case, but I think we should rather NOT do that but return the original
		 * value unchanged instead.
		 * 
		 * So I extend the original two lines of code:
		 * TimeTool t = new TimeTool(i[1]);
		 * return i[0] + ": " + t.toString(TimeTool.FULL_GER); //$NON-NLS-1$
		 * by something more intelligent.
		 */		
		String OrderInfo = new String("");
		String OrderDateTime = new String("");
		if (i.length>0) {
			OrderInfo = i[0].toString();
			if (i.length>1) {														//js: only if we have 2 items at all
				SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");	//js: specify reference timestamp format
				format.setLenient(false);
				try {
					format.parse(i[1]);												//js: only if i[1] matches this format
					TimeTool t = new TimeTool(i[1]);								//js: convert the timestamp to the desired format
					OrderDateTime = t.toString(TimeTool.FULL_GER); 
				}
				catch (Exception e) {												//js: otherwise
					System.out.print("jsdebug: Bestellung.java.getlabel(): A should-be-timestamp failed the format test -\n");
					System.out.print("jsdebug:                             Please find out how the respective entry got into the list of orders.\n");
					OrderDateTime = i[1];											//js: return the second item unchanged.
				}
			}
			else {
				System.out.print("jsdebug: Bestellung.java.getlabel(): A should-be-order:timestamp pair contained less than 2 components -\n");		
				System.out.print("jsdebug:                             please find out how the respective entry got into the list of orders.\n");
							}
		}
		else {
			System.out.print("jsdebug: Bestellung.java.getlabel(): A should-be-order:timestamp pair was completely empty -\n");		
			System.out.print("jsdebug:                             please find out how the respective entry got into the list of orders.\n");
					}
		//System.out.print("jsdebug: Bestellung.java 3\n");
		return OrderInfo + ": " + OrderDateTime ; //$NON-NLS-1$
		}
	
	
	public String asString(ListenTyp type){
		StringBuilder ret = new StringBuilder();
		for (Item i : alItems) {
			switch (type) {
			case PHARMACODE:
				ret.append(i.art.getPharmaCode());
				break;
			case NAME:
				ret.append(i.art.getLabel());
				break;
			case VOLL:
				ret.append(i.art.getPharmaCode()).append(StringTool.space).append(i.art.getName());
				break;
			default:
				break;
			}
			ret.append(",").append(i.num).append(StringTool.lf); //$NON-NLS-1$
		}
		return ret.toString();
	}
	
	public List<Item> asList(){
		return alItems;
	}
	
	public void addItem(Artikel art, int num){
		Item i = findItem(art);
		if (i != null) {
			i.num += num;
		} else {
			alItems.add(new Item(art, num));
		}
	}
	
	public Item findItem(Artikel art){
		for (Item i : alItems) {
			if (i.art.getId().equals(art.getId())) {
				return i;
			}
		}
		return null;
	}
	
	public void removeItem(Item art){
		alItems.remove(art);
		
	}
	
	public void save(){
		StringBuilder sb = new StringBuilder();
		for (Item i : alItems) {
			sb.append(i.art.getId()).append(",").append(i.num).append(";"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		set(ITEM_FIELD, sb.toString());
	}
	
	public void load(){
		String[] it = checkNull(get(ITEM_FIELD)).split(";"); //$NON-NLS-1$
		if (alItems == null) {
			alItems = new ArrayList<Item>();
		} else {
			alItems.clear();
		}
		for (String i : it) {
			String[] fld = i.split(","); //$NON-NLS-1$
			if (fld.length == 2) {
				Artikel art = Artikel.load(fld[0]);
				if (art.exists()) {
					alItems.add(new Item(art, Integer.parseInt(fld[1])));
				}
			}
		}
	}
	
	@Override
	protected String getTableName(){
		return TABLENAME;
	}
	
	public static Bestellung load(String id){
		Bestellung ret = new Bestellung(id);
		if (ret != null) {
			ret.load();
		}
		return ret;
	}
	
	protected Bestellung(){}
	
	protected Bestellung(String id){
		super(id);
	}
	
	public static class Item {
		public Item(Artikel a, int n){
			art = a;
			num = n;
		}
		
		public Artikel art;
		public int num;
	}
}
