/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2023 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/Apache2 OR 2/JSEL
 *
 *     1/ Apache2
 *     ==================================================================================
 *
 *     Copyright (C) 2002-2023 Jahia Solutions Group SA. All rights reserved.
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
package org.jahia.services.render.filter;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.scripting.Script;

import javax.servlet.http.HttpServletRequest;

/**
 * Add node related attributes in request, this was done after the cache filter because the node related info are not needed
 * if fragment is in cache.
 *
 * Before cache refactoring this operations was done in the BaseAttributesFilter, but we move this here to avoid
 * reading the node before the cache filter
 *
 * Created by jkevan on 27/04/2017.
 */
public class NodeAttributesFilter extends AbstractFilter {

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {

        JCRNodeWrapper node = resource.getNode();
        HttpServletRequest request = renderContext.getRequest();

        chain.pushAttribute(request, "workspace", node.getSession().getWorkspace().getName());
        chain.pushAttribute(request, "currentWorkspace", node.getSession().getWorkspace().getName());

        final Script script = resource.getScript(renderContext);
        if (script != null) {
            chain.pushAttribute(request, "script", script);
            chain.pushAttribute(request, "scriptInfo", script.getView().getInfo());
        } else {
            chain.pushAttribute(request, "script", null);
            chain.pushAttribute(request, "scriptInfo", null);
        }

        if (!Resource.CONFIGURATION_INCLUDE.equals(resource.getContextConfiguration())) {
            chain.pushAttribute(request, "currentNode", node);
        }

        return null;
    }
}
