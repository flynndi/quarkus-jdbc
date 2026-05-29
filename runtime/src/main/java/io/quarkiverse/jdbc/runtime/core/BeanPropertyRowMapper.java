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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.convert.ConversionService;
import io.quarkiverse.jdbc.runtime.convert.DefaultConversionService;
import io.quarkiverse.jdbc.runtime.exception.DataRetrievalFailureException;
import io.quarkiverse.jdbc.runtime.exception.InvalidDataAccessApiUsageException;
import io.quarkiverse.jdbc.runtime.exception.TypeMismatchDataAccessException;
import io.quarkiverse.jdbc.runtime.support.JdbcUtils;
import io.quarkiverse.jdbc.runtime.util.Assert;
import io.quarkiverse.jdbc.runtime.util.BeanUtils;
import io.quarkiverse.jdbc.runtime.util.ClassUtils;
import io.quarkiverse.jdbc.runtime.util.ReflectionUtils;
import io.quarkiverse.jdbc.runtime.util.StringUtils;

/**
 * {@link RowMapper} implementation that converts a row into a new instance
 * of the specified mapped target class via public setters.
 *
 * @author Thomas Risberg
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 2.5
 * @param <T> the result type
 * @see DataClassRowMapper
 * @see SimplePropertyRowMapper
 */
public class BeanPropertyRowMapper<T> implements RowMapper<T> {

    /** Logger available to subclasses. */
    protected final Logger logger = Logger.getLogger(getClass());

    /** The class we are mapping to. */
    private @Nullable Class<T> mappedClass;

    /** Whether we're strictly validating. */
    private boolean checkFullyPopulated = false;

    /** Whether {@code NULL} database values should be ignored for primitive properties. */
    private boolean primitivesDefaultedForNullValue = false;

    /** ConversionService for binding JDBC values to bean properties. */
    private @Nullable ConversionService conversionService = DefaultConversionService.getSharedInstance();

    /** Map of the properties we provide mapping for. */
    private @Nullable Map<String, PropertyDescriptor> mappedProperties;

    /** Set of bean property names we provide mapping for. */
    private @Nullable Set<String> mappedPropertyNames;


    /**
     * Create a new {@code BeanPropertyRowMapper} for bean-style configuration.
     * @see #setMappedClass
     * @see #setCheckFullyPopulated
     */
    public BeanPropertyRowMapper() {
    }

    /**
     * Create a new {@code BeanPropertyRowMapper}, accepting unpopulated properties.
     * @param mappedClass the class that each row should be mapped to
     */
    public BeanPropertyRowMapper(Class<T> mappedClass) {
        initialize(mappedClass);
    }

    /**
     * Create a new {@code BeanPropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     * @param checkFullyPopulated whether we're strictly validating that all bean properties have been mapped
     */
    public BeanPropertyRowMapper(Class<T> mappedClass, boolean checkFullyPopulated) {
        initialize(mappedClass);
        this.checkFullyPopulated = checkFullyPopulated;
    }


    /**
     * Set the class that each row should be mapped to.
     */
    public void setMappedClass(Class<T> mappedClass) {
        if (this.mappedClass == null) {
            initialize(mappedClass);
        }
        else {
            if (this.mappedClass != mappedClass) {
                throw new InvalidDataAccessApiUsageException("The mapped class can not be reassigned to map to " +
                        mappedClass + " since it is already providing mapping for " + this.mappedClass);
            }
        }
    }

    /**
     * Get the class that we are mapping to.
     */
    public final @Nullable Class<T> getMappedClass() {
        return this.mappedClass;
    }

    /**
     * Set whether we're strictly validating that all bean properties have been mapped.
     */
    public void setCheckFullyPopulated(boolean checkFullyPopulated) {
        this.checkFullyPopulated = checkFullyPopulated;
    }

    /**
     * Return whether we're strictly validating that all bean properties have been mapped.
     */
    public boolean isCheckFullyPopulated() {
        return this.checkFullyPopulated;
    }

