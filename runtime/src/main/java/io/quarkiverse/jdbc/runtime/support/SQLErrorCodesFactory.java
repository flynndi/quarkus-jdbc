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

import java.io.InputStream;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.quarkiverse.jdbc.runtime.util.Assert;
import io.quarkiverse.jdbc.runtime.util.StringUtils;

/**
 * Factory for creating {@link SQLErrorCodes} based on database metadata.
 *
 * <p>This Quarkus migration keeps the Spring API shape and DataSource cache,
 * while loading Spring-style {@code sql-error-codes.xml} resources without
 * depending on Spring BeanFactory XML infrastructure.
 */
public class SQLErrorCodesFactory {

    public static final String SQL_ERROR_CODE_OVERRIDE_PATH = "sql-error-codes.xml";

    public static final String SQL_ERROR_CODE_DEFAULT_PATH = "io/quarkiverse/jdbc/runtime/support/sql-error-codes.xml";

    private static final Logger logger = Logger.getLogger(SQLErrorCodesFactory.class);

    private static @Nullable SQLErrorCodesFactory instance;

    public static SQLErrorCodesFactory getInstance() {
        if (instance == null) {
            instance = new SQLErrorCodesFactory();
        }
        return instance;
    }

    private final Map<String, SQLErrorCodes> errorCodesMap = new ConcurrentHashMap<>();

    private final Map<DataSource, SQLErrorCodes> dataSourceCache = new ConcurrentHashMap<>();

    protected SQLErrorCodesFactory() {
        loadErrorCodes(SQL_ERROR_CODE_DEFAULT_PATH, false);
        loadErrorCodes(SQL_ERROR_CODE_OVERRIDE_PATH, true);
        if (logger.isTraceEnabled()) {
            logger.trace("SQLErrorCodes loaded: " + this.errorCodesMap.keySet());
        }
    }

    public SQLErrorCodes getErrorCodes(String databaseName) {
        Assert.notNull(databaseName, "Database product name must not be null");

        SQLErrorCodes sec = this.errorCodesMap.get(databaseName);
        if (sec == null) {
            for (SQLErrorCodes candidate : this.errorCodesMap.values()) {
                if (simpleMatch(candidate.getDatabaseProductNames(), databaseName)) {
                    sec = candidate;
                    break;
                }
            }
        }
        if (sec != null) {
            checkCustomTranslatorRegistry(databaseName, sec);
            if (logger.isDebugEnabled()) {
                logger.debug("SQL error codes for '" + databaseName + "' found");
            }
            return sec;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("SQL error codes for '" + databaseName + "' not found");
        }
        return new SQLErrorCodes();
    }

    public SQLErrorCodes getErrorCodes(DataSource dataSource) {
        SQLErrorCodes sec = resolveErrorCodes(dataSource);
        return (sec != null ? sec : new SQLErrorCodes());
    }

    public @Nullable SQLErrorCodes resolveErrorCodes(DataSource dataSource) {
        Assert.notNull(dataSource, "DataSource must not be null");
        SQLErrorCodes sec = this.dataSourceCache.get(dataSource);
        if (sec != null) {
            return sec;
        }
        try {
            String name = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
            if (StringUtils.hasLength(name)) {
                return registerDatabase(dataSource, name);
            }
        }
        catch (MetaDataAccessException ex) {
            logger.warn("Error while extracting database name", ex);
        }
        return null;
    }

    public SQLErrorCodes registerDatabase(DataSource dataSource, String databaseName) {
        SQLErrorCodes sec = getErrorCodes(databaseName);
        if (logger.isDebugEnabled()) {
            logger.debug("Caching SQL error codes for DataSource [" + identify(dataSource) +
                    "]: database product name is '" + databaseName + "'");
        }
        this.dataSourceCache.put(dataSource, sec);
        return sec;
    }

    public @Nullable SQLErrorCodes unregisterDatabase(DataSource dataSource) {
        return this.dataSourceCache.remove(dataSource);
    }

    public void registerErrorCodes(String databaseName, SQLErrorCodes errorCodes) {
        Assert.notNull(databaseName, "Database product name must not be null");
        Assert.notNull(errorCodes, "SQLErrorCodes must not be null");
        this.errorCodesMap.put(databaseName, errorCodes);
    }

