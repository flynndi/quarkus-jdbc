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

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Minimal class utilities used by the JDBC runtime.
 */
public abstract class ClassUtils {

    private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<>(9);

    static {
        primitiveTypeToWrapperMap.put(boolean.class, Boolean.class);
        primitiveTypeToWrapperMap.put(byte.class, Byte.class);
        primitiveTypeToWrapperMap.put(char.class, Character.class);
        primitiveTypeToWrapperMap.put(double.class, Double.class);
        primitiveTypeToWrapperMap.put(float.class, Float.class);
        primitiveTypeToWrapperMap.put(int.class, Integer.class);
        primitiveTypeToWrapperMap.put(long.class, Long.class);
        primitiveTypeToWrapperMap.put(short.class, Short.class);
        primitiveTypeToWrapperMap.put(void.class, Void.class);
    }

    public static Class<?> resolvePrimitiveIfNecessary(Class<?> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        return (clazz.isPrimitive() && clazz != void.class ? primitiveTypeToWrapperMap.get(clazz) : clazz);
    }
}
