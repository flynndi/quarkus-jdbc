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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.core.StatementCreatorUtils;
import io.quarkiverse.jdbc.runtime.util.Assert;
import io.quarkiverse.jdbc.runtime.util.BeanUtils;
import io.quarkiverse.jdbc.runtime.util.ReflectionUtils;
import io.quarkiverse.jdbc.runtime.util.StringUtils;

/**
 * {@link SqlParameterSource} implementation that obtains parameter values
 * from bean properties of a given JavaBean object. Supports components of
 * record classes as well, with accessor methods matching parameter names.
 *
 * @author Thomas Risberg
 * @author Juergen Hoeller
 * @since 2.0
 * @see NamedParameterJdbcTemplate
 * @see SimplePropertySqlParameterSource
 */
public class BeanPropertySqlParameterSource extends AbstractSqlParameterSource {

    private final Object object;

    private String @Nullable [] propertyNames;


    /**
     * Create a new BeanPropertySqlParameterSource for the given bean.
     * @param object the bean instance to wrap
     */
    public BeanPropertySqlParameterSource(Object object) {
        Assert.notNull(object, "Bean object must not be null");
        this.object = object;
    }


    @Override
    public boolean hasValue(String paramName) {
        PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(this.object.getClass(), paramName);
        if (pd != null && pd.getReadMethod() != null) {
            return true;
        }
        return (resolveIndexedMapValue(paramName) != null);
    }

    @Override
    public @Nullable Object getValue(String paramName) throws IllegalArgumentException {
        PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(this.object.getClass(), paramName);
        if (pd == null || pd.getReadMethod() == null) {
            IndexedMapValue indexedMapValue = resolveIndexedMapValue(paramName);
            if (indexedMapValue != null) {
                return indexedMapValue.value();
            }
            throw new IllegalArgumentException("Invalid property '" + paramName + "' of bean class [" +
                    this.object.getClass().getName() + "]: Bean property is not readable");
        }
        Method readMethod = pd.getReadMethod();
        ReflectionUtils.makeAccessible(readMethod);
        return ReflectionUtils.invokeMethod(readMethod, this.object);
    }

    /**
     * Derives a default SQL type from the corresponding property type.
     * @see StatementCreatorUtils#javaTypeToSqlParameterType
     */
    @Override
    public int getSqlType(String paramName) {
        int sqlType = super.getSqlType(paramName);
        if (sqlType != TYPE_UNKNOWN) {
            return sqlType;
        }
        IndexedMapValue indexedMapValue = resolveIndexedMapValue(paramName);
        if (indexedMapValue != null && indexedMapValue.value() != null) {
            return StatementCreatorUtils.javaTypeToSqlParameterType(indexedMapValue.value().getClass());
        }
        PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(this.object.getClass(), paramName);
        Class<?> propType = (pd != null ? pd.getPropertyType() : null);
        return StatementCreatorUtils.javaTypeToSqlParameterType(propType);
    }

    @Override
    public String[] getParameterNames() {
        return getReadablePropertyNames();
    }

    /**
     * Provide access to the property names of the wrapped bean.
     * @return an array containing all the known readable property names
     */
    public String[] getReadablePropertyNames() {
        if (this.propertyNames == null) {
            List<String> names = new ArrayList<>();
            for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(this.object.getClass())) {
                if (pd.getReadMethod() != null && !"class".equals(pd.getName())) {
                    names.add(pd.getName());
                }
            }
            if (this.object.getClass().isRecord()) {
                for (var component : this.object.getClass().getRecordComponents()) {
                    if (!names.contains(component.getName())) {
                        names.add(component.getName());
                    }
                }
            }
            this.propertyNames = StringUtils.toStringArray(names);
        }
        return this.propertyNames;
    }

    private @Nullable IndexedMapValue resolveIndexedMapValue(String paramName) {
        int openingBracket = paramName.indexOf('[');
        int closingBracket = paramName.indexOf(']', openingBracket + 1);
        if (openingBracket <= 0 || closingBracket != paramName.length() - 1) {
            return null;
        }
        PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(
                this.object.getClass(), paramName.substring(0, openingBracket));
        if (pd == null || pd.getReadMethod() == null) {
            return null;
        }
        Method readMethod = pd.getReadMethod();
        ReflectionUtils.makeAccessible(readMethod);
        Object propertyValue = ReflectionUtils.invokeMethod(readMethod, this.object);
        if (propertyValue instanceof Map<?, ?> map) {
            String key = paramName.substring(openingBracket + 1, closingBracket);
            return (map.containsKey(key) ? new IndexedMapValue(map.get(key)) : null);
        }
        return null;
    }

    private record IndexedMapValue(@Nullable Object value) {
    }
}