    /**
     * Set whether a {@code NULL} database column value should be ignored for primitive properties.
     */
    public void setPrimitivesDefaultedForNullValue(boolean primitivesDefaultedForNullValue) {
        this.primitivesDefaultedForNullValue = primitivesDefaultedForNullValue;
    }

    /**
     * Get the value of the {@code primitivesDefaultedForNullValue} flag.
     */
    public boolean isPrimitivesDefaultedForNullValue() {
        return this.primitivesDefaultedForNullValue;
    }

    /**
     * Set a {@link ConversionService} for binding JDBC values to bean properties, or {@code null} for none.
     */
    public void setConversionService(@Nullable ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Return a {@link ConversionService} for binding JDBC values to bean properties, or {@code null} if none.
     */
    public @Nullable ConversionService getConversionService() {
        return this.conversionService;
    }


    /**
     * Initialize the mapping meta-data for the given class.
     * @param mappedClass the mapped class
     */
    protected void initialize(Class<T> mappedClass) {
        this.mappedClass = mappedClass;
        this.mappedProperties = new HashMap<>();
        this.mappedPropertyNames = new HashSet<>();

        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(mappedClass)) {
            if (pd.getWriteMethod() != null) {
                Set<String> mappedNames = mappedNames(pd);
                for (String mappedName : mappedNames) {
                    this.mappedProperties.put(mappedName, pd);
                }
                this.mappedPropertyNames.add(pd.getName());
            }
        }
    }

    /**
     * Remove the specified property from the mapped properties.
     * @param propertyName the property name
     */
    protected void suppressProperty(@Nullable String propertyName) {
        if (this.mappedProperties != null) {
            this.mappedProperties.remove(lowerCaseName(propertyName));
            this.mappedProperties.remove(underscoreName(propertyName));
        }
    }

    /**
     * Determine the mapped names for the given property.
     * @param pd the property descriptor discovered on initialization
     * @return a set of mapped names
     */
    protected Set<String> mappedNames(PropertyDescriptor pd) {
        Set<String> mappedNames = new HashSet<>(4);
        mappedNames.add(lowerCaseName(pd.getName()));
        mappedNames.add(underscoreName(pd.getName()));
        return mappedNames;
    }

    /**
     * Convert the given name to lower case.
     * @param name the original name
     * @return the converted name
     */
    protected String lowerCaseName(@Nullable String name) {
        if (!StringUtils.hasLength(name)) {
            return "";
        }
        return name.toLowerCase(Locale.US);
    }

    /**
     * Convert a name in camelCase to an underscored name in lower case.
     * @param name the original name
     * @return the converted name
     */
    protected String underscoreName(@Nullable String name) {
        return JdbcUtils.convertPropertyNameToUnderscoreName(name);
    }


    /**
     * Extract the values for all columns in the current row.
     */
    @Override
    public T mapRow(ResultSet rs, int rowNumber) throws SQLException {
        T mappedObject = constructMappedInstance(rs);

        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        Set<String> populatedProperties = (isCheckFullyPopulated() ? new HashSet<>() : null);

        for (int index = 1; index <= columnCount; index++) {
            String column = JdbcUtils.lookupColumnName(rsmd, index);
            String property = lowerCaseName(StringUtils.delete(column, " "));
            PropertyDescriptor pd = (this.mappedProperties != null ? this.mappedProperties.get(property) : null);
            if (pd != null) {
                Method writeMethod = pd.getWriteMethod();
                if (writeMethod == null) {
                    continue;
                }
                try {
                    Object value = getColumnValue(rs, index, pd);
                    if (rowNumber == 0 && logger.isDebugEnabled()) {
                        logger.debug("Mapping column '" + column + "' to property '" + pd.getName() +
                                "' of type '" + pd.getPropertyType().getTypeName() + "'");
                    }
                    try {
                        setPropertyValue(mappedObject, writeMethod, pd.getPropertyType(), value);
                    }
                    catch (TypeMismatchDataAccessException ex) {
                        if (!(value == null && isPrimitivesDefaultedForNullValue())) {
                            throw ex;
                        }
                    }
                    if (populatedProperties != null) {
                        populatedProperties.add(pd.getName());
                    }
                }
                catch (IllegalArgumentException ex) {
                    throw new DataRetrievalFailureException(
                            "Unable to map column '" + column + "' to property '" + pd.getName() + "'", ex);
                }
            }
        }

        if (populatedProperties != null && !populatedProperties.equals(this.mappedPropertyNames)) {
            throw new InvalidDataAccessApiUsageException("Given ResultSet does not contain all properties " +
                    "necessary to populate object of " + this.mappedClass + ": " + this.mappedPropertyNames);
        }

        return mappedObject;
    }

