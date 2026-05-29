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

package io.quarkiverse.jdbc.runtime.core.namedparam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.jdbc.runtime.core.SqlParameterValue;
import io.quarkiverse.jdbc.runtime.exception.InvalidDataAccessApiUsageException;

class NamedParameterUtilsTests {

    @Test
    void parseSql() {
        String sql = "xxx :a yyyy :b :c :a zzzzz";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        assertEquals("xxx ? yyyy ? ? ? zzzzz", substituteNamedParameters(parsedSql));
        assertParameterNames(parsedSql, "a", "b", "c", "a");
        assertEquals(4, parsedSql.getTotalParameterCount());
        assertEquals(3, parsedSql.getNamedParameterCount());

        String sql2 = "xxx &a yyyy ? zzzzz";
        ParsedSql parsedSql2 = NamedParameterUtils.parseSqlStatement(sql2);
        assertEquals("xxx ? yyyy ? zzzzz", NamedParameterUtils.substituteNamedParameters(parsedSql2, null));
        assertParameterNames(parsedSql2, "a");
        assertEquals(2, parsedSql2.getTotalParameterCount());
        assertEquals(1, parsedSql2.getNamedParameterCount());

        String sql3 = "xxx &\u00e4+:\u00f6" + '\t' + ":\u00fc%10 yyyy ? zzzzz";
        ParsedSql parsedSql3 = NamedParameterUtils.parseSqlStatement(sql3);
        assertParameterNames(parsedSql3, "\u00e4", "\u00f6", "\u00fc");
    }

    @Test
    void substituteNamedParameters() {
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("a", "a").addValue("b", "b").addValue("c", "c");

        assertEquals("xxx ? ? ?", NamedParameterUtils.substituteNamedParameters("xxx :a :b :c", namedParams));
        assertEquals("xxx ? ? ? xx ? ?",
                NamedParameterUtils.substituteNamedParameters("xxx :a :b :c xx :a :a", namedParams));
    }

    @Test
    void convertParamMapToArray() {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("a", "a");
        paramMap.put("b", "b");
        paramMap.put("c", "c");

        assertEquals(3, NamedParameterUtils.buildValueArray("xxx :a :b :c", paramMap).length);
        assertEquals(5, NamedParameterUtils.buildValueArray("xxx :a :b :c xx :a :b", paramMap).length);
        assertEquals(5, NamedParameterUtils.buildValueArray("xxx :a :a :a xx :a :a", paramMap).length);
        assertEquals("b", NamedParameterUtils.buildValueArray("xxx :a :b :c xx :a :b", paramMap)[4]);
        assertThrows(InvalidDataAccessApiUsageException.class,
                () -> NamedParameterUtils.buildValueArray("xxx :a :b ?", paramMap));
    }

