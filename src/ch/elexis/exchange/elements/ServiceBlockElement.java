/*******************************************************************************
 * Copyright (c) 2006-2010, G. Weirich, SGAM.informatics and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 * 
 *  $Id: ServiceBlockElement.java 5980 2010-01-29 05:55:25Z rgw_ch $
 *******************************************************************************/

package ch.elexis.exchange.elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;

import ch.elexis.Hub;
import ch.elexis.data.Eigenleistung;
import ch.elexis.data.ICodeElement;
import ch.elexis.data.IVerrechenbar;
import ch.elexis.data.Leistungsblock;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.PersistentObjectFactory;
import ch.elexis.exchange.XChangeExporter;
import ch.elexis.util.Extensions;
import ch.elexis.util.SWTHelper;
import ch.elexis.views.codesystems.CodeSelectorFactory;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.StringTool;

public class ServiceBlockElement extends XChangeElement {
	public static final String XMLNAME = "serviceblock";
	public static final String ENCLOSING = "serviceblocks";
	public static final String ATTR_NAME = "name";
	static java.util.List<IConfigurationElement> codesystems;
	
	static List<ICodeElement> codeElements;
	static HashMap<ICodeElement, CodeSelectorFactory> factories;
	
	static {
		codesystems = Extensions.getExtensions("ch.elexis.Verrechnungscode");
		codeElements = new ArrayList<ICodeElement>(codesystems.size());
		factories = new HashMap<ICodeElement, CodeSelectorFactory>(codesystems.size());
		for (IConfigurationElement ic : codesystems) {
			try {
				PersistentObjectFactory po =
					(PersistentObjectFactory) ic.createExecutableExtension("ElementFactory");
				CodeSelectorFactory cs =
					(CodeSelectorFactory) ic.createExecutableExtension("CodeSelectorFactory");
				if (cs == null) {
					SWTHelper.alert("Fehler", "CodeSelectorFactory is null");
				}
				ICodeElement ics = (ICodeElement) po.createTemplate(cs.getElementClass());
				if (ics == null) {
					SWTHelper.alert("Fehler", "CodeElement is null");
				}
				codeElements.add(ics);
				factories.put(ics, cs);
				
			} catch (CoreException ex) {
				ExHandler.handle(ex);
			}
		}
		
	}
	
	public ServiceBlockElement asExporter(XChangeExporter p, Leistungsblock lb){
		asExporter(p);
		setAttribute(ATTR_NAME, lb.getName());
		List<ICodeElement> ics = lb.getElements();
		for (ICodeElement ic : ics) {
			if (ic instanceof IVerrechenbar) {
				IVerrechenbar iv = (IVerrechenbar) ic;
				ServiceElement se = new ServiceElement().asExporter(sender, iv);
				add(se);
			}
		}
		return this;
	}
	
	public void doImport(){
		String name = getAttr(ATTR_NAME);
		if (!StringTool.isNothing(name)) {
			Leistungsblock ret = new Leistungsblock(name, Hub.actMandant);
			List<ServiceElement> lService =
				(List<ServiceElement>) getChildren(ServiceElement.XMLNAME, ServiceElement.class);
			for (ServiceElement se : lService) {
				XidElement xid = se.getXid();
				List<PersistentObject> ls = xid.findObject();
				boolean bFound = false;
				for (PersistentObject po : ls) {
					if (po instanceof IVerrechenbar) {
						ret.addElement((IVerrechenbar) po);
						bFound = true;
						break;
					}
				}
				if (!bFound) {
					if (!bFound) { // we do not have a object with matching XID
						String contract = se.getAttr("contractName");
						String code = se.getAttr("contractCode");
						String lname = se.getAttr("name");
						boolean bMatched = false;
						for (ICodeElement ice : codeElements) {
							if (ice.getCodeSystemName().equals(contract)) {
								CodeSelectorFactory cof = factories.get(ice);
								if (cof != null) {
									PersistentObject po = cof.findElement(code);
									if (po != null /* && po.exists() */) {
										bMatched = true;
										ret.addElement((ICodeElement) po);
										break;
									} else {
										Eigenleistung custom = Eigenleistung.load(code);
										if (custom.exists()) {
											ret.addElement(custom);
											bMatched = true;
											break;
										}
									}
								}
							}
						}
						if (!bMatched) {
							Eigenleistung custom =
								new Eigenleistung(code, lname, se.getAttr("cost"), se
									.getAttr("price"));
							ret.addElement(custom);
						}
					}
					
					/*
					 * ret.addElement(new
					 * Eigenleistung(se.getAttr(ServiceElement.ATTR_CONTRACT_CODE),
					 * se.getAttr(ServiceElement.ATTR_NAME), se.getAttr(ServiceElement.ATTR_COST),
					 * se.getAttr(ServiceElement.ATTR_PRICE)));
					 */
				}
			}
		}
		
	}
	
	@Override
	public String getXMLName(){
		return XMLNAME;
	}
	
}
