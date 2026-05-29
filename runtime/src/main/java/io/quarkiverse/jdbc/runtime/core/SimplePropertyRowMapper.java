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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.convert.ConversionService;
import io.quarkiverse.jdbc.runtime.convert.DefaultConversionService;
import io.quarkiverse.jdbc.runtime.support.JdbcUtils;
import io.quarkiverse.jdbc.runtime.util.Assert;
import io.quarkiverse.jdbc.runtime.util.BeanUtils;
import io.quarkiverse.jdbc.runtime.util.ClassUtils;
import io.quarkiverse.jdbc.runtime.util.ReflectionUtils;

/**
 * {@link RowMapper} implementation that converts a row into a new instance
 * of the specified mapped target class.
 *
 * <p>This Quarkus runtime variant keeps Spring JDBC's constructor/setter/field
 * mapping order but uses local reflection utilities instead of Spring Beans.
 *
 * @author Juergen Hoeller
 * @since 6.1
 * @param <T> the result type
 * @see io.quarkiverse.jdbc.runtime.core.simple.JdbcClient.StatementSpec#query(Class)
 * @see io.quarkiverse.jdbc.runtime.core.namedparam.SimplePropertySqlParameterSource
 */
public class SimplePropertyRowMapper<T> implements RowMapper<T> {

    private static final Object NO_DESCRIPTOR = new Object();

    private final Class<T> mappedClass;

    private final ConversionService conversionService;

    private final Constructor<T> mappedConstructor;

    private final String[] constructorParameterNames;

    private final Class<?>[] constructorParameterTypes;

    private final Map<String, Object> propertyDescriptors = new ConcurrentHashMap<>();


    /**
     * Create a new {@code SimplePropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     */
    public SimplePropertyRowMapper(Class<T> mappedClass) {
        this(mappedClass, DefaultConversionService.getSharedInstance());
    }

    /**
     * Create a new {@code SimplePropertyRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     * @param conversionService a {@link ConversionService} for binding JDBC values to bean properties
     */
    public SimplePropertyRowMapper(Class<T> mappedClass, ConversionService conversionService) {
        Assert.notNull(mappedClass, "Mapped Class must not be null");
        Assert.notNull(conversionService, "ConversionService must not be null");
        this.mappedClass = mappedClass;
        this.conversionService = conversionService;
        this.mappedConstructor = BeanUtils.getResolvableConstructor(mappedClass);
        this.constructorParameterNames = (this.mappedConstructor.getParameterCount() > 0 ?
                BeanUtils.getParameterNames(this.mappedConstructor) : new String[0]);
        this.constructorParameterTypes = this.mappedConstructor.getParameterTypes();
    }


    @Override
    public T mapRow(ResultSet rs, int rowNumber) throws SQLException {
        @Nullable Object[] args = new Object[this.constructorParameterNames.length];
        Set<Integer> usedIndex = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String name = this.constructorParameterNames[i];
            int index;
            try {
                index = rs.findColumn(name);
            }
            catch (SQLException ex) {
                index = rs.findColumn(JdbcUtils.convertPropertyNameToUnderscoreName(name));
            }
            Class<?> targetType = this.constructorParameterTypes[i];
            Object value = JdbcUtils.getResultSetValue(rs, index, targetType);
            usedIndex.add(index);
            args[i] = convertIfNecessary(value, targetType);
        }
        T mappedObject = BeanUtils.instantiateClass(this.mappedConstructor, args);

        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        for (int index = 1; index <= columnCount; index++) {
            if (!usedIndex.contains(index)) {
                Object desc = getDescriptor(JdbcUtils.lookupColumnName(rsmd, index));
                if (desc instanceof Method method) {
                    Class<?> targetType = method.getParameterTypes()[0];
                    Object value = JdbcUtils.getResultSetValue(rs, index, targetType);
                    value = convertIfNecessary(value, targetType);
                    ReflectionUtils.makeAccessible(method);
                    ReflectionUtils.invokeMethod(method, mappedObject, value);
                }
                else if (desc instanceof Field field) {
                    Object value = JdbcUtils.getResultSetValue(rs, index, field.getType());
                    value = convertIfNecessary(value, field.getType());
                    ReflectionUtils.makeAccessible(field);
                    ReflectionUtils.setField(field, mappedObject, value);
                }
            }
        }

        return mappedObject;
    }

    private @Nullable Object convertIfNecessary(@Nullable Object value, Class<?> targetType) {
        Class<?> targetClass = ClassUtils.resolvePrimitiveIfNecessary(targetType);
        if (value == null || targetClass.isInstance(value)) {
            return value;
        }
        return this.conversionService.convert(value, targetClass);
    }

    private Object getDescriptor(String column) {
        return this.propertyDescriptors.computeIfAbsent(column, name -> {
            PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(this.mappedClass, name);
            if (pd != null && pd.getWriteMethod() != null) {
                return pd.getWriteMethod();
            }
            Field field = ReflectionUtils.findField(this.mappedClass, name);
            if (field != null) {
                return field;
            }

            String adaptedName = JdbcUtils.convertUnderscoreNameToPropertyName(name);
            if (!adaptedName.equals(name)) {
                pd = BeanUtils.getPropertyDescriptor(this.mappedClass, adaptedName);
                if (pd != null && pd.getWriteMethod() != null) {
                    return pd.getWriteMethod();
                }
                field = ReflectionUtils.findField(this.mappedClass, adaptedName);
                if (field != null) {
                    return field;
                }
            }

            PropertyDescriptor[] pds = BeanUtils.getPropertyDescriptors(this.mappedClass);
            for (PropertyDescriptor candidate : pds) {
                if (name.equalsIgnoreCase(candidate.getName()) && candidate.getWriteMethod() != null) {
                    return candidate.getWriteMethod();
                }
            }
            field = ReflectionUtils.findFieldIgnoreCase(this.mappedClass, name);
            if (field != null) {
                return field;
            }

            return NO_DESCRIPTOR;
        });
    }
}
