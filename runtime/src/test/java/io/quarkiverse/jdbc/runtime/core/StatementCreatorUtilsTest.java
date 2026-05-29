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

package io.quarkiverse.jdbc.runtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatementCreatorUtilsTest {

    private static final String IGNORE_GET_PARAMETER_TYPE_PROPERTY_NAME =
            "quarkus.jdbc.statement.ignore-get-parameter-type";

    private String originalIgnoreGetParameterType;

    @BeforeEach
    void captureSystemProperty() {
        this.originalIgnoreGetParameterType = System.getProperty(IGNORE_GET_PARAMETER_TYPE_PROPERTY_NAME);
    }

    @AfterEach
    void resetConfiguration() {
        if (this.originalIgnoreGetParameterType != null) {
            System.setProperty(IGNORE_GET_PARAMETER_TYPE_PROPERTY_NAME, this.originalIgnoreGetParameterType);
        }
        else {
            System.clearProperty(IGNORE_GET_PARAMETER_TYPE_PROPERTY_NAME);
        }
    }

    @Test
    void readsQuarkusConfiguredFalseValue() {
        System.setProperty(IGNORE_GET_PARAMETER_TYPE_PROPERTY_NAME, "false");

        assertEquals(Boolean.FALSE, StatementCreatorUtils.shouldIgnoreGetParameterType());
    }

    @Test
    void readsQuarkusConfiguredTrueValue() {
        System.setProperty(IGNORE_GET_PARAMETER_TYPE_PROPERTY_NAME, "true");

        assertEquals(Boolean.TRUE, StatementCreatorUtils.shouldIgnoreGetParameterType());
    }

    @Test
    void preservesAutomaticModeWhenQuarkusValueIsAbsent() {
        System.clearProperty(IGNORE_GET_PARAMETER_TYPE_PROPERTY_NAME);

        assertNull(StatementCreatorUtils.shouldIgnoreGetParameterType());
    }
}