    @Test
    void convertTypeMapToArray() {
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("a", "a", 1).addValue("b", "b", 2).addValue("c", "c", 3);

        assertEquals(3,
                NamedParameterUtils.buildSqlTypeArray(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c"), namedParams).length);
        assertEquals(5,
                NamedParameterUtils.buildSqlTypeArray(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c xx :a :b"), namedParams).length);
        assertEquals(5,
                NamedParameterUtils.buildSqlTypeArray(
                        NamedParameterUtils.parseSqlStatement("xxx :a :a :a xx :a :a"), namedParams).length);
        assertEquals(2,
                NamedParameterUtils.buildSqlTypeArray(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c xx :a :b"), namedParams)[4]);
    }

    @Test
    void convertSqlParameterValueToArray() {
        SqlParameterValue sqlParameterValue = new SqlParameterValue(2, "b");
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("a", "a");
        paramMap.put("b", sqlParameterValue);
        paramMap.put("c", "c");

        assertSame(sqlParameterValue,
                NamedParameterUtils.buildValueArray("xxx :a :b :c xx :a :b", paramMap)[4]);

        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("a", "a", 1).addValue("b", sqlParameterValue).addValue("c", "c", 3);
        assertSame(sqlParameterValue,
                NamedParameterUtils.buildValueArray(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c xx :a :b"), namedParams, null)[4]);
    }

    @Test
    void convertTypeMapToSqlParameterList() {
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("a", "a", 1).addValue("b", "b", 2).addValue("c", "c", 3, "SQL_TYPE");

        assertEquals(3,
                NamedParameterUtils.buildSqlParameterList(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c"), namedParams).size());
        assertEquals(5,
                NamedParameterUtils.buildSqlParameterList(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c xx :a :b"), namedParams).size());
        assertEquals(5,
                NamedParameterUtils.buildSqlParameterList(
                        NamedParameterUtils.parseSqlStatement("xxx :a :a :a xx :a :a"), namedParams).size());
        assertEquals(2,
                NamedParameterUtils.buildSqlParameterList(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c xx :a :b"), namedParams).get(4).getSqlType());
        assertEquals("SQL_TYPE",
                NamedParameterUtils.buildSqlParameterList(
                        NamedParameterUtils.parseSqlStatement("xxx :a :b :c"), namedParams).get(2).getTypeName());
    }

    @Test
    void buildValueArrayWithMissingParameterValue() {
        String sql = "select count(0) from foo where id = :id";

        assertThrows(InvalidDataAccessApiUsageException.class,
                () -> NamedParameterUtils.buildValueArray(sql, Collections.emptyMap()));
    }

    @Test
    void substituteNamedParametersWithStringContainingQuotes() {
        String expectedSql = "select 'first name' from artists where id = ? and quote = 'exsqueeze me?'";
        String sql = "select 'first name' from artists where id = :id and quote = 'exsqueeze me?'";

        assertEquals(expectedSql, NamedParameterUtils.substituteNamedParameters(sql, new MapSqlParameterSource()));
    }

    @Test
    void parseSqlStatementWithStringContainingQuotes() {
        String expectedSql = "select 'first name' from artists where id = ? and quote = 'exsqueeze me?'";
        String sql = "select 'first name' from artists where id = :id and quote = 'exsqueeze me?'";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlContainingComments() {
        String sql1 = "/*+ HINT */ xxx /* comment ? */ :a yyyy :b :c :a zzzzz -- :xx XX\n";
        ParsedSql parsedSql1 = NamedParameterUtils.parseSqlStatement(sql1);
        assertEquals("/*+ HINT */ xxx /* comment ? */ ? yyyy ? ? ? zzzzz -- :xx XX\n",
                NamedParameterUtils.substituteNamedParameters(parsedSql1, null));

        MapSqlParameterSource paramMap = new MapSqlParameterSource();
        paramMap.addValue("a", "a");
        paramMap.addValue("b", "b");
        paramMap.addValue("c", "c");
        Object[] params = NamedParameterUtils.buildValueArray(parsedSql1, paramMap, null);
        assertArrayValues(params, "a", "b", "c", "a");

        String sql2 = "/*+ HINT */ xxx /* comment ? */ :a yyyy :b :c :a zzzzz -- :xx XX";
        ParsedSql parsedSql2 = NamedParameterUtils.parseSqlStatement(sql2);
        assertEquals("/*+ HINT */ xxx /* comment ? */ ? yyyy ? ? ? zzzzz -- :xx XX",
                NamedParameterUtils.substituteNamedParameters(parsedSql2, null));

        String sql3 = "/*+ HINT */ xxx /* comment ? */ :a yyyy :b :c :a zzzzz /* :xx XX*";
        ParsedSql parsedSql3 = NamedParameterUtils.parseSqlStatement(sql3);
        assertEquals("/*+ HINT */ xxx /* comment ? */ ? yyyy ? ? ? zzzzz /* :xx XX*",
                NamedParameterUtils.substituteNamedParameters(parsedSql3, null));

        String sql4 = "/*+ HINT */ xxx /* comment :a ? */ :a yyyy :b :c :a zzzzz /* :xx XX*";
        ParsedSql parsedSql4 = NamedParameterUtils.parseSqlStatement(sql4);
        Map<String, String> parameters = Collections.singletonMap("a", "0");
        assertEquals("/*+ HINT */ xxx /* comment :a ? */ ? yyyy ? ? ? zzzzz /* :xx XX*",
                NamedParameterUtils.substituteNamedParameters(parsedSql4, new MapSqlParameterSource(parameters)));
    }

    @Test
    void parseSqlStatementWithPostgresCasting() {
        String expectedSql = "select 'first name' from artists where id = ? and birth_date=?::timestamp";
        String sql = "select 'first name' from artists where id = :id and birth_date=:birthDate::timestamp";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlStatementWithPostgresContainedOperator() {
        String expectedSql = "select 'first name' from artists where info->'stat'->'albums' = ?? ? and '[\"1\",\"2\",\"3\"]'::jsonb ?? '4'";
        String sql = "select 'first name' from artists where info->'stat'->'albums' = ?? :album and '[\"1\",\"2\",\"3\"]'::jsonb ?? '4'";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(1, parsedSql.getTotalParameterCount());
        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlStatementWithPostgresAnyArrayStringsExistsOperator() {
        String expectedSql = "select '[\"3\", \"11\"]'::jsonb ?| '{1,3,11,12,17}'::text[]";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(expectedSql);

        assertEquals(0, parsedSql.getTotalParameterCount());
        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlStatementWithPostgresAllArrayStringsExistsOperator() {
        String expectedSql = "select '[\"3\", \"11\"]'::jsonb ?& '{1,3,11,12,17}'::text[] AND ? = 'Back in Black'";
        String sql = "select '[\"3\", \"11\"]'::jsonb ?& '{1,3,11,12,17}'::text[] AND :album = 'Back in Black'";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(1, parsedSql.getTotalParameterCount());
        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlStatementWithEscapedColon() {
        String expectedSql = "select '0\\:0' as a, foo from bar where baz < DATE(? 23:59:59) and baz = ?";
        String sql = "select '0\\:0' as a, foo from bar where baz < DATE(:p1 23\\:59\\:59) and baz = :p2";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertParameterNames(parsedSql, "p1", "p2");
        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlStatementWithBracketDelimitedParameterNames() {
        String expectedSql = "select foo from bar where baz = b??z";
        String sql = "select foo from bar where baz = b:{p1}:{p2}z";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertParameterNames(parsedSql, "p1", "p2");
        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlStatementWithEmptyBracketsOrBracketsInQuotes() {
        String expectedSql = "select foo from bar where baz = b:{}z";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(expectedSql);
        assertEquals(List.of(), parsedSql.getParameterNames());
        assertEquals(expectedSql, substituteNamedParameters(parsedSql));

        String expectedSql2 = "select foo from bar where baz = 'b:{p1}z'";
        ParsedSql parsedSql2 = NamedParameterUtils.parseSqlStatement(expectedSql2);
        assertEquals(List.of(), parsedSql2.getParameterNames());
        assertEquals(expectedSql2, NamedParameterUtils.substituteNamedParameters(parsedSql2, null));
    }

    @Test
    void parseSqlStatementWithSingleLetterInBrackets() {
        String expectedSql = "select foo from bar where baz = b?z";
        String sql = "select foo from bar where baz = b:{p}z";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertParameterNames(parsedSql, "p");
        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void parseSqlStatementWithLogicalAnd() {
        String expectedSql = "xxx & yyyy";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(expectedSql);

        assertEquals(expectedSql, substituteNamedParameters(parsedSql));
    }

    @Test
    void substituteNamedParametersWithLogicalAnd() {
        String expectedSql = "xxx & yyyy";

        assertEquals(expectedSql, NamedParameterUtils.substituteNamedParameters(expectedSql, new MapSqlParameterSource()));
    }

    @Test
    void variableAssignmentOperator() {
        String expectedSql = "x := 1";

        assertEquals(expectedSql, NamedParameterUtils.substituteNamedParameters(expectedSql, new MapSqlParameterSource()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT ':foo'':doo', :xxx FROM DUAL",
            "SELECT /*:doo*/':foo', :xxx FROM DUAL",
            "SELECT ':foo'/*:doo*/, :xxx FROM DUAL",
            "SELECT \":foo\"\":doo\", :xxx FROM DUAL",
            "SELECT `:foo``:doo`, :xxx FROM DUAL"
    })
    void parseSqlStatementWithParametersInsideQuotesAndComments(String sql) {
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(1, parsedSql.getTotalParameterCount());
        assertParameterNames(parsedSql, "xxx");
    }

    @Test
    void parseSqlStatementWithSquareBracket() {
        String sql = "SELECT ARRAY[:ext]";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(1, parsedSql.getNamedParameterCount());
        assertParameterNames(parsedSql, "ext");
        assertEquals("SELECT ARRAY[?]", substituteNamedParameters(parsedSql));
    }

    @Test
    void paramNameWithNestedSquareBrackets() {
        String sql = "insert into GeneratedAlways (id, first_name, last_name) values " +
                "(:records[0].id, :records[0].firstName, :records[0].lastName), " +
                "(:records[1].id, :records[1].firstName, :records[1].lastName)";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(Set.of(
                "records[0].id", "records[0].firstName", "records[0].lastName",
                "records[1].id", "records[1].firstName", "records[1].lastName"),
                new HashSet<>(parsedSql.getParameterNames()));
    }

    @Test
    void namedParamMapReference() {
        String sql = "insert into foos (id) values (:headers[id])";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertEquals(1, parsedSql.getNamedParameterCount());
        assertParameterNames(parsedSql, "headers[id]");

        Foo foo = new Foo();
        SqlParameterSource paramSource = new BeanPropertySqlParameterSource(foo);
        Object[] params = NamedParameterUtils.buildValueArray(parsedSql, paramSource, null);
        SqlParameterValue sqlParameterValue = assertInstanceOf(SqlParameterValue.class, params[0]);
        assertEquals(foo.getHeaders().get("id"), sqlParameterValue.getValue());

        assertEquals("insert into foos (id) values (?)",
                NamedParameterUtils.substituteNamedParameters(parsedSql, paramSource));
    }

    @Test
    void parseSqlStatementWithBackticks() {
        String sql = "select * from `tb&user` where id = :id";
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        assertParameterNames(parsedSql, "id");
        assertEquals("select * from `tb&user` where id = ?", substituteNamedParameters(parsedSql));
    }

    private static String substituteNamedParameters(ParsedSql parsedSql) {
        return NamedParameterUtils.substituteNamedParameters(parsedSql, null);
    }

    private static void assertParameterNames(ParsedSql parsedSql, String... names) {
        assertIterableEquals(List.of(names), parsedSql.getParameterNames());
    }

    private static void assertArrayValues(Object[] actual, Object... expected) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    private static final class Foo {

        private final Map<String, Object> headers = new HashMap<>();

        private Foo() {
            this.headers.put("id", 1);
        }

        public Map<String, Object> getHeaders() {
            return this.headers;
        }
    }

}
