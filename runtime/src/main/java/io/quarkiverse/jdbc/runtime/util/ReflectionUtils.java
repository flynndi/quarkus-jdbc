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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jspecify.annotations.Nullable;

/**
 * Minimal reflection utilities used by JDBC runtime property mappers.
 */
public abstract class ReflectionUtils {

    public static void makeAccessible(AccessibleObject accessibleObject) {
        if (!accessibleObject.canAccess(null)) {
            accessibleObject.setAccessible(true);
        }
    }

    public static void makeAccessible(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            method.setAccessible(true);
        }
    }

    public static void makeAccessible(Field field) {
        if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isPublic(field.getDeclaringClass().getModifiers())
                || Modifier.isFinal(field.getModifiers())) {
            field.setAccessible(true);
        }
    }

    public static @Nullable Field findField(Class<?> clazz, String name) {
        Assert.notNull(clazz, "Class must not be null");
        Assert.notNull(name, "Field name must not be null");
        Class<?> searchType = clazz;
        while (searchType != null && searchType != Object.class) {
            try {
                return searchType.getDeclaredField(name);
            }
            catch (NoSuchFieldException ex) {
                searchType = searchType.getSuperclass();
            }
        }
        return null;
    }

    public static @Nullable Field findFieldIgnoreCase(Class<?> clazz, String name) {
        Assert.notNull(clazz, "Class must not be null");
        Assert.notNull(name, "Field name must not be null");
        Class<?> searchType = clazz;
        while (searchType != null && searchType != Object.class) {
            for (Field field : searchType.getDeclaredFields()) {
                if (name.equalsIgnoreCase(field.getName())) {
                    return field;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    public static @Nullable Object invokeMethod(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access method: " + method, ex);
        }
        catch (InvocationTargetException ex) {
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Invocation of method failed: " + method, targetException);
        }
    }

    public static @Nullable Object getField(Field field, Object target) {
        try {
            return field.get(target);
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access field: " + field, ex);
        }
    }

    public static void setField(Field field, Object target, @Nullable Object value) {
        try {
            field.set(target, value);
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access field: " + field, ex);
        }
    }
}
