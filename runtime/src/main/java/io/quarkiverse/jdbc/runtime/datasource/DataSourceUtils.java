/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.quarkiverse.jdbc.runtime.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.exception.CannotGetJdbcConnectionException;

/**
 * Utility methods for obtaining and releasing JDBC Connections.
 *
 * <p>This class intentionally keeps the Spring JDBC call shape while using
 * Quarkus/Agroal semantics internally. Transaction enlistment is handled by
 * Agroal and Narayana when a JTA transaction is active.
 */
public abstract class DataSourceUtils {

    private static final Logger logger = Logger.getLogger(DataSourceUtils.class);

    public static Connection getConnection(DataSource dataSource) throws CannotGetJdbcConnectionException {
        try {
            return doGetConnection(dataSource);
        }
        catch (SQLException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", ex);
        }
        catch (IllegalStateException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", ex);
        }
    }

    public static Connection doGetConnection(DataSource dataSource) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("No DataSource specified");
        }
        Connection con = dataSource.getConnection();
        if (con == null) {
            throw new IllegalStateException("DataSource returned null from getConnection(): " + dataSource);
        }
        return con;
    }

    public static void releaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) {
        try {
            doReleaseConnection(con, dataSource);
        }
        catch (SQLException ex) {
            logger.debug("Could not close JDBC Connection", ex);
        }
        catch (Throwable ex) {
            logger.debug("Unexpected exception on closing JDBC Connection", ex);
        }
    }

    public static void doReleaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) throws SQLException {
        if (con != null) {
            con.close();
        }
    }

    public static boolean isConnectionTransactional(Connection con, @Nullable DataSource dataSource) {
        return false;
    }

    public static void applyTimeout(Statement stmt, @Nullable DataSource dataSource, int timeout) throws SQLException {
        if (timeout >= 0) {
            stmt.setQueryTimeout(timeout);
        }
    }

    public static void applyTransactionTimeout(Statement stmt, @Nullable DataSource dataSource) throws SQLException {
    }

    public static Connection getTargetConnection(Connection con) {
        Connection conToUse = con;
        while (conToUse instanceof ConnectionProxy connectionProxy) {
            conToUse = connectionProxy.getTargetConnection();
        }
        return conToUse;
    }
}
