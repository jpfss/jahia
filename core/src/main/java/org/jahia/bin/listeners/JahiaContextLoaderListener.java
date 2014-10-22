/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect”, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 */
package org.jahia.bin.listeners;

import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.apache.pluto.driver.PortalStartupListener;
import org.codehaus.plexus.util.StringUtils;
import org.jahia.api.Constants;
import org.jahia.bin.Jahia;
import org.jahia.exceptions.JahiaException;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.osgi.FrameworkService;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.JahiaAfterInitializationService;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.applications.ApplicationsManagerServiceImpl;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.settings.SettingsBean;
import org.jahia.tools.patches.GroovyPatcher;
import org.jahia.utils.Patterns;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.jstl.core.Config;
import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Entry point and startup/shutdown listener for all Jahia services, including Spring application context, OSGi platform service etc.
 * 
 * @author Serge Huber
 */
public class JahiaContextLoaderListener extends PortalStartupListener implements
        ServletRequestListener,
        ServletRequestAttributeListener,
        HttpSessionListener,
        HttpSessionActivationListener,
        HttpSessionAttributeListener,
        HttpSessionBindingListener,
        ServletContextAttributeListener {
    
    /**
     * This event is fired when the root application context is initialized.
     * 
     * @author Sergiy Shyrkov
     */
    public static class RootContextInitializedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 8215602249732419470L;

        public RootContextInitializedEvent(Object source) {
            super(source);
        }
        
        public XmlWebApplicationContext getContext() {
            return (XmlWebApplicationContext) getSource();
        }
    }
    
    private static final transient Logger logger = LoggerFactory
            .getLogger(JahiaContextLoaderListener.class);
    
    private static Set<String> addedSystemProperties = new HashSet<String>();
    
    private static ServletContext servletContext;
    
    private static long startupTime;

    private static long sessionCount = 0;
    
    private static String pid = "";

    private static boolean contextInitialized = false;
    
    private static boolean running;
    
    private static Map<String, Object> jahiaContextListenersConfiguration;

    @SuppressWarnings("unchecked")
    private static Map<ServletRequest, Long> requestTimes = Collections.synchronizedMap(new LRUMap(1000));

    private static String webAppRoot;

    public boolean isEventInterceptorActivated(String interceptorName) {
        if (jahiaContextListenersConfiguration == null) {
            return false; // by default all event interceptor are deactivated.
        }
        Object interceptorActivatedObject = jahiaContextListenersConfiguration.get(interceptorName);
        if (interceptorActivatedObject instanceof Boolean) {
            return (Boolean) interceptorActivatedObject;
        } else if (interceptorActivatedObject instanceof String) {
            return Boolean.parseBoolean((String) interceptorActivatedObject);
        } else {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public void contextInitialized(ServletContextEvent event) {
        startupTime = System.currentTimeMillis();
        startupWithTrust(Jahia.isEnterpriseEdition() ? (Jahia.getBuildNumber() + "." + Jahia.getEEBuildNumber()) : String.valueOf(Jahia.getBuildNumber()));

        logger.info("Starting up Digital Factory, please wait...");

        servletContext = event.getServletContext();
        
        Jahia.setContextPath(servletContext.getContextPath());
        
        initWebAppRoot();
        
        if (System.getProperty("jahia.config") == null) {
            setSystemProperty("jahia.config", "");
        }
        if (System.getProperty("jahia.license") == null) {
            setSystemProperty("jahia.license", "");
        }
        
        try {
            // verify supported Java version
            Jahia.verifyJavaVersion(servletContext.getInitParameter("supported_jdk_versions"));
        } catch (JahiaInitializationException e) {
            throw new JahiaRuntimeException(e);
        }

        detectPID(servletContext);
        
        GroovyPatcher.executeScripts(servletContext, "beforeContextInitializing");

        // initialize VFS file system (solves classloader issue: https://issues.apache.org/jira/browse/VFS-228 )
        try {
            VFS.getManager();
        } catch (FileSystemException e) {
            throw new JahiaRuntimeException(e);
        }

        try {
            long timer = System.currentTimeMillis();
            logger.info("Start initializing Spring root application context");
            
            running = true;
            
            super.contextInitialized(event);
            
            logger.info("Spring Root application context initialized in {} ms", (System.currentTimeMillis() - timer));

            // initialize services registry
            ServicesRegistry.getInstance().init();
            
            // fire Spring event that the root context is initialized
            WebApplicationContext rootCtx = ContextLoader.getCurrentWebApplicationContext();
            rootCtx.publishEvent(new RootContextInitializedEvent(rootCtx));
            
            if (Jahia.isEnterpriseEdition()) {
                requireLicense();
            }
            
            boolean isProcessingServer = SettingsBean.getInstance().isProcessingServer();
            
            // execute patches after root context initialization
            if (isProcessingServer) {
                GroovyPatcher.executeScripts(servletContext, "rootContextInitialized");
            }
            
            // start OSGi container
            timer = System.currentTimeMillis();
            logger.info("Starting OSGi platform service");
            
            new FrameworkService(event.getServletContext());
            FrameworkService.getInstance().start();
            boolean stopWaiting = false;
            synchronized (FrameworkService.getInstance()) {
                while (!stopWaiting && !FrameworkService.getInstance().isStarted()) {
                    try {
                        FrameworkService.getInstance().wait(10 * 60 * 1000L);
                        stopWaiting = true;
                        logger.info("Stopped waiting for OSGi framework startup");
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }
            
            logger.info("OSGi platform service initialized in {} ms", (System.currentTimeMillis() - timer));
            
            // do initialization of all services, implementing JahiaAfterInitializationService
            initJahiaAfterInitializationServices();
            
            // register listeners after the portal is started
            ApplicationsManagerServiceImpl.getInstance().registerListeners();
            
            // set fallback locale
            Config.set(servletContext, Config.FMT_FALLBACK_LOCALE, (SettingsBean.getInstance().getDefaultLanguageCode() != null) ? SettingsBean
                    .getInstance().getDefaultLanguageCode() : Locale.ENGLISH.getLanguage());
            
            jahiaContextListenersConfiguration = (Map<String, Object>) ContextLoader.getCurrentWebApplicationContext().getBean("jahiaContextListenersConfiguration");
            if (isEventInterceptorActivated("interceptServletContextListenerEvents")) {
                SpringContextSingleton.getInstance().publishEvent(new ServletContextInitializedEvent(event.getServletContext()));
            }
            contextInitialized = true;
            
            // execute patches after the complete initialization
            if (isProcessingServer) {
                GroovyPatcher.executeScripts(servletContext, "contextInitialized");
            } else {
                // we leave the possibility to provide Groovy scripts for non-processing servers 
                GroovyPatcher.executeScripts(servletContext, "nonProcessingServer");
            }
        } catch (JahiaException e) {
            running = false;
            logger.error(e.getMessage(), e);
            throw new JahiaRuntimeException(e);
        } catch (BundleException e) {
            running = false;
            logger.error(e.getMessage(), e);
            throw new JahiaRuntimeException(e);
        } catch (RuntimeException e) {
            running = false;
            throw e;
        } finally {
            JCRSessionFactory.getInstance().closeAllSessions();
        }
    }

    private void initWebAppRoot() {
        webAppRoot = servletContext.getRealPath("/");
        if (webAppRoot != null
                && webAppRoot.length() > 1
                && webAppRoot.charAt(webAppRoot.length() - 1) == File.separatorChar) {
            webAppRoot = webAppRoot.substring(0, webAppRoot.length() - 1);
        }
        try {
            setSystemProperty("jahiaWebAppRoot", webAppRoot);
        } catch (SecurityException se) {
            logger.error(
                    "System property jahiaWebAppRoot was NOT set to "
                            + webAppRoot
                            + " successfully ! Please check app server security manager policies to allow this.",
                    se);
        }
        // let's try to read it to make sure it was set properly as this is
        // critical for Jahia startup and may fail on some application servers
        // that have SecurityManager permissions set.
        if (System.getProperty("jahiaWebAppRoot") != null
                && System.getProperty("jahiaWebAppRoot").equals(webAppRoot)) {
            logger.info("System property jahiaWebAppRoot set to " + webAppRoot
                    + " successfully.");
        } else {
            logger.error("System property jahiaWebAppRoot was NOT set to "
                    + webAppRoot
                    + " successfully ! Please check app server security manager policies to allow this.");
        }
    }

    private void initJahiaAfterInitializationServices() throws JahiaInitializationException {
        try {
            JCRSessionFactory.getInstance().setCurrentUser(JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser());

            // initializing core services
            for (JahiaAfterInitializationService service : SpringContextSingleton.getInstance().getContext()
                    .getBeansOfType(JahiaAfterInitializationService.class).values()) {
                service.initAfterAllServicesAreStarted();
            }
            
            // initializing services for modules
            ServicesRegistry.getInstance().getJahiaTemplateManagerService().getTemplatePackageRegistry()
                    .afterInitializationForModules();
        } finally {
            JCRSessionFactory.getInstance().setCurrentUser(null);
        }
    }

    public static void initJahiaAfterInitializationServices(ListableBeanFactory beanfactory)
            throws JahiaInitializationException {
        Map<String, JahiaAfterInitializationService> map = beanfactory
                .getBeansOfType(JahiaAfterInitializationService.class);
        for (JahiaAfterInitializationService service : map.values()) {
            service.initAfterAllServicesAreStarted();
        }
    }

    private void detectPID(ServletContext servletContext) {
        try {
            pid = Patterns.AT.split(ManagementFactory.getRuntimeMXBean().getName())[0];
        } catch (Exception e) {
            logger.warn("Unable to determine process id", e);
        }
    }

    private void requireLicense() {
        try {
            if (!ContextLoader.getCurrentWebApplicationContext().getBean("licenseChecker")
                    .getClass().getName().equals("org.jahia.security.license.LicenseChecker")
                    || !ContextLoader.getCurrentWebApplicationContext().getBean("LicenseFilter")
                            .getClass().getName()
                            .equals("org.jahia.security.license.LicenseFilter")) {
                throw new FatalBeanException("Required classes for license manager were not found");
            }
        } catch (NoSuchBeanDefinitionException e) {
            throw new FatalBeanException("Required classes for license manager were not found", e);
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        if (!running) {
            return;
        }
        contextInitialized = false;
        running = false;
        if (isEventInterceptorActivated("interceptServletContextListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(
                    new ServletContextDestroyedEvent(event.getServletContext()));
        }

        long timer = System.currentTimeMillis();
        logger.info("Stopping OSGi platform service");

        try {
            FrameworkService.getInstance().stop();
        } catch (Exception e) {
            logger.error("Error stopping OSGi platform service. Cause: " + e.getMessage(), e);
        }

        logger.info("OSGi platform service stopped in {} ms", (System.currentTimeMillis() - timer));

        timer = System.currentTimeMillis();
        logger.info("Shutting down Spring root application context");

        super.contextDestroyed(event);

        removeAddedSystemProperties();
        
        logger.info("Spring Root application context shut down in {} ms", (System.currentTimeMillis() - timer));
    }

    /**
     * startupWithTrust
     * AK    20.01.2001
     */
    private void startupWithTrust(String buildString) {
        StringBuilder buildBuffer = new StringBuilder();

        for (int i = 0; i < buildString.length(); i++) {
            buildBuffer.append(" ");
            buildBuffer.append(buildString.substring(i, i + 1));
        }

        StringBuilder versionBuffer = new StringBuilder();
        for (int i = 0; i < Constants.JAHIA_PROJECT_VERSION.length(); i++) {
            versionBuffer.append(" ");
            versionBuffer.append(Constants.JAHIA_PROJECT_VERSION.substring(i, i + 1));
        }

        StringBuilder codeNameBuffer = new StringBuilder();
        for (int i = 0; i < Jahia.CODE_NAME.length(); i++) {
            codeNameBuffer.append(" ");
            codeNameBuffer.append(Jahia.CODE_NAME.substring(i, i + 1));
        }

        String msg;
        InputStream is = null;
        try {
            is = this.getClass().getResourceAsStream(Jahia.isEnterpriseEdition() ? "/META-INF/jahia-ee-startup-intro.txt" : "/META-INF/jahia-startup-intro.txt");
            msg = IOUtils.toString(is);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            msg = "";
        } finally {
            IOUtils.closeQuietly(is);
        }
        msg = StringUtils.replace(msg, "@BUILD_NUMBER@", buildBuffer.toString());
        msg = StringUtils.replace(msg, "@BUILD_DATE@", Jahia.getBuildDate());
        msg = StringUtils.replace(msg, "@VERSION@", versionBuffer.toString());
        msg = StringUtils.replace(msg, "@CODENAME@", codeNameBuffer.toString());

        System.out.println (msg);
        System.out.flush();
    }

    public static ServletContext getServletContext() {
        return servletContext;
    }

    public static long getStartupTime() {
        return startupTime;
    }

    public void sessionCreated(HttpSessionEvent se) {
        sessionCount++;
        if (isEventInterceptorActivated("interceptHttpSessionListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionCreatedEvent(se.getSession()));
        }
    }

    public void sessionDestroyed(HttpSessionEvent se) {
        sessionCount--;
        if (isEventInterceptorActivated("interceptHttpSessionListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionDestroyedEvent(se.getSession()));
        }
    }
    private static Pattern httpMethod = Pattern.compile("POST|PUT|GET|DELETE");
    public void requestDestroyed(ServletRequestEvent sre) {
        requestTimes.remove(sre.getServletRequest());
        if (isEventInterceptorActivated("interceptServletRequestListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new ServletRequestDestroyedEvent(sre.getServletRequest()));
        }
    }

    public void requestInitialized(ServletRequestEvent sre) {
        ServletRequest servletRequest = sre.getServletRequest();
        if(servletRequest instanceof HttpServletRequest && httpMethod.matcher(((HttpServletRequest)servletRequest).getMethod().toUpperCase()).matches()) {
            requestTimes.put(servletRequest, System.currentTimeMillis());
        }
        if (isEventInterceptorActivated("interceptServletRequestListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new ServletRequestInitializedEvent(servletRequest));
        }
    }

    public static long getSessionCount() {
        return sessionCount;
    }

    public static long getRequestCount() {
        return requestTimes.size();
    }

    public static String getPid() {
        return pid;
    }
    
    /**
     * Sets the system property keeping track of properties, we added (there was no value present before we set it).
     * 
     * @param key
     *            the property key
     * @param value
     *            the value to be set
     */
    public static void setSystemProperty(String key, String value) {
        if (System.setProperty(key, value) == null) {
            addedSystemProperties.add(key);
        }
    }
    
    private static void removeAddedSystemProperties() {
        try {
            for (String key : addedSystemProperties) {
                System.clearProperty(key);
            }
        } finally {
            addedSystemProperties.clear();
        }
    }

    public void sessionWillPassivate(HttpSessionEvent se) {
        if (isEventInterceptorActivated("interceptHttpSessionActivationEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionWillPassivateEvent(se.getSession()));
        }
    }

    public void sessionDidActivate(HttpSessionEvent se) {
        if (isEventInterceptorActivated("interceptHttpSessionActivationEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionDidActivateEvent(se.getSession()));
        }
    }

    public void attributeAdded(HttpSessionBindingEvent se) {
        if (isEventInterceptorActivated("interceptHttpSessionAttributeListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionAttributeAddedEvent(se));
        }
    }

    public void attributeRemoved(HttpSessionBindingEvent se) {
        if (isEventInterceptorActivated("interceptHttpSessionAttributeListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionAttributeRemovedEvent(se));
        }
    }

    public void attributeReplaced(HttpSessionBindingEvent se) {
        if (isEventInterceptorActivated("interceptHttpSessionAttributeListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionAttributeReplacedEvent(se));
        }
    }

    public void valueBound(HttpSessionBindingEvent event) {
        if (isEventInterceptorActivated("interceptHttpSessionBindingListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionValueBoundEvent(event));
        }
    }

    public void valueUnbound(HttpSessionBindingEvent event) {
        if (isEventInterceptorActivated("interceptHttpSessionBindingListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new HttpSessionValueUnboundEvent(event));
        }
    }

    public void attributeAdded(ServletContextAttributeEvent scab) {
        if (contextInitialized) {
            if (isEventInterceptorActivated("interceptServletContextAttributeListenerEvents")) {
                SpringContextSingleton.getInstance().publishEvent(new ServletContextAttributeAddedEvent(scab));
            }
        }
    }

    public void attributeRemoved(ServletContextAttributeEvent scab) {
        if (contextInitialized) {
            if (isEventInterceptorActivated("interceptServletContextAttributeListenerEvents")) {
                SpringContextSingleton.getInstance().publishEvent(new ServletContextAttributeRemovedEvent(scab));
            }
        }
    }

    public void attributeReplaced(ServletContextAttributeEvent scab) {
        if (contextInitialized) {
            if (isEventInterceptorActivated("interceptServletContextAttributeListenerEvents")) {
                SpringContextSingleton.getInstance().publishEvent(new ServletContextAttributeReplacedEvent(scab));
            }
        }
    }

    public void attributeAdded(ServletRequestAttributeEvent srae) {
        if (isEventInterceptorActivated("interceptServletRequestAttributeListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new ServletRequestAttributeAddedEvent(srae));
        }
    }

    public void attributeRemoved(ServletRequestAttributeEvent srae) {
        if (isEventInterceptorActivated("interceptServletRequestAttributeListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new ServletRequestAttributeRemovedEvent(srae));
        }
    }

    public void attributeReplaced(ServletRequestAttributeEvent srae) {
        if (isEventInterceptorActivated("interceptServletRequestAttributeListenerEvents")) {
            SpringContextSingleton.getInstance().publishEvent(new ServletRequestAttributeReplacedEvent(srae));
        }
    }

    public static boolean isContextInitialized() {
        return contextInitialized;
    }

    public class HttpSessionCreatedEvent extends ApplicationEvent {
        private static final long serialVersionUID = -7421486835176013728L;
        
        public HttpSessionCreatedEvent(HttpSession session) {
            super(session);
        }

        public HttpSession getSession() {
            return (HttpSession) super.getSource();
        }
    }

    public class HttpSessionDestroyedEvent extends ApplicationEvent {
        private static final long serialVersionUID = -1387944667725619591L;
        
        public HttpSessionDestroyedEvent(HttpSession session) {
            super(session);
        }
        public HttpSession getSession() {
            return (HttpSession) super.getSource();
        }
    }

    public class ServletRequestDestroyedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 7596456549896361175L;
        
        public ServletRequestDestroyedEvent(ServletRequest servletRequest) {
            super(servletRequest);
        }

        public ServletRequest getServletRequest() {
            return (ServletRequest) super.getSource();
        }
    }

    public class ServletRequestInitializedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 5822992792782543993L;
        
        public ServletRequestInitializedEvent(ServletRequest servletRequest) {
            super(servletRequest);
        }
        public ServletRequest getServletRequest() {
            return (ServletRequest) super.getSource();
        }
    }

    public class HttpSessionWillPassivateEvent extends ApplicationEvent {
        private static final long serialVersionUID = 6886011344567163295L;
        
        public HttpSessionWillPassivateEvent(HttpSession session) {
            super(session);
        }
        public HttpSession getSession() {
            return (HttpSession) super.getSource();
        }
    }

    public class HttpSessionDidActivateEvent extends ApplicationEvent {
        private static final long serialVersionUID = 5814761122135408014L;
        
        public HttpSessionDidActivateEvent(HttpSession session) {
            super(session);
        }
        public HttpSession getSession() {
            return (HttpSession) super.getSource();
        }
    }

    public class HttpSessionAttributeAddedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 7316259699549761735L;

        public HttpSessionAttributeAddedEvent(HttpSessionBindingEvent httpSessionBindingEvent) {
            super(httpSessionBindingEvent);
        }

        public HttpSessionBindingEvent getHttpSessionBindingEvent() {
            return (HttpSessionBindingEvent) super.getSource();
        }
    }

    public class HttpSessionAttributeRemovedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 876708448117102271L;
        public HttpSessionAttributeRemovedEvent(HttpSessionBindingEvent httpSessionBindingEvent) {
            super(httpSessionBindingEvent);
        }
        public HttpSessionBindingEvent getHttpSessionBindingEvent() {
            return (HttpSessionBindingEvent) super.getSource();
        }
    }

    public class HttpSessionAttributeReplacedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 8128290080471455221L;
        public HttpSessionAttributeReplacedEvent(HttpSessionBindingEvent httpSessionBindingEvent) {
            super(httpSessionBindingEvent);
        }
        public HttpSessionBindingEvent getHttpSessionBindingEvent() {
            return (HttpSessionBindingEvent) super.getSource();
        }
    }

    public class HttpSessionValueBoundEvent extends ApplicationEvent {
        private static final long serialVersionUID = -3415824235349946403L;
        public HttpSessionValueBoundEvent(HttpSessionBindingEvent httpSessionBindingEvent) {
            super(httpSessionBindingEvent);
        }
        public HttpSessionBindingEvent getHttpSessionBindingEvent() {
            return (HttpSessionBindingEvent) super.getSource();
        }
    }

    public class HttpSessionValueUnboundEvent extends ApplicationEvent {
        private static final long serialVersionUID = 8453994121930169941L;
        public HttpSessionValueUnboundEvent(HttpSessionBindingEvent httpSessionBindingEvent) {
            super(httpSessionBindingEvent);
        }
        public HttpSessionBindingEvent getHttpSessionBindingEvent() {
            return (HttpSessionBindingEvent) super.getSource();
        }
    }

    public class ServletContextAttributeAddedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 3430737803878399224L;

        public ServletContextAttributeAddedEvent(ServletContextAttributeEvent servletContextAttributeEvent) {
            super(servletContextAttributeEvent);
        }

        public ServletContextAttributeEvent getServletContextAttributeEvent() {
            return (ServletContextAttributeEvent) super.getSource();
        }
    }

    public class ServletContextAttributeRemovedEvent extends ApplicationEvent {
        private static final long serialVersionUID = -3543715780914938235L;
        public ServletContextAttributeRemovedEvent(ServletContextAttributeEvent servletContextAttributeEvent) {
            super(servletContextAttributeEvent);
        }
        public ServletContextAttributeEvent getServletContextAttributeEvent() {
            return (ServletContextAttributeEvent) super.getSource();
        }
    }

    public class ServletContextAttributeReplacedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 5729697513603811739L;
        public ServletContextAttributeReplacedEvent(ServletContextAttributeEvent servletContextAttributeEvent) {
            super(servletContextAttributeEvent);
        }
        public ServletContextAttributeEvent getServletContextAttributeEvent() {
            return (ServletContextAttributeEvent) super.getSource();
        }
    }

    public class ServletRequestAttributeAddedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 3317475270634384739L;
        public ServletRequestAttributeAddedEvent(ServletRequestAttributeEvent servletRequestAttributeEvent) {
            super(servletRequestAttributeEvent);
        }
        public ServletRequestAttributeEvent getServletRequestAttributeEvent() {
            return (ServletRequestAttributeEvent) super.getSource();
        }
    }

    private class ServletRequestAttributeRemovedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 4181992489489417634L;
        public ServletRequestAttributeRemovedEvent(ServletRequestAttributeEvent servletRequestAttributeEvent) {
            super(servletRequestAttributeEvent);
        }
    }

    private class ServletRequestAttributeReplacedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 1785714293103597626L;
        public ServletRequestAttributeReplacedEvent(ServletRequestAttributeEvent servletRequestAttributeEvent) {
            super(servletRequestAttributeEvent);
        }
    }

    public class ServletContextInitializedEvent extends ApplicationEvent {
        private static final long serialVersionUID = 7380625349896182566L;
        public ServletContextInitializedEvent(ServletContext servletContext) {
            super(servletContext);
        }
        public ServletContext getServletContext() {
            return (ServletContext) super.getSource();
        }
    }

    private class ServletContextDestroyedEvent extends ApplicationEvent {
        private static final long serialVersionUID = -2099082546094025673L;
        public ServletContextDestroyedEvent(ServletContext servletContext) {
            super(servletContext);
        }
    }

    /**
     * Returns <code>true</code> if Jahia is either starting or is currently running, but is not in a process of shutting down.
     * 
     * @return <code>true</code> if Jahia is either starting or is currently running, but is not in a process of shutting down; otherwise
     *         returns <code>false</code>
     */
    public static boolean isRunning() {
        return running;
    }

    public static String getWebAppRoot() {
        return webAppRoot;
    }
}
