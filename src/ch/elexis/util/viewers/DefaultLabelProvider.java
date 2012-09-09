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
 *    $Id: DefaultLabelProvider.java 5322 2009-05-29 10:59:45Z rgw_ch $
 *******************************************************************************/

package ch.elexis.util.viewers;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import ch.elexis.data.PersistentObject;

/**
 * Defaultimplementation des Labelproviders. Verwendet die getLabel() Methode von PersistentObject.
 * 
 * @author Gerry
 */
public class DefaultLabelProvider extends LabelProvider implements ITableLabelProvider {
	
	public Image getColumnImage(Object element, int columnIndex){
		return null;
	}
	
	public String getColumnText(Object element, int columnIndex){
		//System.out.print("jsdebug: BestellView.DefaultLabelProvider 1\n");
		//System.out.print("jsdebug: element:     "+element.toString()+"\n");
		//System.out.print("jsdebug: columnIndex: "+columnIndex+"\n");
						
		if (element instanceof PersistentObject) {
			//System.out.print("jsdebug: BestellView.DefaultLabelProvider 2\n");
			PersistentObject po = (PersistentObject) element;
			//System.out.print("jsdebug: BestellView.DefaultLabelProvider 3\n");
			//System.out.print("jsdebug: po:            "+po.toString()+"\n");
			//System.out.print("jsdebug: po==null:      "+po==null+"\n");
			//System.out.print("jsdebug: po.getLabel(): "+po.getLabel()+"\n");
			
			return po.getLabel();
		}
		//System.out.print("jsdebug: BestellView.DefaultLabelProvider 4\n");
		return element.toString();
	}
	
	@Override
	public String getText(Object element){
		//System.out.print("jsdebug: BestellView.DefaultLabelProvider 5\n");
		if (element instanceof PersistentObject) {
			//System.out.print("jsdebug: BestellView.DefaultLabelProvider 6\n");
			PersistentObject po = (PersistentObject) element;
			//System.out.print("jsdebug: BestellView.DefaultLabelProvider 7\n");
			return po.getLabel();
		}
		//System.out.print("jsdebug: BestellView.DefaultLabelProvider 8\n");
		return element.toString();
	}
	
}