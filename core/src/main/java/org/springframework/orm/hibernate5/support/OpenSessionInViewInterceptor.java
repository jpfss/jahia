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
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
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
package org.springframework.orm.hibernate5.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.hibernate5.SessionFactoryUtils;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.web.context.request.AsyncWebRequestInterceptor;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;

/**
 * Spring web request interceptor that binds a Hibernate {@code Session} to the
 * thread for the entire processing of the request.
 *
 * <p>This class is a concrete expression of the "Open Session in View" pattern, which
 * is a pattern that allows for the lazy loading of associations in web views despite
 * the original transactions already being completed.
 *
 * <p>This interceptor makes Hibernate Sessions available via the current thread,
 * which will be autodetected by transaction managers. It is suitable for service layer
 * transactions via {@link org.springframework.orm.hibernate5.HibernateTransactionManager}
 * as well as for non-transactional execution (if configured appropriately).
 *
 * <p>In contrast to {@link OpenSessionInViewFilter}, this interceptor is configured
 * in a Spring application context and can thus take advantage of bean wiring.
 *
 * <p><b>WARNING:</b> Applying this interceptor to existing logic can cause issues
 * that have not appeared before, through the use of a single Hibernate
 * {@code Session} for the processing of an entire request. In particular, the
 * reassociation of persistent objects with a Hibernate {@code Session} has to
 * occur at the very beginning of request processing, to avoid clashes with already
 * loaded instances of the same objects.
 *
 * @author Juergen Hoeller
 * @see OpenSessionInViewFilter
 * @see OpenSessionInterceptor
 * @see org.springframework.orm.hibernate5.HibernateTransactionManager
 * @see TransactionSynchronizationManager
 * @see SessionFactory#getCurrentSession()
 * @since 4.2
 */
public class OpenSessionInViewInterceptor implements AsyncWebRequestInterceptor {

    /**
     * Suffix that gets appended to the {@code SessionFactory}
     * {@code toString()} representation for the "participate in existing
     * session handling" request attribute.
     *
     * @see #getParticipateAttributeName
     */
    public static final String PARTICIPATE_SUFFIX = ".PARTICIPATE";

    protected final Log logger = LogFactory.getLog(getClass());

    private SessionFactory sessionFactory;

    /**
     * Return the Hibernate SessionFactory that should be used to create Hibernate Sessions.
     */
    public SessionFactory getSessionFactory() {
        return this.sessionFactory;
    }

    /**
     * Set the Hibernate SessionFactory that should be used to create Hibernate Sessions.
     */
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private SessionFactory obtainSessionFactory() {
        SessionFactory sf = getSessionFactory();
        Assert.state(sf != null, "No SessionFactory set");
        return sf;
    }


    /**
     * Open a new Hibernate {@code Session} according and bind it to the thread via the
     * {@link TransactionSynchronizationManager}.
     */
    @Override
    public void preHandle(WebRequest request) throws DataAccessException {
        String key = getParticipateAttributeName();
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        if (asyncManager.hasConcurrentResult() && applySessionBindingInterceptor(asyncManager, key)) {
            return;
        }

        if (TransactionSynchronizationManager.hasResource(obtainSessionFactory())) {
            // Do not modify the Session: just mark the request accordingly.
            Integer count = (Integer) request.getAttribute(key, WebRequest.SCOPE_REQUEST);
            int newCount = (count != null ? count + 1 : 1);
            request.setAttribute(getParticipateAttributeName(), newCount, WebRequest.SCOPE_REQUEST);
        } else {
            logger.debug("Opening Hibernate Session in OpenSessionInViewInterceptor");
            Session session = openSession();
            SessionHolder sessionHolder = new SessionHolder(session);
            TransactionSynchronizationManager.bindResource(obtainSessionFactory(), sessionHolder);

            AsyncRequestInterceptor asyncRequestInterceptor =
                    new AsyncRequestInterceptor(obtainSessionFactory(), sessionHolder);
            asyncManager.registerCallableInterceptor(key, asyncRequestInterceptor);
            asyncManager.registerDeferredResultInterceptor(key, asyncRequestInterceptor);
        }
    }

    @Override
    public void postHandle(WebRequest request, ModelMap model) {
    }

    /**
     * Unbind the Hibernate {@code Session} from the thread and close it).
     *
     * @see TransactionSynchronizationManager
     */
    @Override
    public void afterCompletion(WebRequest request, Exception ex) throws DataAccessException {
        if (!decrementParticipateCount(request)) {
            SessionHolder sessionHolder =
                    (SessionHolder) TransactionSynchronizationManager.unbindResource(obtainSessionFactory());
            logger.debug("Closing Hibernate Session in OpenSessionInViewInterceptor");
            SessionFactoryUtils.closeSession(sessionHolder.getSession());
        }
    }

    private boolean decrementParticipateCount(WebRequest request) {
        String participateAttributeName = getParticipateAttributeName();
        Integer count = (Integer) request.getAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        if (count == null) {
            return false;
        }
        // Do not modify the Session: just clear the marker.
        if (count > 1) {
            request.setAttribute(participateAttributeName, count - 1, WebRequest.SCOPE_REQUEST);
        } else {
            request.removeAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        }
        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(WebRequest request) {
        if (!decrementParticipateCount(request)) {
            TransactionSynchronizationManager.unbindResource(obtainSessionFactory());
        }
    }

    /**
     * Open a Session for the SessionFactory that this interceptor uses.
     * <p>The default implementation delegates to the {@link SessionFactory#openSession}
     * method and sets the {@link Session}'s flush mode to "MANUAL".
     *
     * @return the Session to use
     * @throws DataAccessResourceFailureException if the Session could not be created
     * @see FlushMode#MANUAL
     */
    protected Session openSession() throws DataAccessResourceFailureException {
        try {
            Session session = obtainSessionFactory().openSession();
            session.setHibernateFlushMode(FlushMode.MANUAL);
            return session;
        } catch (HibernateException ex) {
            throw new DataAccessResourceFailureException("Could not open Hibernate Session", ex);
        }
    }

    /**
     * Return the name of the request attribute that identifies that a request is
     * already intercepted.
     * <p>The default implementation takes the {@code toString()} representation
     * of the {@code SessionFactory} instance and appends {@link #PARTICIPATE_SUFFIX}.
     */
    protected String getParticipateAttributeName() {
        return obtainSessionFactory().toString() + PARTICIPATE_SUFFIX;
    }

    private boolean applySessionBindingInterceptor(WebAsyncManager asyncManager, String key) {
        CallableProcessingInterceptor cpi = asyncManager.getCallableInterceptor(key);
        if (cpi == null) {
            return false;
        }
        ((AsyncRequestInterceptor) cpi).bindSession();
        return true;
    }

}