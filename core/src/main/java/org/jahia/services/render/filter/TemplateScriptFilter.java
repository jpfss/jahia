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

package org.jahia.services.render.filter;

import org.slf4j.Logger;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderException;
import org.jahia.services.render.Resource;
import org.jahia.services.render.scripting.Script;
import org.jahia.settings.SettingsBean;
import org.slf4j.profiler.Profiler;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * TemplateScriptFilter
 * <p/>
 * Execute the template script associated to the current resource.
 * <p/>
 * This is a final filter, subsequent filters will not be chained.
 */
public class TemplateScriptFilter extends AbstractFilter {

    private static Logger logger = org.slf4j.LoggerFactory.getLogger(TemplateScriptFilter.class);

    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        Profiler profiler = (Profiler) renderContext.getRequest().getAttribute("profiler");
        if (profiler != null) {
            profiler.start("render template " + resource.getResolvedTemplate());
        }

        HttpServletRequest request = renderContext.getRequest();
        Script script = (Script) request.getAttribute("script");
        renderContext.getResourcesStack().push(resource);
        StringBuffer output = new StringBuffer();
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Render " + script.getTemplate().getPath() + " for resource: " + resource +
                             " with mainResource " + renderContext.getMainResource());
            }
            if (SettingsBean.getInstance().isDevelopmentMode() && Boolean.valueOf(renderContext.getRequest().getParameter("moduleinfo")) && !resource.getNode().isNodeType("jnt:pageTemplate")) {
                output.append("\n<span class='hidden' script='").append(script.getTemplate().getInfo()).append("' node='").append(resource.getNode().getPath()).append("'></span>\n");
            }
            output.append(script.execute(resource, renderContext));
            
        } catch (RenderException e) {
            output.append(handleError(script.getTemplate().getInfo(), e, renderContext, resource));
        } finally {
            renderContext.getResourcesStack().pop();
        }
        return output.toString().trim();
    }


    private static String handleError(String template, RenderException ex, RenderContext ctx, Resource resource)
            throws RenderException {

        String content = null;

        String onError = SettingsBean.getInstance().lookupString("templates.modules.onError");

        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

        if ("propagate".equals(onError)) {
            throw ex;
        } else if ("hide".equals(onError)) {
            logger.warn("Error including the content of the template '" + template + "'. Cause: " + cause.getMessage(),
                        cause);
            content = "";
        } else {
            logger.warn("Error including the content of the template '" + template + "'. Cause: " + cause.getMessage(),
                        cause);
            StringBuilder out = new StringBuilder(256);
            out.append("<div class=\"page-fragment-error\">").append(getErrorMessage(ctx, resource)).append(
                    !"compact".equals(onError) ? ": " + getExceptionDetails(cause) : "").append("</div>");

            content = out.toString();
        }

        return content;
    }

    private static String getErrorMessage(RenderContext ctx, Resource resource) {
//        return JahiaResourceBundle.getString(null, "templates.modules.onError", resource.getLocale(), ctx.getSite().getTemplatePackageName());
        return "Module error";
    }

    private static Object getExceptionDetails(Throwable ex) {
        StringWriter out = new StringWriter();
        out.append(ex.getMessage()).append("\n<!--\n");
        ex.printStackTrace(new PrintWriter(out));
        out.append("\n-->\n");

        return out.toString();
    }

}