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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Types;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jdbc.runtime.core.SqlParameterValue;
import io.quarkiverse.jdbc.runtime.support.JdbcUtils;

class MapSqlParameterSourceTests {

    @Test
    void nullParameterValuesPassedToCtorIsOk() {
        assertDoesNotThrow(() -> new MapSqlParameterSource(null));
    }

    @Test
    void getValueChokesIfParameterIsNotPresent() {
        MapSqlParameterSource source = new MapSqlParameterSource();

        assertThrows(IllegalArgumentException.class, () -> source.getValue("pechorin was right!"));
    }

    @Test
    void sqlParameterValueRegistersSqlType() {
        MapSqlParameterSource source = new MapSqlParameterSource("FOO", new SqlParameterValue(Types.NUMERIC, "Foo"));
        MapSqlParameterSource copied = new MapSqlParameterSource();
        copied.addValues(source.getValues());

        assertEquals(Types.NUMERIC, source.getSqlType("FOO"));
        assertEquals(Types.NUMERIC, copied.getSqlType("FOO"));
    }

    @Test
    void toStringShowsParameterDetails() {
        MapSqlParameterSource source = new MapSqlParameterSource("FOO", new SqlParameterValue(Types.NUMERIC, "Foo"));

        assertEquals("MapSqlParameterSource {FOO=Foo (type:NUMERIC)}", source.toString());
    }

    @Test
    void toStringShowsCustomSqlType() {
        MapSqlParameterSource source = new MapSqlParameterSource("FOO", new SqlParameterValue(Integer.MAX_VALUE, "Foo"));

        assertEquals("MapSqlParameterSource {FOO=Foo (type:" + Integer.MAX_VALUE + ")}", source.toString());
    }

    @Test
    void toStringDoesNotShowTypeUnknown() {
        MapSqlParameterSource source = new MapSqlParameterSource("FOO", new SqlParameterValue(JdbcUtils.TYPE_UNKNOWN, "Foo"));

        assertEquals("MapSqlParameterSource {FOO=Foo}", source.toString());
    }

}
