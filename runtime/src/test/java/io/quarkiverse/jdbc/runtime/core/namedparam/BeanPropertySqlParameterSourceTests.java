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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class BeanPropertySqlParameterSourceTests {

    @Test
    void withNullBeanPassedToCtor() {
        assertThrows(IllegalArgumentException.class, () -> new BeanPropertySqlParameterSource(null));
    }

    @Test
    void getValueWhereTheUnderlyingBeanHasNoSuchProperty() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestBean());

        assertThrows(IllegalArgumentException.class, () -> source.getValue("thisPropertyDoesNotExist"));
    }

    @Test
    void successfulPropertyAccess() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestBean("tb", 99));
        List<String> readablePropertyNames = Arrays.asList(source.getReadablePropertyNames());

        assertTrue(readablePropertyNames.contains("name"));
        assertTrue(readablePropertyNames.contains("age"));
        assertEquals("tb", source.getValue("name"));
        assertEquals(99, source.getValue("age"));
        assertEquals(Types.VARCHAR, source.getSqlType("name"));
        assertEquals(Types.INTEGER, source.getSqlType("age"));
    }

    @Test
    void successfulRecordPropertyAccess() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestRecord("record", 21));

        assertIterableEquals(List.of("age", "name"), Arrays.asList(source.getReadablePropertyNames()).stream().sorted().toList());
        assertEquals("record", source.getValue("name"));
        assertEquals(21, source.getValue("age"));
        assertEquals(Types.VARCHAR, source.getSqlType("name"));
        assertEquals(Types.INTEGER, source.getSqlType("age"));
    }

    @Test
    void successfulPropertyAccessWithOverriddenSqlType() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestBean("tb", 99));
        source.registerSqlType("age", Types.NUMERIC);

        assertEquals("tb", source.getValue("name"));
        assertEquals(99, source.getValue("age"));
        assertEquals(Types.VARCHAR, source.getSqlType("name"));
        assertEquals(Types.NUMERIC, source.getSqlType("age"));
    }

    @Test
    void hasValueWhereTheUnderlyingBeanHasNoSuchProperty() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestBean());

        assertFalse(source.hasValue("thisPropertyDoesNotExist"));
    }

    @Test
    void getValueWhereTheUnderlyingBeanPropertyIsNotReadable() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new NoReadableProperties());

        assertThrows(IllegalArgumentException.class, () -> source.getValue("noOp"));
    }

    @Test
    void hasValueWhereTheUnderlyingBeanPropertyIsNotReadable() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new NoReadableProperties());

        assertFalse(source.hasValue("noOp"));
    }

    @Test
    void toStringShowsParameterDetails() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestBean("tb", 99));
        String text = source.toString();

        assertTrue(text.startsWith("BeanPropertySqlParameterSource {"), text);
        assertTrue(text.contains("name=tb (type:VARCHAR)"), text);
        assertTrue(text.contains("age=99 (type:INTEGER)"), text);
        assertTrue(text.endsWith("}"), text);
    }

    @Test
    void toStringShowsCustomSqlType() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestBean("tb", 99));
        source.registerSqlType("name", Integer.MAX_VALUE);
        String text = source.toString();

        assertTrue(text.startsWith("BeanPropertySqlParameterSource {"), text);
        assertTrue(text.contains("name=tb (type:" + Integer.MAX_VALUE + ")"), text);
        assertTrue(text.contains("age=99 (type:INTEGER)"), text);
        assertTrue(text.endsWith("}"), text);
    }

    @Test
    void toStringDoesNotShowTypeUnknown() {
        BeanPropertySqlParameterSource source = new BeanPropertySqlParameterSource(new TestBean("tb", 99));
        String text = source.toString();

        assertTrue(text.startsWith("BeanPropertySqlParameterSource {"), text);
        assertTrue(text.contains("beanFactory=null"), text);
        assertFalse(text.contains("beanFactory=null (type:"), text);
        assertTrue(text.endsWith("}"), text);
    }

    @SuppressWarnings("unused")
    private static final class NoReadableProperties {

        public void setNoOp(String noOp) {
        }
    }

    private record TestRecord(String name, int age) {
    }

    private static final class TestBean {

        private final String name;

        private final int age;

        private Object beanFactory;

        private TestBean() {
            this(null, 0);
        }

        private TestBean(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return this.name;
        }

        public int getAge() {
            return this.age;
        }

        public Object getBeanFactory() {
            return this.beanFactory;
        }
    }

}
