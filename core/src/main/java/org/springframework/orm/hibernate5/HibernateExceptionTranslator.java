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
package org.springframework.orm.hibernate5;

import org.hibernate.HibernateException;
import org.hibernate.JDBCException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;

import javax.persistence.PersistenceException;

/**
 * {@link PersistenceExceptionTranslator} capable of translating {@link HibernateException}
 * instances to Spring's {@link DataAccessException} hierarchy. As of Spring 4.3.2 and
 * Hibernate 5.2, it also converts standard JPA {@link PersistenceException} instances.
 *
 * <p>Extended by {@link LocalSessionFactoryBean}, so there is no need to declare this
 * translator in addition to a {@code LocalSessionFactoryBean}.
 *
 * <p>When configuring the container with {@code @Configuration} classes, a {@code @Bean}
 * of this type must be registered manually.
 *
 * @author Juergen Hoeller
 * @see org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor
 * @see SessionFactoryUtils#convertHibernateAccessException(HibernateException)
 * @see EntityManagerFactoryUtils#convertJpaAccessExceptionIfPossible(RuntimeException)
 * @since 4.2
 */
public class HibernateExceptionTranslator implements PersistenceExceptionTranslator {

    private SQLExceptionTranslator jdbcExceptionTranslator;


    /**
     * Set the JDBC exception translator for Hibernate exception translation purposes.
     * <p>Applied to any detected {@link java.sql.SQLException} root cause of a Hibernate
     * {@link JDBCException}, overriding Hibernate's own {@code SQLException} translation
     * (which is based on a Hibernate Dialect for a specific target database).
     *
     * @see java.sql.SQLException
     * @see org.hibernate.JDBCException
     * @see org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator
     * @see org.springframework.jdbc.support.SQLStateSQLExceptionTranslator
     * @since 5.1
     */
    public void setJdbcExceptionTranslator(SQLExceptionTranslator jdbcExceptionTranslator) {
        this.jdbcExceptionTranslator = jdbcExceptionTranslator;
    }


    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        if (ex instanceof HibernateException) {
            return convertHibernateAccessException((HibernateException) ex);
        }
        if (ex instanceof PersistenceException) {
            if (ex.getCause() instanceof HibernateException) {
                return convertHibernateAccessException((HibernateException) ex.getCause());
            }
            return EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(ex);
        }
        return null;
    }

    /**
     * Convert the given HibernateException to an appropriate exception from the
     * {@code org.springframework.dao} hierarchy.
     * <p>Will automatically apply a specified SQLExceptionTranslator to a
     * Hibernate JDBCException, otherwise rely on Hibernate's default translation.
     *
     * @param ex the HibernateException that occurred
     * @return a corresponding DataAccessException
     * @see SessionFactoryUtils#convertHibernateAccessException
     */
    protected DataAccessException convertHibernateAccessException(HibernateException ex) {
        if (this.jdbcExceptionTranslator != null && ex instanceof JDBCException) {
            JDBCException jdbcEx = (JDBCException) ex;
            DataAccessException dae = this.jdbcExceptionTranslator.translate(
                    "Hibernate operation: " + jdbcEx.getMessage(), jdbcEx.getSQL(), jdbcEx.getSQLException());
            if (dae != null) {
                throw dae;
            }
        }
        return SessionFactoryUtils.convertHibernateAccessException(ex);
    }

}