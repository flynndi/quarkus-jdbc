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

package io.quarkiverse.jdbc.runtime.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Minimal bean utilities used by JDBC runtime property mappers.
 */
public abstract class BeanUtils {

    private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new LinkedHashMap<>(8);

    static {
        primitiveWrapperTypeMap.put(boolean.class, Boolean.class);
        primitiveWrapperTypeMap.put(byte.class, Byte.class);
        primitiveWrapperTypeMap.put(char.class, Character.class);
        primitiveWrapperTypeMap.put(double.class, Double.class);
        primitiveWrapperTypeMap.put(float.class, Float.class);
        primitiveWrapperTypeMap.put(int.class, Integer.class);
        primitiveWrapperTypeMap.put(long.class, Long.class);
        primitiveWrapperTypeMap.put(short.class, Short.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> getResolvableConstructor(Class<T> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        if (constructors.length == 1) {
            return (Constructor<T>) constructors[0];
        }
        try {
            return clazz.getDeclaredConstructor();
        }
        catch (NoSuchMethodException ex) {
            if (clazz.isRecord()) {
                Constructor<?> canonical = findCanonicalRecordConstructor(clazz);
                if (canonical != null) {
                    return (Constructor<T>) canonical;
                }
            }
            throw new IllegalStateException("No primary or single unique constructor found for " + clazz.getName(), ex);
        }
    }

    private static @Nullable Constructor<?> findCanonicalRecordConstructor(Class<?> clazz) {
        Class<?>[] parameterTypes = new Class<?>[clazz.getRecordComponents().length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = clazz.getRecordComponents()[i].getType();
        }
        try {
            return clazz.getDeclaredConstructor(parameterTypes);
        }
        catch (NoSuchMethodException ex) {
            return null;
        }
    }

    public static String[] getParameterNames(Constructor<?> constructor) {
        if (constructor.getDeclaringClass().isRecord()) {
            String[] names = new String[constructor.getParameterCount()];
            for (int i = 0; i < names.length; i++) {
                names[i] = constructor.getDeclaringClass().getRecordComponents()[i].getName();
            }
            return names;
        }
        String[] names = new String[constructor.getParameterCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = constructor.getParameters()[i].getName();
        }
        return names;
    }


    public static <T> T instantiateClass(Class<T> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        try {
            return instantiateClass(clazz.getDeclaredConstructor());
        }
        catch (NoSuchMethodException ex) {
            throw new IllegalStateException("No default constructor found for " + clazz.getName(), ex);
        }
    }

    public static <T> T instantiateClass(Constructor<T> constructor, @Nullable Object... args) {
        try {
            if (!Modifier.isPublic(constructor.getModifiers()) || !Modifier.isPublic(constructor.getDeclaringClass().getModifiers())) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance(args);
        }
        catch (InstantiationException ex) {
            throw new IllegalStateException("Could not instantiate class: " + constructor.getDeclaringClass().getName(), ex);
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access constructor: " + constructor, ex);
        }
        catch (InvocationTargetException ex) {
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Constructor threw exception", targetException);
        }
    }

    public static @Nullable PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) {
        for (PropertyDescriptor descriptor : getPropertyDescriptors(clazz)) {
            if (descriptor.getName().equals(propertyName)) {
                return descriptor;
            }
        }
        Method recordAccessor = findRecordAccessor(clazz, propertyName);
        if (recordAccessor != null) {
            try {
                return new PropertyDescriptor(propertyName, recordAccessor, null);
            }
            catch (IntrospectionException ex) {
                throw new IllegalStateException("Failed to create record property descriptor", ex);
            }
        }
        return null;
    }

    public static PropertyDescriptor[] getPropertyDescriptors(Class<?> clazz) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
            return beanInfo.getPropertyDescriptors();
        }
        catch (IntrospectionException ex) {
            throw new IllegalStateException("Failed to introspect bean class: " + clazz.getName(), ex);
        }
    }

    private static @Nullable Method findRecordAccessor(Class<?> clazz, String propertyName) {
        if (!clazz.isRecord()) {
            return null;
        }
        for (var component : clazz.getRecordComponents()) {
            if (component.getName().equals(propertyName)) {
                return component.getAccessor();
            }
        }
        return null;
    }

    public static boolean isSimpleProperty(Class<?> type) {
        Assert.notNull(type, "'type' must not be null");
        return isSimpleValueType(type) || (type.isArray() && isSimpleValueType(type.componentType()));
    }

    public static boolean isSimpleValueType(Class<?> type) {
        return (type != void.class && type != Void.class &&
                (isPrimitiveOrWrapper(type) ||
                Enum.class.isAssignableFrom(type) ||
                CharSequence.class.isAssignableFrom(type) ||
                Number.class.isAssignableFrom(type) ||
                Date.class.isAssignableFrom(type) ||
                Temporal.class.isAssignableFrom(type) ||
                ZoneId.class.isAssignableFrom(type) ||
                TimeZone.class.isAssignableFrom(type) ||
                File.class.isAssignableFrom(type) ||
                Path.class.isAssignableFrom(type) ||
                Charset.class.isAssignableFrom(type) ||
                Currency.class.isAssignableFrom(type) ||
                InetAddress.class.isAssignableFrom(type) ||
                URI.class == type ||
                URL.class == type ||
                UUID.class == type ||
                Locale.class == type ||
                Pattern.class == type ||
                Class.class == type ||
                BigDecimal.class == type ||
                BigInteger.class == type));
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return (type.isPrimitive() || primitiveWrapperTypeMap.containsValue(type));
    }
}
