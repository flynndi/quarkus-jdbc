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

package io.quarkiverse.jdbc.runtime.core.simple;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.convert.ConversionService;
import io.quarkiverse.jdbc.runtime.core.JdbcOperations;
import io.quarkiverse.jdbc.runtime.core.ResultSetExtractor;
import io.quarkiverse.jdbc.runtime.core.RowCallbackHandler;
import io.quarkiverse.jdbc.runtime.core.RowMapper;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcOperations;
import io.quarkiverse.jdbc.runtime.core.namedparam.SqlParameterSource;
import io.quarkiverse.jdbc.runtime.support.DataAccessUtils;
import io.quarkiverse.jdbc.runtime.support.KeyHolder;
import io.quarkiverse.jdbc.runtime.support.rowset.SqlRowSet;

/**
 * A fluent {@code JdbcClient} with common JDBC query and update operations,
 * supporting JDBC-style positional as well as named parameters with a convenient
 * unified facade for JDBC {@code PreparedStatement} execution.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 6.1
 * @see ResultSetExtractor
 * @see RowCallbackHandler
 * @see RowMapper
 * @see JdbcOperations
 * @see NamedParameterJdbcOperations
 */
public interface JdbcClient {

    /**
     * The starting point for any JDBC operation: a custom SQL String.
     * @param sql the SQL query or update statement as a String
     * @return a chained statement specification
     */
    StatementSpec sql(String sql);


    // Static factory methods

    /**
     * Create a {@code JdbcClient} for the given {@link DataSource}.
     * @param dataSource the DataSource to obtain connections from
     */
    static JdbcClient create(DataSource dataSource) {
        return new DefaultJdbcClient(dataSource);
    }

    /**
     * Create a {@code JdbcClient} for the given {@link JdbcOperations} delegate.
     * @param jdbcTemplate the delegate to perform operations on
     */
    static JdbcClient create(JdbcOperations jdbcTemplate) {
        return new DefaultJdbcClient(jdbcTemplate);
    }

    /**
     * Create a {@code JdbcClient} for the given {@link NamedParameterJdbcOperations} delegate.
     * @param jdbcTemplate the delegate to perform operations on
     */
    static JdbcClient create(NamedParameterJdbcOperations jdbcTemplate) {
        return new DefaultJdbcClient(jdbcTemplate, null);
    }

    /**
     * Create a {@code JdbcClient} for the given {@link NamedParameterJdbcOperations} delegate.
     * @param jdbcTemplate the delegate to perform operations on
     * @param conversionService a {@link ConversionService} for converting fetched JDBC values
     * to mapped classes in {@link StatementSpec#query(Class)}
     * @since 7.0
     */
    static JdbcClient create(NamedParameterJdbcOperations jdbcTemplate, ConversionService conversionService) {
        return new DefaultJdbcClient(jdbcTemplate, conversionService);
    }


    /**
     * A statement specification for parameter bindings and query/update execution.
     */
    interface StatementSpec {

        /**
         * Apply the given fetch size to any subsequent query statement.
         * @param fetchSize the fetch size
         * @since 7.0
         */
        StatementSpec withFetchSize(int fetchSize);

        /**
         * Apply the given maximum number of rows to any subsequent query statement.
         * @param maxRows the maximum number of rows
         * @since 7.0
         */
        StatementSpec withMaxRows(int maxRows);

        /**
         * Apply the given query timeout to any subsequent query statement.
         * @param queryTimeout the query timeout in seconds
         * @since 7.0
         */
        StatementSpec withQueryTimeout(int queryTimeout);

        /**
         * Bind a positional JDBC statement parameter for "?" placeholder resolution.
         * @param value the parameter value to bind
         * @return this statement specification (for chaining)
         */
        StatementSpec param(@Nullable Object value);

        /**
         * Bind a positional JDBC statement parameter for "?" placeholder resolution.
         * @param jdbcIndex the JDBC-style index (starting with 1)
         * @param value the parameter value to bind
         * @return this statement specification (for chaining)
         */
        StatementSpec param(int jdbcIndex, @Nullable Object value);

        /**
         * Bind a positional JDBC statement parameter for "?" placeholder resolution.
         * @param jdbcIndex the JDBC-style index (starting with 1)
         * @param value the parameter value to bind
         * @param sqlType the associated SQL type (see {@link java.sql.Types})
         * @return this statement specification (for chaining)
         */
        StatementSpec param(int jdbcIndex, @Nullable Object value, int sqlType);

        /**
         * Bind a named statement parameter for ":x" placeholder resolution.
         * @param name the parameter name
         * @param value the parameter value to bind
         * @return this statement specification (for chaining)
         */
        StatementSpec param(String name, @Nullable Object value);

        /**
         * Bind a named statement parameter for ":x" placeholder resolution.
         * @param name the parameter name
         * @param value the parameter value to bind
         * @param sqlType the associated SQL type (see {@link java.sql.Types})
         * @return this statement specification (for chaining)
         */
        StatementSpec param(String name, @Nullable Object value, int sqlType);

        /**
         * Bind a var-args list of positional parameters for "?" placeholder resolution.
         * @param values the parameter values to bind
         * @return this statement specification (for chaining)
         */
        StatementSpec params(Object... values);

