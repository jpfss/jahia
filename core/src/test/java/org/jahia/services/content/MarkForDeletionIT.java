/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2015 Jahia Solutions Group SA. All rights reserved.
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
package org.jahia.services.content;

import org.jahia.api.Constants;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.sites.JahiaSite;
import org.jahia.test.utils.TestHelper;
import org.jahia.test.framework.AbstractJUnitTest;
import org.jahia.utils.LanguageCodeConverters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

import java.util.*;

import static org.junit.Assert.*;

/**
 * This class tests different aspects of our "mark for deletion" implementation
 * 
 */
public class MarkForDeletionIT extends AbstractJUnitTest {
    private static String DEFAULT_LANGUAGE = "en";
    
    private final static String TESTSITE_NAME = "markedForDeletionTest";
    private final static String SITECONTENT_ROOT_NODE = "/sites/" + TESTSITE_NAME;
    
    private static final String MEETING = "meeting";
    private static final String PARIS = "paris";
    private static final String GENEVA = "geneva";
    private static final String KLAGENFURT = "klagenfurt";
    private static final String DUESSELDORF = "duesseldorf";
    
    private static final String DELETION_MESSAGE = "Deleted in unit test";

    private static final String QUERY = "select * from ["
            + Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT + "]";

    JCRSessionWrapper editSession;
    JCRSessionWrapper liveSession;

    @Override
    public void beforeClassSetup() throws Exception {
        super.beforeClassSetup();
        JahiaSite site = TestHelper.createSite(TESTSITE_NAME, null);
        assertNotNull(site);

        JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE,
                LanguageCodeConverters.languageCodeToLocale(DEFAULT_LANGUAGE));

