/*******************************************************************************
 * Copyright (c) 2010, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    
 *  $Id: CDSSGroup.java 6352 2010-05-12 17:04:22Z rgw_ch $
 *******************************************************************************/

package ch.elexis.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class CDSSGroup extends PreferencePage implements IWorkbenchPreferencePage {
	
	public CDSSGroup(){
		noDefaultAndApplyButton();
	}
	
	@Override
	protected Control createContents(Composite parent){
		Composite ret = new Composite(parent, SWT.NONE);
		ret.setLayout(new FillLayout());
		StyledText text = new StyledText(ret, SWT.NONE);
		text.setWordWrap(true);
		text.setText(Messages.CDSSGroup_ExplanationCDSSLine1
			+ Messages.CDSSGroup_ExplanationCDSSLine2 + Messages.CDSSGroup_ExplanationCDSSLine3);
		return ret;
	}
	
	@Override
	public void init(IWorkbench workbench){
	// TODO Auto-generated method stub
	
	}
	
}
