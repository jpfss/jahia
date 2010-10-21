/**
 * This file is part of Jahia: An integrated WCM, DMS and Portal Solution
 * Copyright (C) 2002-2010 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program
 * Alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms contained in a separate written agreement
 * between you and Jahia Solutions Group SA. If you are unsure which license is appropriate
 * for your use, please contact the sales department at sales@jahia.com.
 */

package org.jahia.ajax.gwt.client.widget.edit.mainarea;

import com.extjs.gxt.ui.client.dnd.DND;
import com.extjs.gxt.ui.client.dnd.DropTarget;
import com.extjs.gxt.ui.client.event.DNDEvent;
import com.extjs.gxt.ui.client.widget.Header;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import org.jahia.ajax.gwt.client.core.BaseAsyncCallback;
import org.jahia.ajax.gwt.client.core.JahiaGWTParameters;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.messages.Messages;
import org.jahia.ajax.gwt.client.service.content.JahiaContentManagementService;
import org.jahia.ajax.gwt.client.util.content.actions.ContentActions;
import org.jahia.ajax.gwt.client.widget.edit.EditModeDNDListener;

/**
 * Created by IntelliJ IDEA.
 * User: toto
 * Date: Aug 18, 2009
 * Time: 7:25:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class AreaModule extends SimpleModule {

    private String moduleType;
    private String areaType = "jnt:contentList";  // todo set the areatype

    public AreaModule(String id, String path, String s, String template, String scriptInfo, String nodeTypes, String referenceType,
                      String moduleType, MainModule mainModule) {
        super(id, path, template, scriptInfo, nodeTypes, referenceType, mainModule);
        hasDragDrop = false;
        head = new Header();
        add(head);
        this.moduleType = moduleType;
        if (path.contains("/")) {
            head.setText(Messages.get("label."+moduleType) + " : " + path.substring(path.lastIndexOf('/') + 1));
        } else {
            head.setText(Messages.get("label."+moduleType)+" : "+ path);
        }
//        setBodyBorder(false);
        head.addStyleName("x-panel-header");
        head.addStyleName("x-panel-header-"+moduleType+"module");
        html = new HTML(s);
        add(html);
    }

    LayoutContainer ctn;

    @Override public void onParsed() {
        super.onParsed();
        if (!hasChildren) {
            addStyleName(moduleType);
            removeAll();

            LayoutContainer dash = new LayoutContainer();
            dash.addStyleName("dashedArea");

            ctn = new LayoutContainer();
            ctn.addStyleName(moduleType+"Template");
            ctn.addText(head.getText());
            dash.add(ctn);

            add(dash);
        } else {
            setBorders(false);
        }
    }

    @Override public void onNodeTypesLoaded() {
        if (!hasChildren) {
            DropTarget target = new ModuleDropTarget(this, node == null ? EditModeDNDListener.EMPTYAREA_TYPE : EditModeDNDListener.PLACEHOLDER_TYPE);
            target.setOperation(DND.Operation.COPY);
            target.setFeedback(DND.Feedback.INSERT);
            target.addDNDListener(mainModule.getEditLinker().getDndListener());

            if (getNodeTypes() != null) {
                String[] nodeTypesArray = getNodeTypes().split(" ");
                for (final String s : nodeTypesArray) {
                    Button button = new Button(ModuleHelper.getNodeType(s) != null ? ModuleHelper.getNodeType(
                            s).getLabel() : s);
                    button.setStyleName("button-placeholder");
                    button.addClickHandler(new ClickHandler() {
                        public void onClick(ClickEvent event) {
                            createNode(new BaseAsyncCallback<GWTJahiaNode>() {
                                public void onSuccess(GWTJahiaNode result) {
                                    if (node != null && node.isWriteable() && !node.isLocked()) {
                                        ContentActions.showContentWizard(mainModule.getEditLinker(), s, node);
                                    }
                                }
                            });
                        }
                    });
                    ctn.add(button);
                    ctn.layout();
                }
            }


        }                
    }

    @Override public String getPath() {
        return "*";
    }

    public void createNode(final AsyncCallback<GWTJahiaNode> callback) {
        if (node == null) {
            JahiaContentManagementService.App.getInstance().createNode(path.substring(0, path.lastIndexOf('/')), path.substring(path.lastIndexOf('/') + 1),
                    areaType, null,null,null,null,new AsyncCallback<GWTJahiaNode>() {
                        public void onSuccess(GWTJahiaNode result) {
                            node = result;
                            callback.onSuccess(result);
                        }

                        public void onFailure(Throwable caught) {
                            callback.onFailure(caught);
                        }
                    });
        } else {
            callback.onSuccess(node);
        }
    }
}