    /**
     * Construct an instance of the mapped class for the current row.
     * @param rs the ResultSet to map
     * @return a corresponding instance of the mapped class
     * @throws SQLException if an SQLException is encountered
     */
    protected T constructMappedInstance(ResultSet rs) throws SQLException {
        Assert.state(this.mappedClass != null, "Mapped class was not specified");
        return BeanUtils.instantiateClass(this.mappedClass);
    }

    private void setPropertyValue(T mappedObject, Method writeMethod, Class<?> propertyType, @Nullable Object value) {
        Object valueToUse = convertValueIfNecessary(value, propertyType);
        ReflectionUtils.makeAccessible(writeMethod);
        try {
            ReflectionUtils.invokeMethod(writeMethod, mappedObject, valueToUse);
        }
        catch (IllegalArgumentException ex) {
            throw new TypeMismatchDataAccessException("Failed to convert property value of type " +
                    (value != null ? value.getClass().getName() : "null") + " to required type " +
                    propertyType.getName(), ex);
        }
    }

    /**
     * Convert the given value to the given target type if necessary.
     * @param value the source value
     * @param targetType the target type
     * @return the converted value
     */
    protected @Nullable Object convertValueIfNecessary(@Nullable Object value, Class<?> targetType) {
        Class<?> targetClass = ClassUtils.resolvePrimitiveIfNecessary(targetType);
        if (value == null || targetClass.isInstance(value)) {
            return value;
        }
        ConversionService cs = getConversionService();
        if (cs != null && cs.canConvert(value.getClass(), targetClass)) {
            return cs.convert(value, targetClass);
        }
        throw new TypeMismatchDataAccessException("Value [" + value + "] of type [" + value.getClass().getName() +
                "] cannot be converted to required type [" + targetClass.getName() + "]");
    }

    /**
     * Retrieve a JDBC object value for the specified column.
     * @param rs is the ResultSet holding the data
     * @param index is the column index
     * @param pd the bean property that each result object is expected to match
     * @return the Object value
     * @throws SQLException in case of extraction failure
     */
    protected @Nullable Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) throws SQLException {
        return JdbcUtils.getResultSetValue(rs, index, pd.getPropertyType());
    }

    /**
     * Retrieve a JDBC object value for the specified column.
     * @param rs is the ResultSet holding the data
     * @param index is the column index
     * @param paramType the target parameter type
     * @return the Object value
     * @throws SQLException in case of extraction failure
     */
    protected @Nullable Object getColumnValue(ResultSet rs, int index, Class<?> paramType) throws SQLException {
        return JdbcUtils.getResultSetValue(rs, index, paramType);
    }


    /**
     * Static factory method to create a new {@code BeanPropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     */
    public static <T> BeanPropertyRowMapper<T> newInstance(Class<T> mappedClass) {
        return new BeanPropertyRowMapper<>(mappedClass);
    }

    /**
     * Static factory method to create a new {@code BeanPropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     * @param conversionService the {@link ConversionService} for binding JDBC values to bean properties, or {@code null} for none
     */
    public static <T> BeanPropertyRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService) {

        BeanPropertyRowMapper<T> rowMapper = newInstance(mappedClass);
        rowMapper.setConversionService(conversionService);
        return rowMapper;
    }
}
