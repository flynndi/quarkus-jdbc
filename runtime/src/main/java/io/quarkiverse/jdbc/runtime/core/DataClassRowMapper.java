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

import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.convert.ConversionService;
import io.quarkiverse.jdbc.runtime.util.Assert;
import io.quarkiverse.jdbc.runtime.util.BeanUtils;

/**
 * {@link RowMapper} implementation that converts a row into a new instance
 * of the specified mapped target class, supporting constructor parameters.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 5.3
 * @param <T> the result type
 * @see SimplePropertyRowMapper
 */
public class DataClassRowMapper<T> extends BeanPropertyRowMapper<T> {

    private @Nullable Constructor<T> mappedConstructor;

    private String @Nullable [] constructorParameterNames;

    private Class<?> @Nullable [] constructorParameterTypes;


    /**
     * Create a new {@code DataClassRowMapper} for bean-style configuration.
     * @see #setMappedClass
     * @see #setConversionService
     */
    public DataClassRowMapper() {
    }

    /**
     * Create a new {@code DataClassRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     */
    public DataClassRowMapper(Class<T> mappedClass) {
        super(mappedClass);
    }


    @Override
    protected void initialize(Class<T> mappedClass) {
        super.initialize(mappedClass);

        this.mappedConstructor = BeanUtils.getResolvableConstructor(mappedClass);
        int paramCount = this.mappedConstructor.getParameterCount();
        if (paramCount > 0) {
            this.constructorParameterNames = BeanUtils.getParameterNames(this.mappedConstructor);
            for (String name : this.constructorParameterNames) {
                suppressProperty(name);
            }
            this.constructorParameterTypes = this.mappedConstructor.getParameterTypes();
        }
    }

    @Override
    protected T constructMappedInstance(ResultSet rs) throws SQLException {
        Assert.state(this.mappedConstructor != null, "Mapped constructor was not initialized");

        @Nullable Object[] args;
        if (this.constructorParameterNames != null && this.constructorParameterTypes != null) {
            args = new Object[this.constructorParameterNames.length];
            for (int i = 0; i < args.length; i++) {
                String name = this.constructorParameterNames[i];
                int index;
                try {
                    index = rs.findColumn(lowerCaseName(name));
                }
                catch (SQLException ex) {
                    index = rs.findColumn(underscoreName(name));
                }
                Class<?> targetType = this.constructorParameterTypes[i];
                Object value = getColumnValue(rs, index, targetType);
                args[i] = convertValueIfNecessary(value, targetType);
            }
        }
        else {
            args = new Object[0];
        }

        return BeanUtils.instantiateClass(this.mappedConstructor, args);
    }


    /**
     * Static factory method to create a new {@code DataClassRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     */
    public static <T> DataClassRowMapper<T> newInstance(Class<T> mappedClass) {
        return new DataClassRowMapper<>(mappedClass);
    }

    /**
     * Static factory method to create a new {@code DataClassRowMapper}.
     * @param mappedClass the class that each row should be mapped to
     * @param conversionService the {@link ConversionService} for binding JDBC values to bean properties, or {@code null} for none
     */
    public static <T> DataClassRowMapper<T> newInstance(
            Class<T> mappedClass, @Nullable ConversionService conversionService) {

        DataClassRowMapper<T> rowMapper = newInstance(mappedClass);
        rowMapper.setConversionService(conversionService);
        return rowMapper;
    }
}
