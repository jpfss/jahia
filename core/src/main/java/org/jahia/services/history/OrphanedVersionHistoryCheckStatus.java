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
package org.jahia.services.history;

import org.slf4j.helpers.MessageFormatter;

/**
 * Result of the orphaned version history check operation.
 * 
 * @author Sergiy Shyrkov
 */
public class OrphanedVersionHistoryCheckStatus extends VersionHistoryCheckStatus {

    long limit;

    long orphaned;

    /**
     * Returns the maximum count of found orphaned version histories to stop the process on.
     * 
     * @return the maximum count of found orphaned version histories to stop the process on
     */
    public long getLimit() {
        return limit;
    }

    /**
     * Returns the number of found orphaned version histories.
     * 
     * @return the number of found orphaned version histories
     */
    public long getOrphaned() {
        return orphaned;
    }

    @Override
    public String toString() {
        return MessageFormatter.arrayFormat("{} version histories checked. {} orphans found. "
                + "{} version items deleted. {} complete version histories deleted.", new Long[] { checked, orphaned,
                deletedVersionItems, deleted }).getMessage();
    }
}