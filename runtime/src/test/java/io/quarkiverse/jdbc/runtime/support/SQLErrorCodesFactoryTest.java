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

package io.quarkiverse.jdbc.runtime.support;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.jdbc.runtime.exception.DataIntegrityViolationException;

class SQLErrorCodesFactoryTest {

    @Test
    void loadsDefaultSqlErrorCodesAndMatchesDatabaseProductNames() {
        SQLErrorCodesFactory factory = new SQLErrorCodesFactory();

        SQLErrorCodes db2 = factory.getErrorCodes("DB2/LINUXX8664");
        SQLErrorCodes mysql = factory.getErrorCodes("MariaDB");

        assertArrayEquals(new String[] { "-803" }, db2.getDuplicateKeyCodes());
        assertArrayEquals(new String[] { "1062" }, mysql.getDuplicateKeyCodes());
    }

    @Test
    void loadsDefaultSqlErrorCodesWhenContextClassLoaderCannotFindResource() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        URLClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null);

        try {
            currentThread.setContextClassLoader(emptyClassLoader);
            SQLErrorCodesFactory factory = new SQLErrorCodesFactory();

            SQLErrorCodes db2 = factory.getErrorCodes("DB2/LINUXX8664");

            assertArrayEquals(new String[] { "-803" }, db2.getDuplicateKeyCodes());
        }
        finally {
            currentThread.setContextClassLoader(originalClassLoader);
            emptyClassLoader.close();
        }
    }

    @Test
    void detectsRootClasspathOverrideFromContextClassLoaderAtCallTime(@TempDir Path tempDir) throws Exception {
        assertFalse(SQLErrorCodeSQLExceptionTranslator.hasUserProvidedErrorCodesFile());
        Files.writeString(tempDir.resolve(SQLErrorCodesFactory.SQL_ERROR_CODE_OVERRIDE_PATH), "");

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        URLClassLoader overrideClassLoader = new URLClassLoader(new URL[] { tempDir.toUri().toURL() },
                SQLErrorCodesFactory.class.getClassLoader());

        try {
            currentThread.setContextClassLoader(overrideClassLoader);

            assertTrue(SQLErrorCodeSQLExceptionTranslator.hasUserProvidedErrorCodesFile());
        }
        finally {
            currentThread.setContextClassLoader(originalClassLoader);
            overrideClassLoader.close();
        }
    }

    @Test
    void loadsRootClasspathOverrideAndCustomTranslations(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve(SQLErrorCodesFactory.SQL_ERROR_CODE_OVERRIDE_PATH), """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="OverrideDB" name="OverrideAlias"
                            class="io.quarkiverse.jdbc.runtime.support.SQLErrorCodes">
                        <property name="databaseProductName" value="Override Product*"/>
                        <property name="badSqlGrammarCodes" value="111, 222"/>
                        <property name="customTranslations">
                            <list>
                                <bean class="io.quarkiverse.jdbc.runtime.support.CustomSQLErrorCodesTranslation">
                                    <property name="errorCodes" value="333"/>
                                    <property name="exceptionClass"
                                            value="io.quarkiverse.jdbc.runtime.exception.DataIntegrityViolationException"/>
                                </bean>
                            </list>
                        </property>
                    </bean>
                </beans>
                """);

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        URLClassLoader overrideClassLoader = new URLClassLoader(new URL[] { tempDir.toUri().toURL() },
                SQLErrorCodesFactory.class.getClassLoader());

        try {
            currentThread.setContextClassLoader(overrideClassLoader);
            SQLErrorCodesFactory factory = new SQLErrorCodesFactory();

            SQLErrorCodes byProductName = factory.getErrorCodes("Override Product 1");
            SQLErrorCodes byAlias = factory.getErrorCodes("OverrideAlias");
            CustomSQLErrorCodesTranslation[] translations = byProductName.getCustomTranslations();

            assertArrayEquals(new String[] { "111", "222" }, byProductName.getBadSqlGrammarCodes());
            assertArrayEquals(new String[] { "111", "222" }, byAlias.getBadSqlGrammarCodes());
            assertNotNull(translations);
            assertEquals(1, translations.length);
            assertArrayEquals(new String[] { "333" }, translations[0].getErrorCodes());
            assertEquals(DataIntegrityViolationException.class, translations[0].getExceptionClass());
        }
        finally {
            currentThread.setContextClassLoader(originalClassLoader);
            overrideClassLoader.close();
        }
    }
}
