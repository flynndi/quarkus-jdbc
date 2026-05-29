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

package io.quarkiverse.jdbc.runtime.datasource.init;

import static io.quarkiverse.jdbc.runtime.datasource.init.ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER;
import static io.quarkiverse.jdbc.runtime.datasource.init.ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER;
import static io.quarkiverse.jdbc.runtime.datasource.init.ScriptUtils.DEFAULT_COMMENT_PREFIX;
import static io.quarkiverse.jdbc.runtime.datasource.init.ScriptUtils.DEFAULT_COMMENT_PREFIXES;
import static io.quarkiverse.jdbc.runtime.datasource.init.ScriptUtils.DEFAULT_STATEMENT_SEPARATOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ScriptUtilsTests {

    @Test
    void splitSqlScriptDelimitedWithSemicolon() {
        String rawStatement1 = "insert into customer (id, name)\nvalues (1, 'Rod ; Johnson'), (2, 'Adrian \n Collier')";
        String cleanedStatement1 = "insert into customer (id, name) values (1, 'Rod ; Johnson'), (2, 'Adrian \n Collier')";
        String rawStatement2 = "insert into orders(id, order_date, customer_id)\nvalues (1, '2008-01-02', 2)";
        String cleanedStatement2 = "insert into orders(id, order_date, customer_id) values (1, '2008-01-02', 2)";
        String rawStatement3 = "insert into orders(id, order_date, customer_id) values (1, '2008-01-02', 2)";
        String delimiter = ";";
        String script = String.join(delimiter, rawStatement1, rawStatement2, rawStatement3);

        List<String> statements = new ArrayList<>();
        splitSqlScript(script, delimiter, statements);

        assertIterableEquals(List.of(cleanedStatement1, cleanedStatement2, rawStatement3), statements);
    }

    @Test
    void splitSqlScriptDelimitedWithNewLine() {
        String statement1 = "insert into customer (id, name) values (1, 'Rod ; Johnson'), (2, 'Adrian \n Collier')";
        String statement2 = "insert into orders(id, order_date, customer_id) values (1, '2008-01-02', 2)";
        String statement3 = "insert into orders(id, order_date, customer_id) values (1, '2008-01-02', 2)";
        String delimiter = "\n";
        String script = String.join(delimiter, statement1, statement2, statement3);

        List<String> statements = new ArrayList<>();
        splitSqlScript(script, delimiter, statements);

        assertIterableEquals(List.of(statement1, statement2, statement3), statements);
    }

    @Test
    void splitSqlScriptDelimitedWithNewLineButDefaultDelimiterSpecified() {
        String statement1 = "do something";
        String statement2 = "do something else";
        String script = String.join("\n", statement1, statement2);

        List<String> statements = new ArrayList<>();
        splitSqlScript(script, DEFAULT_STATEMENT_SEPARATOR, statements);

        assertIterableEquals(List.of(script.replace('\n', ' ')), statements);
    }

    @Test
    void splitScriptWithSingleQuotesNestedInsideDoubleQuotes() {
        String statement1 = "select '1' as \"Dogbert's owner's\" from dual";
        String statement2 = "select '2' as \"Dilbert's\" from dual";
        String script = String.join(";", statement1, statement2);

        List<String> statements = new ArrayList<>();
        splitSqlScript(script, ";", statements);

        assertIterableEquals(List.of(statement1, statement2), statements);
    }

    @Test
    void splitScriptContainingComments() {
        String script = """
                -- leading comment
                insert into customer (id, name)
                values (1, 'Rod; Johnson'), (2, 'Adrian Collier');
                /* block ; comment */
                insert into orders(id, order_date, customer_id)
                values (1, '2008-01-02', 2);
                """;

        List<String> statements = new ArrayList<>();
        ScriptUtils.splitSqlScript(null, script, ";", DEFAULT_COMMENT_PREFIXES,
                DEFAULT_BLOCK_COMMENT_START_DELIMITER, DEFAULT_BLOCK_COMMENT_END_DELIMITER, statements);

        assertIterableEquals(List.of(
                "insert into customer (id, name) values (1, 'Rod; Johnson'), (2, 'Adrian Collier')",
                "insert into orders(id, order_date, customer_id) values (1, '2008-01-02', 2)"), statements);
    }

    @Test
    void containsStatementSeparatorIgnoresQuotesAndComments() {
        assertFalse(containsSqlScriptDelimiters("select 1\n select ';'", ";"));
        assertFalse(containsSqlScriptDelimiters("select 1\n select \";\"", ";"));
        assertTrue(containsSqlScriptDelimiters("select 1; select 2", ";"));
        assertTrue(containsSqlScriptDelimiters("-- a;b;c\n insert into colors(color_num) values(42);", ";"));
        assertTrue(containsSqlScriptDelimiters("/* a;b;c */\n insert into colors(color_num) values(42);", ";"));
        assertFalse(containsSqlScriptDelimiters("-- a;b;c\n insert into colors(color_num) values(42)", ";"));
        assertFalse(containsSqlScriptDelimiters("/* a;b;c */\n insert into colors(color_num) values(42)", ";"));
    }

    @Test
    void containsStatementSeparatorRejectsUnclosedBlockComment() {
        assertThrows(ScriptParseException.class,
                () -> containsSqlScriptDelimiters("select 1 /* missing end", ";"));
    }

    private static void splitSqlScript(String script, String separator, List<String> statements) {
        ScriptUtils.splitSqlScript(null, script, separator, new String[] { DEFAULT_COMMENT_PREFIX },
                DEFAULT_BLOCK_COMMENT_START_DELIMITER, DEFAULT_BLOCK_COMMENT_END_DELIMITER, statements);
    }

    private static boolean containsSqlScriptDelimiters(String script, String delimiter) {
        return ScriptUtils.containsStatementSeparator(null, script, delimiter, DEFAULT_COMMENT_PREFIXES,
                DEFAULT_BLOCK_COMMENT_START_DELIMITER, DEFAULT_BLOCK_COMMENT_END_DELIMITER);
    }

}
