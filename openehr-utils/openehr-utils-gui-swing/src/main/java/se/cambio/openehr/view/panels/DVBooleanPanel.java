package se.cambio.openehr.view.panels;

import org.openehr.rm.datatypes.basic.DataValue;
import org.openehr.rm.datatypes.basic.DvBoolean;

import se.cambio.openehr.util.OpenEHRLanguageManager;

public class DVBooleanPanel extends DVComboBoxPanel implements DVPanelInterface{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public DVBooleanPanel(String idElement, String idTemplate, boolean allowNull, boolean requestFocus){
	super(idElement, idTemplate, allowNull, requestFocus);
	insertOption("false", OpenEHRLanguageManager.getMessage("False"), OpenEHRLanguageManager.getMessage("False"));
	insertOption("true", OpenEHRLanguageManager.getMessage("True"), OpenEHRLanguageManager.getMessage("True"));
    }

    public void setDataValue(DataValue dataValue) {
	String code = "";
	if (dataValue instanceof DvBoolean){
	    code =((DvBoolean) dataValue).toString();
	}
	getComboBox().setSelectedItem(code);
    }
    

    public DataValue getDataValue(){
	return new DvBoolean(new Boolean(""+getComboBox().getSelectedItem()));
    }
}
/*
 *  ***** BEGIN LICENSE BLOCK *****
 *  Version: MPL 2.0/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  2.0 (the 'License'); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an 'AS IS' basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *
 *  The Initial Developers of the Original Code are Iago Corbal and Rong Chen.
 *  Portions created by the Initial Developer are Copyright (C) 2012-2013
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *
 * Software distributed under the License is distributed on an 'AS IS' basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 *  ***** END LICENSE BLOCK *****
 */