    private void loadErrorCodes(String path, boolean optional) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream resourceInputStream = classLoader != null ? classLoader.getResourceAsStream(path) : null;
        if (resourceInputStream == null) {
            classLoader = SQLErrorCodesFactory.class.getClassLoader();
            resourceInputStream = classLoader != null ? classLoader.getResourceAsStream(path) : ClassLoader.getSystemResourceAsStream(path);
        }
        try (InputStream inputStream = resourceInputStream) {
            if (inputStream == null) {
                if (!optional) {
                    logger.info("Default sql-error-codes.xml not found at " + path);
                }
                return;
            }
            parseErrorCodes(inputStream);
            if (optional && logger.isDebugEnabled()) {
                logger.debug("Found custom sql-error-codes.xml file at the root of the classpath");
            }
        }
        catch (Exception ex) {
            logger.warn("Error loading SQL error codes from " + path, ex);
        }
    }

    private void parseErrorCodes(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        }
        catch (IllegalArgumentException ignored) {
        }

        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        Document document = documentBuilder.parse(inputStream);
        NodeList beans = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < beans.getLength(); i++) {
            Node node = beans.item(i);
            if (node instanceof Element bean && "bean".equals(localName(bean))) {
                if (isSqlErrorCodesBean(bean)) {
                    parseBean(bean);
                }
            }
        }
    }

    private void parseBean(Element bean) {
        String id = bean.getAttribute("id");
        if (!StringUtils.hasText(id)) {
            return;
        }

        SQLErrorCodes errorCodes = new SQLErrorCodes();
        String[] aliases = aliases(bean.getAttribute("name"));
        for (Element property : childElements(bean, "property")) {
            String name = property.getAttribute("name");
            applyProperty(errorCodes, name, property);
        }

        this.errorCodesMap.put(id, errorCodes);
        for (String alias : aliases) {
            this.errorCodesMap.put(alias, errorCodes);
        }
    }

    private void applyProperty(SQLErrorCodes errorCodes, String name, Element property) {
        switch (name) {
            case "databaseProductName" -> errorCodes.setDatabaseProductName(singleValue(property));
            case "databaseProductNames" -> errorCodes.setDatabaseProductNames(listValues(property));
            case "useSqlStateForTranslation" ->
                    errorCodes.setUseSqlStateForTranslation(Boolean.parseBoolean(singleValue(property)));
            case "badSqlGrammarCodes" -> errorCodes.setBadSqlGrammarCodes(commaDelimitedValues(property));
            case "invalidResultSetAccessCodes" -> errorCodes.setInvalidResultSetAccessCodes(commaDelimitedValues(property));
            case "duplicateKeyCodes" -> errorCodes.setDuplicateKeyCodes(commaDelimitedValues(property));
            case "dataIntegrityViolationCodes" -> errorCodes.setDataIntegrityViolationCodes(commaDelimitedValues(property));
            case "permissionDeniedCodes" -> errorCodes.setPermissionDeniedCodes(commaDelimitedValues(property));
            case "dataAccessResourceFailureCodes" -> errorCodes.setDataAccessResourceFailureCodes(commaDelimitedValues(property));
            case "transientDataAccessResourceCodes" -> errorCodes.setTransientDataAccessResourceCodes(commaDelimitedValues(property));
            case "cannotAcquireLockCodes" -> errorCodes.setCannotAcquireLockCodes(commaDelimitedValues(property));
            case "deadlockLoserCodes" -> errorCodes.setDeadlockLoserCodes(commaDelimitedValues(property));
            case "cannotSerializeTransactionCodes" -> errorCodes.setCannotSerializeTransactionCodes(commaDelimitedValues(property));
            case "customTranslations" -> errorCodes.setCustomTranslations(customTranslations(property));
            case "customSqlExceptionTranslatorClass" -> setCustomTranslatorClass(errorCodes, singleValue(property));
            default -> {
                if (logger.isTraceEnabled()) {
                    logger.trace("Ignoring unsupported SQLErrorCodes property '" + name + "'");
                }
            }
        }
    }

    private CustomSQLErrorCodesTranslation[] customTranslations(Element property) {
        List<CustomSQLErrorCodesTranslation> translations = new ArrayList<>();
        for (Element bean : childElements(property, "bean")) {
            CustomSQLErrorCodesTranslation translation = new CustomSQLErrorCodesTranslation();
            for (Element translationProperty : childElements(bean, "property")) {
                String name = translationProperty.getAttribute("name");
                if ("errorCodes".equals(name)) {
                    translation.setErrorCodes(commaDelimitedValues(translationProperty));
                }
                else if ("exceptionClass".equals(name)) {
                    setCustomExceptionClass(translation, singleValue(translationProperty));
                }
            }
            translations.add(translation);
        }
        return translations.toArray(CustomSQLErrorCodesTranslation[]::new);
    }

    private void setCustomExceptionClass(CustomSQLErrorCodesTranslation translation, String className) {
        if (!StringUtils.hasText(className)) {
            return;
        }
        try {
            translation.setExceptionClass(resolveClass(className));
        }
        catch (Throwable ex) {
            logger.warn("Could not load custom SQL exception class " + className, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void setCustomTranslatorClass(SQLErrorCodes errorCodes, String className) {
        if (!StringUtils.hasText(className)) {
            return;
        }
        try {
            Class<?> customTranslatorClass = resolveClass(className);
            if (!SQLExceptionTranslator.class.isAssignableFrom(customTranslatorClass)) {
                throw new IllegalArgumentException("Class does not implement SQLExceptionTranslator: " + className);
            }
            errorCodes.setCustomSqlExceptionTranslatorClass(
                    (Class<? extends SQLExceptionTranslator>) customTranslatorClass);
        }
        catch (Throwable ex) {
            logger.warn("Could not load custom SQL exception translator class " + className, ex);
        }
    }

    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        }
        catch (Exception ignored) {
        }
    }

    private static String singleValue(Element property) {
        String valueAttribute = property.getAttribute("value");
        if (StringUtils.hasText(valueAttribute)) {
            return valueAttribute.trim();
        }
        String[] values = listValues(property);
        return (values.length > 0 ? values[0] : "");
    }

    private static String[] commaDelimitedValues(Element property) {
        return Arrays.stream(singleValue(property).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    private static String[] listValues(Element property) {
        return childElements(property, "value").stream()
                .map(Element::getTextContent)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    private static String[] aliases(String nameAttribute) {
        if (!StringUtils.hasText(nameAttribute)) {
            return new String[0];
        }
        return Arrays.stream(nameAttribute.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    private static List<Element> childElements(Element parent, String name) {
        ArrayList<Element> elements = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element element) {
                if (name.equals(localName(element))) {
                    elements.add(element);
                }
                else if (isCollectionElement(element)) {
                    elements.addAll(childElements(element, name));
                }
            }
        }
        return elements;
    }

    private static boolean isSqlErrorCodesBean(Element bean) {
        String className = bean.getAttribute("class");
        return (!StringUtils.hasText(className) ||
                SQLErrorCodes.class.getName().equals(className) ||
                "org.springframework.jdbc.support.SQLErrorCodes".equals(className));
    }

    private static boolean isCollectionElement(Element element) {
        String localName = localName(element);
        return ("list".equals(localName) || "array".equals(localName) || "set".equals(localName));
    }

    private static Class<?> resolveClass(String className) throws ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = SQLErrorCodesFactory.class.getClassLoader();
        }
        return Class.forName(className, true, classLoader);
    }

    private static String localName(Element element) {
        String localName = element.getLocalName();
        return (localName != null ? localName : element.getTagName());
    }

    private static boolean simpleMatch(String @Nullable [] patterns, String str) {
        if (patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (simpleMatch(pattern, str)) {
                return true;
            }
        }
        return false;
    }

    private static boolean simpleMatch(@Nullable String pattern, String str) {
        if (pattern == null) {
            return false;
        }
        int firstIndex = pattern.indexOf('*');
        if (firstIndex == -1) {
            return pattern.equals(str);
        }
        if (firstIndex == 0) {
            if (pattern.length() == 1) {
                return true;
            }
            int nextIndex = pattern.indexOf('*', 1);
            if (nextIndex == -1) {
                return str.endsWith(pattern.substring(1));
            }
            String part = pattern.substring(1, nextIndex);
            if (part.isEmpty()) {
                return simpleMatch(pattern.substring(nextIndex), str);
            }
            int partIndex = str.indexOf(part);
            while (partIndex != -1) {
                if (simpleMatch(pattern.substring(nextIndex), str.substring(partIndex + part.length()))) {
                    return true;
                }
                partIndex = str.indexOf(part, partIndex + 1);
            }
            return false;
        }
        return str.length() >= firstIndex && pattern.startsWith(str.substring(0, firstIndex)) &&
                simpleMatch(pattern.substring(firstIndex), str.substring(firstIndex));
    }

    private String identify(DataSource dataSource) {
        return dataSource.getClass().getName() + '@' + Integer.toHexString(dataSource.hashCode());
    }

    private void checkCustomTranslatorRegistry(String databaseName, SQLErrorCodes errorCodes) {
        SQLExceptionTranslator customTranslator =
                CustomSQLExceptionTranslatorRegistry.getInstance().findTranslatorForDatabase(databaseName);
        if (customTranslator != null) {
            if (errorCodes.getCustomSqlExceptionTranslator() != null && logger.isDebugEnabled()) {
                logger.debug("Overriding already defined custom translator '" +
                        errorCodes.getCustomSqlExceptionTranslator().getClass().getSimpleName() +
                        " with '" + customTranslator.getClass().getSimpleName() +
                        "' found in the CustomSQLExceptionTranslatorRegistry for database '" + databaseName + "'");
            }
            else if (logger.isTraceEnabled()) {
                logger.trace("Using custom translator '" + customTranslator.getClass().getSimpleName() +
                        "' found in the CustomSQLExceptionTranslatorRegistry for database '" + databaseName + "'");
            }
            errorCodes.setCustomSqlExceptionTranslator(customTranslator);
        }
    }
}