        initContent(session);
    }

    @Override
    public void afterClassSetup() throws Exception {
        super.afterClassSetup();
        TestHelper.deleteSite(TESTSITE_NAME);
    }
    
    @Before
    public void setUp() {

    }

    @After
    public void tearDown() {

    }    
    
    @Test
    public void testMarkForDeletionOnUnpublished() throws Exception {
        reopenSession();

        JCRNodeWrapper node = editSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 0);
        node.markForDeletion(DELETION_MESSAGE);
        editSession.save();
        
        assertTrue("jmix:markedForDeletionRoot not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertTrue("marked for deletion comment not set", node.getPropertyAsString(Constants.MARKED_FOR_DELETION_MESSAGE).equals(DELETION_MESSAGE));
        assertTrue("j:deletionUser not set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertTrue("j:deletionDate not set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
        
        try {
            liveSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 0);
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        JCRPublicationService jcrService = ServicesRegistry.getInstance()
                .getJCRPublicationService();

        reopenSession();

        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        
        try {
            editSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 0);
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            liveSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 0);
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
    }

    @Test
    public void testMarkForDeletionOnPublished() throws Exception {
        reopenSession();

        JCRNodeWrapper node = editSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 1);
        
        JCRPublicationService jcrService = ServicesRegistry.getInstance()
                .getJCRPublicationService();        
        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        
        node = editSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 1);
        
        node.markForDeletion(DELETION_MESSAGE);
        editSession.save();
        
        assertTrue("jmix:markedForDeletionRoot not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertTrue("marked for deletion comment not set", node.getPropertyAsString(Constants.MARKED_FOR_DELETION_MESSAGE).equals(DELETION_MESSAGE));
        assertTrue("j:deletionUser not set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertTrue("j:deletionDate not set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
        
        JCRNodeWrapper liveNode = liveSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 1);
        assertNotNull("Node is no longer existing in live workspace", liveNode);

        reopenSession();

        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        
        try {
            editSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 1);
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            liveSession.getNode("/sites/markedForDeletionTest/contents/" + MEETING + 1);
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }    
    }
    
    @Test
    public void testMarkForDeletionWithChildren() throws Exception {
        reopenSession();
        
        JCRNodeWrapper node = editSession.getNode("/sites/markedForDeletionTest/pages/page3");
        
        JCRPublicationService jcrService = ServicesRegistry.getInstance()
                .getJCRPublicationService();         
        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        
        node = editSession.getNode("/sites/markedForDeletionTest/pages/page3");
        JCRNodeWrapper childNode = editSession.getNode("/sites/markedForDeletionTest/pages/page3/page31");
        
        node.markForDeletion(DELETION_MESSAGE);
        editSession.save();
        
        assertTrue("jmix:markedForDeletionRoot not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertTrue("marked for deletion comment not set", node.getPropertyAsString(Constants.MARKED_FOR_DELETION_MESSAGE).equals(DELETION_MESSAGE));
        assertTrue("j:deletionUser not set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertTrue("j:deletionDate not set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
        
        assertFalse("jmix:markedForDeletionRoot should not be set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertFalse("marked for deletion comment should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_MESSAGE));
        assertFalse("j:deletionUser should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertFalse("j:deletionDate should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_DATE));

        reopenSession();

        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();

        try {
            editSession.getNode("/sites/markedForDeletionTest/pages/page3/page31");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            liveSession
                    .getNode("/sites/markedForDeletionTest/pages/page3/page31");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            editSession.getNode("/sites/markedForDeletionTest/pages/page3");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            liveSession
                    .getNode("/sites/markedForDeletionTest/pages/page3");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
    }    
    
    @Test
    public void testMarkForDeletionWithChildrenAlreadyMarked() throws Exception {
        reopenSession();

        JCRNodeWrapper node = editSession.getNode("/sites/markedForDeletionTest/pages/page2");
        
        JCRPublicationService jcrService = ServicesRegistry.getInstance()
                .getJCRPublicationService();         
        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        node = editSession.getNode("/sites/markedForDeletionTest/pages/page2/page21");
        JCRNodeWrapper parentNode = editSession.getNode("/sites/markedForDeletionTest/pages/page2");
        
        node.markForDeletion(DELETION_MESSAGE);
        editSession.save();
        
        assertTrue("jmix:markedForDeletionRoot not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertTrue("marked for deletion comment not set", node.getPropertyAsString(Constants.MARKED_FOR_DELETION_MESSAGE).equals(DELETION_MESSAGE));
        assertTrue("j:deletionUser not set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertTrue("j:deletionDate not set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
                
        parentNode.markForDeletion("2nd " + DELETION_MESSAGE);
        editSession.save();
        
        assertTrue("jmix:markedForDeletionRoot not set", parentNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", parentNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertTrue("marked for deletion comment not set", parentNode.getPropertyAsString(Constants.MARKED_FOR_DELETION_MESSAGE).equals("2nd " + DELETION_MESSAGE));
        assertTrue("j:deletionUser not set", parentNode.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertTrue("j:deletionDate not set", parentNode.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
        
        assertFalse("jmix:markedForDeletionRoot should not be set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertFalse("marked for deletion comment should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_MESSAGE));
        assertFalse("j:deletionUser should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertFalse("j:deletionDate should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));        
        
        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page2"));
        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page2/page21"));

        reopenSession();

        jcrService.publishByMainId(parentNode.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        
        try {
            editSession.getNode("/sites/markedForDeletionTest/pages/page2/page21");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            liveSession
                    .getNode("/sites/markedForDeletionTest/pages/page2/page21");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            editSession.getNode("/sites/markedForDeletionTest/pages/page2");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }
        try {
            liveSession
                    .getNode("/sites/markedForDeletionTest/pages/page2");
            fail("Did not throw PathNotFoundException");
        } catch (PathNotFoundException e) {
            // this is expected
        }        
    }
    
    @Test
    public void testUnmarkForDeletionWithChildren() throws Exception {
        reopenSession();
       
        JCRNodeWrapper node = editSession.getNode("/sites/markedForDeletionTest/pages/page1");
        
        JCRPublicationService jcrService = ServicesRegistry.getInstance()
                .getJCRPublicationService();         
        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        node = editSession.getNode("/sites/markedForDeletionTest/pages/page1");
        JCRNodeWrapper childNode = editSession.getNode("/sites/markedForDeletionTest/pages/page1/page11");
        
        node.markForDeletion(DELETION_MESSAGE);
        editSession.save();
        
        assertTrue("jmix:markedForDeletionRoot not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertTrue("marked for deletion comment not set", node.getPropertyAsString(Constants.MARKED_FOR_DELETION_MESSAGE).equals(DELETION_MESSAGE));
        assertTrue("j:deletionUser not set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertTrue("j:deletionDate not set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
        
        assertFalse("jmix:markedForDeletionRoot should not be set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertTrue("jmix:markedForDeletion not set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertFalse("marked for deletion comment should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_MESSAGE));
        assertFalse("j:deletionUser should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertFalse("j:deletionDate should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_DATE));        

        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page1"));        
        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page1/page11"));
        
        node.unmarkForDeletion();
        editSession.save();
        
        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page1"));        
        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page1/page11"));
        
        assertFalse("jmix:markedForDeletionRoot should not be set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertFalse("jmix:markedForDeletion should not be set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertFalse("marked for deletion comment should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_MESSAGE));
        assertFalse("j:deletionDate should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
        assertFalse("j:deletionUser should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        
        assertFalse("jmix:markedForDeletionRoot should not be set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertFalse("jmix:markedForDeletion should not be set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertFalse("marked for deletion comment should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_MESSAGE));
        assertFalse("j:deletionUser should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertFalse("j:deletionDate should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_DATE));

        reopenSession();

        jcrService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, null, true, null);
        
        reopenSession();
        node = editSession.getNode("/sites/markedForDeletionTest/pages/page1");
        childNode = editSession.getNode("/sites/markedForDeletionTest/pages/page1/page11");
        
        assertNotNull("Node is already deleted", editSession.getNode("/sites/markedForDeletionTest/pages/page1"));        
        assertNotNull("Node is already deleted", editSession.getNode("/sites/markedForDeletionTest/pages/page1/page11"));        
        
        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page1"));        
        assertNotNull("Node is already deleted", liveSession.getNode("/sites/markedForDeletionTest/pages/page1/page11"));

        assertFalse("jmix:markedForDeletionRoot should not be set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertFalse("jmix:markedForDeletion should not be set", node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertFalse("marked for deletion comment should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_MESSAGE));
        assertFalse("j:deletionUser should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertFalse("j:deletionDate should not be set", node.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
        
        assertFalse("jmix:markedForDeletionRoot should not be set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT));
        assertFalse("jmix:markedForDeletion should not be set", childNode.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION));
        assertFalse("marked for deletion comment should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_MESSAGE));
        assertFalse("j:deletionUser should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_USER));
        assertFalse("j:deletionDate should not be set on child", childNode.hasProperty(Constants.MARKED_FOR_DELETION_DATE));
    }    
    
    private static void initContent(JCRSessionWrapper session) throws RepositoryException {
        int i = 0;
        Calendar calendar = new GregorianCalendar(2000, 0, 1, 12, 0);

        JCRNodeWrapper node = session.getNode("/sites/markedForDeletionTest/contents");
        session.getWorkspace().getVersionManager().checkout(node.getPath());        
        createEvent(node, MEETING, PARIS, calendar, i++);
        createEvent(node, MEETING, GENEVA, calendar, i++);
        createEvent(node, MEETING, GENEVA, calendar, i++);
        createEvent(node, MEETING, PARIS, calendar, i++);
        
        JCRNodeWrapper home = session.getNode(SITECONTENT_ROOT_NODE);
        
        JCRNodeWrapper pages = home.addNode("pages", "jnt:page");
        pages.setProperty("jcr:title", "Source");
        pages.setProperty("j:templateName", "simple");
        JCRNodeWrapper page1 = pages.addNode("page1", "jnt:page");
        page1.setProperty("jcr:title", "Page1");
        page1.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page2 = pages.addNode("page2", "jnt:page");
        page2.setProperty("jcr:title", "Page2");
        page2.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page3 = pages.addNode("page3", "jnt:page");
        page3.setProperty("jcr:title", "Page3");
        page3.setProperty("j:templateName", "simple");        
        
        JCRNodeWrapper page11 = page1.addNode("page11", "jnt:page");
        page11.setProperty("jcr:title", "Page1-1");
        page11.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page12 = page1.addNode("page12", "jnt:page");
        page12.setProperty("jcr:title", "Page1-2");
        page12.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page13 = page1.addNode("page13", "jnt:page");
        page13.setProperty("jcr:title", "Page1-3");
        page13.setProperty("j:templateName", "simple");        
        
        JCRNodeWrapper page21 = page2.addNode("page21", "jnt:page");
        page21.setProperty("jcr:title", "Page2-1");
        page21.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page22 = page2.addNode("page22", "jnt:page");
        page22.setProperty("jcr:title", "Page2-2");
        page22.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page23 = page2.addNode("page23", "jnt:page");
        page23.setProperty("jcr:title", "Page2-3");
        page23.setProperty("j:templateName", "simple");        

        JCRNodeWrapper page31 = page3.addNode("page31", "jnt:page");
        page31.setProperty("jcr:title", "Page3-1");
        page31.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page32 = page3.addNode("page32", "jnt:page");
        page32.setProperty("jcr:title", "Page3-2");
        page32.setProperty("j:templateName", "simple");        
        JCRNodeWrapper page33 = page3.addNode("page33", "jnt:page");
        page33.setProperty("jcr:title", "Page3-3");
        page33.setProperty("j:templateName", "simple");        
        
        session.save();
    }

    @Test
    public void testMixinsAndQuery() throws Exception {
        reopenSession();

        long initialNoOfMarkedNodes = JCRContentUtils.size(editSession.getWorkspace().getQueryManager()
                .createQuery(QUERY, Query.JCR_SQL2).execute().getNodes());
        
        JCRNodeWrapper parent = editSession.getNode("/sites/markedForDeletionTest/contents");
        editSession.getWorkspace().getVersionManager().checkout(parent.getPath());
        
        JCRNodeWrapper node = parent.addNode("mixinsAndQueryTest", "jnt:contentFolder");

        Calendar calendar = new GregorianCalendar(2000, 0, 1, 12, 0);
        createEvent(node, MEETING, PARIS, calendar, 1);
        createEvent(node, MEETING, GENEVA, calendar, 2);
        createEvent(node, MEETING, KLAGENFURT, calendar, 3);
        createEvent(node, MEETING, DUESSELDORF, calendar, 4);
        
        editSession.save();
        
        assertEquals("Failed to create 4 event sub-nodes", 4, JCRContentUtils.size(node.getNodes()));

        reopenSession();
        
        assertEquals(
                "Query for marked for deletion nodes delivered wrong number of results",
                0 + initialNoOfMarkedNodes,
                JCRContentUtils.size(editSession.getWorkspace().getQueryManager()
                        .createQuery(QUERY, Query.JCR_SQL2).execute().getNodes()));

        editSession.getNode("/sites/markedForDeletionTest/contents/mixinsAndQueryTest/" + MEETING + 2).markForDeletion(DELETION_MESSAGE);
        editSession.getNode("/sites/markedForDeletionTest/contents/mixinsAndQueryTest/" + MEETING + 4).markForDeletion(DELETION_MESSAGE);
        editSession.save();
        
        reopenSession();
        
        assertEquals(
                "Query for marked for deletion nodes delivered wrong number of results",
                2 + initialNoOfMarkedNodes,
                JCRContentUtils.size(editSession.getWorkspace().getQueryManager()
                        .createQuery(QUERY, Query.JCR_SQL2).execute().getNodes()));

        editSession.getNode("/sites/markedForDeletionTest/contents/mixinsAndQueryTest/" + MEETING + 2).unmarkForDeletion();
        editSession.save();
        
        reopenSession();
        
        assertEquals(
                "Query for marked for deletion nodes delivered wrong number of results",
                1 + initialNoOfMarkedNodes,
                JCRContentUtils.size(editSession.getWorkspace().getQueryManager()
                        .createQuery(QUERY, Query.JCR_SQL2).execute().getNodes()));

        editSession.getNode("/sites/markedForDeletionTest/contents/mixinsAndQueryTest/" + MEETING + 4).unmarkForDeletion();
        editSession.save();
        
        reopenSession();
        
        assertEquals(
                "Query for marked for deletion nodes delivered wrong number of results",
                0 + initialNoOfMarkedNodes,
                JCRContentUtils.size(editSession.getWorkspace().getQueryManager()
                        .createQuery(QUERY, Query.JCR_SQL2).execute().getNodes()));

    }
    
    private static void createEvent(JCRNodeWrapper node, final String eventType, String location, Calendar calendar,
                             int i)
            throws RepositoryException {
        final String name = eventType + i;
        final JCRNodeWrapper event = node.addNode(name, "jnt:event");
        event.setProperty("jcr:title", name);
        event.setProperty("eventsType", eventType);
        event.setProperty("location", location);
        event.setProperty("startDate", calendar);
    }

    private void reopenSession() throws RepositoryException {
        JCRSessionFactory.getInstance().closeAllSessions();
        editSession = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, Locale.ENGLISH);
        liveSession = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.LIVE_WORKSPACE, Locale.ENGLISH);
    }
}