        /**
         * Bind a list of positional parameters for "?" placeholder resolution.
         * @param values the parameter values to bind
         * @return this statement specification (for chaining)
         */
        StatementSpec params(List<?> values);

        /**
         * Bind named statement parameters for ":x" placeholder resolution.
         * @param paramMap a map of names and parameter values to bind
         * @return this statement specification (for chaining)
         */
        StatementSpec params(Map<String, ?> paramMap);

        /**
         * Bind named statement parameters for ":x" placeholder resolution.
         * @param namedParamObject a custom parameter object with named properties serving as statement parameters
         * @return this statement specification (for chaining)
         */
        StatementSpec paramSource(Object namedParamObject);

        /**
         * Bind named statement parameters for ":x" placeholder resolution.
         * @param namedParamSource a custom {@link SqlParameterSource} instance
         * @return this statement specification (for chaining)
         */
        StatementSpec paramSource(SqlParameterSource namedParamSource);

        /**
         * Proceed towards execution of a query.
         * @return the result query specification
         */
        ResultQuerySpec query();

        /**
         * Proceed towards execution of a mapped query.
         * @param mappedClass the target class to apply a RowMapper for
         * @return the mapped query specification
         */
        <T> MappedQuerySpec<@Nullable T> query(Class<T> mappedClass);

        /**
         * Proceed towards execution of a mapped query.
         * @param rowMapper the callback for mapping each row in the ResultSet
         * @return the mapped query specification
         */
        <T extends @Nullable Object> MappedQuerySpec<T> query(RowMapper<T> rowMapper);

        /**
         * Execute a query with the provided SQL statement, processing each row with the given callback.
         * @param rch a callback for processing each row in the ResultSet
         */
        void query(RowCallbackHandler rch);

        /**
         * Execute a query with the provided SQL statement, returning a result object for the entire ResultSet.
         * @param rse a callback for processing the entire ResultSet
         * @return the value returned by the ResultSetExtractor
         */
        <T extends @Nullable Object> T query(ResultSetExtractor<T> rse);

        /**
         * Execute the provided SQL statement as an update.
         * @return the number of rows affected
         */
        int update();

        /**
         * Execute the provided SQL statement as an update.
         * @param generatedKeyHolder a KeyHolder that will hold the generated keys
         * @return the number of rows affected
         */
        int update(KeyHolder generatedKeyHolder);

        /**
         * Execute the provided SQL statement as an update.
         * @param generatedKeyHolder a KeyHolder that will hold the generated keys
         * @param keyColumnNames names of the columns that will have keys generated for them
         * @return the number of rows affected
         */
        int update(KeyHolder generatedKeyHolder, String... keyColumnNames);
    }


    /**
     * A specification for simple result queries.
     */
    interface ResultQuerySpec {

        /**
         * Retrieve the result as a row set.
         * @return a detached row set representation of the original database result
         */
        SqlRowSet rowSet();

        /**
         * Retrieve the result as a list of rows.
         * @return a list of rows represented as case-insensitive maps
         */
        List<Map<String, @Nullable Object>> listOfRows();

        /**
         * Retrieve a single row result.
         * @return the result row represented as a map
         */
        Map<String, @Nullable Object> singleRow();

        /**
         * Retrieve a single column result.
         * @return a list of rows, each represented as its single column value
         */
        List<@Nullable Object> singleColumn();

        /**
         * Retrieve a single value result.
         * @return the single value
         * @see DataAccessUtils#requiredSingleResult(Collection)
         */
        default Object singleValue() {
            return DataAccessUtils.requiredSingleResult(singleColumn());
        }

        /**
         * Retrieve a single value result, if available, as an {@link Optional} handle.
         * @return an Optional handle with the single column value from the single row
         * @see DataAccessUtils#optionalResult(Collection)
         */
        default Optional<Object> optionalValue() {
            return DataAccessUtils.optionalResult(singleColumn());
        }
    }


    /**
     * A specification for RowMapper-mapped queries.
     * @param <T> the RowMapper-declared result type
     */
    interface MappedQuerySpec<T extends @Nullable Object> {

        /**
         * Retrieve the result as a lazily resolved stream of mapped objects.
         * @return the result Stream, needing to be closed once fully processed
         */
        Stream<T> stream();

        /**
         * Retrieve the result as a pre-resolved list of mapped objects.
         * @return the result as a detached List
         */
        List<T> list();

        /**
         * Retrieve the result as an order-preserving set of mapped objects.
         * @return the result as a detached Set
         */
        default Set<T> set() {
            return new LinkedHashSet<>(list());
        }

        /**
         * Retrieve a single result as a required object instance.
         * @return the single result object
         * @see DataAccessUtils#requiredSingleResult(Collection)
         */
        default @NonNull T single() {
            return DataAccessUtils.requiredSingleResult(list());
        }

        /**
         * Retrieve a single result, if available, as an {@link Optional} handle.
         * @return an Optional handle with a single result object or none
         * @see DataAccessUtils#optionalResult(Collection)
         */
        default Optional<@NonNull T> optional() {
            return DataAccessUtils.optionalResult(list());
        }
    }
}
