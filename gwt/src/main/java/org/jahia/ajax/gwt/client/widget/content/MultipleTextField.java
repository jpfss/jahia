/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2022 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/Apache2 OR 2/JSEL
 *
 *     1/ Apache2
 *     ==================================================================================
 *
 *     Copyright (C) 2002-2022 Jahia Solutions Group SA. All rights reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.ajax.gwt.client.widget.content;

import com.extjs.gxt.ui.client.event.*;
import com.extjs.gxt.ui.client.widget.form.Field;
import com.extjs.gxt.ui.client.widget.form.TextField;
import com.extjs.gxt.ui.client.widget.form.TriggerField;

/**
 * Text field with multiple values
 * @param <T>
 */
public class MultipleTextField<T> extends AbstractMultipleField<T> {

    private String regex;
    private String regexText;

    public MultipleTextField() {
        super();
    }

    @Override
    Field getNewField() {
        ItemField itemField = new ItemField();
        if (regex != null) {
            itemField.setRegex(regex);
        }
        if (regexText != null) {
            itemField.getMessages().setRegexText(regexText);
        }
        return itemField;
    }

    /**
     * Field for one value
     */
    private class ItemField extends TriggerField<T> {

        ItemField() {
            setEditable(true);
            setTriggerStyle("x-form-clear-trigger");
        }

        @Override
        protected void onTriggerClick(ComponentEvent ce) {
            fields.remove(this);
            this.removeAllListeners();
            MultipleTextField.this.fireEvent(Events.Change, ce);
            removeFromParent();
        }

        @Override
        public void setReadOnly(boolean readOnly) {
            super.setReadOnly(readOnly);
            setHideTrigger(readOnly);
        }
    }

    public void setRegex(String regex) {
        this.regex = regex;
        for (Field<?> field : fields) {
            if (field instanceof TextField<?>) {
                ((TextField<?>) field).setRegex(regex);
            }
        }
    }

    public void setRegexText(String regexText) {
        this.regexText = regexText;
        for (Field<?> field : fields) {
            if (field instanceof TextField<?>) {
                ((TextField<?>) field).getMessages().setRegexText(regexText);
            }
        }
    }
}
