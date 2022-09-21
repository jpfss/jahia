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
package org.jahia.services.uicomponents.bean.contentmanager;

import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRStoreProvider;
import org.jahia.services.uicomponents.bean.Visibility;
import org.jahia.services.usermanager.JahiaUser;

/**
 * Evaluates the need to display provider key column in the content manager,
 * depending on the presence of the mount points
 * 
 * @author Sergiy Shyrkov
 */
public class ProviderKeyColumnVisibility extends Visibility {

    private JCRSessionFactory sessionFactory;

    private boolean alsoCountDynamicProviders;

    @Override
    public boolean getRealValue(JCRNodeWrapper contextNode, JahiaUser jahiaUser, Locale locale, HttpServletRequest request) {
        boolean visible = false;
        List<JCRStoreProvider> providers = sessionFactory.getProviderList();
        if (providers.size() > 1) {
            if (alsoCountDynamicProviders) {
                visible = true;
            } else {
                int count = 0;
                for (JCRStoreProvider jcrStoreProvider : providers) {
                    if (!jcrStoreProvider.isDynamicallyMounted()) {
                        count++;
                        if (count > 1) {
                            visible = true;
                            break;
                        }
                    }
                }
            }
        }

        return visible;
    }

    public void setJcrSessionFactory(JCRSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * @param alsoCountDynamicProviders the alsoCountDynamicProviders to set
     */
    public void setAlsoCountDynamicProviders(boolean alsoCountDynamicProviders) {
        this.alsoCountDynamicProviders = alsoCountDynamicProviders;
    }

}