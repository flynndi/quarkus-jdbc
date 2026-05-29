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

package io.quarkiverse.jdbc.runtime.convert;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.util.NumberUtils;

/**
 * Minimal default conversion service used by JDBC result mappers.
 */
public class DefaultConversionService implements ConversionService {

    private static volatile @Nullable DefaultConversionService sharedInstance;

    public static ConversionService getSharedInstance() {
        DefaultConversionService cs = sharedInstance;
        if (cs == null) {
            synchronized (DefaultConversionService.class) {
                cs = sharedInstance;
                if (cs == null) {
                    cs = new DefaultConversionService();
                    sharedInstance = cs;
                }
            }
        }
        return cs;
    }

    @Override
    public boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (sourceType == null || targetType.isAssignableFrom(sourceType)) {
            return true;
        }
        if (String.class == targetType) {
            return true;
        }
        if (Number.class.isAssignableFrom(targetType)) {
            return (Number.class.isAssignableFrom(sourceType) || String.class == sourceType);
        }
        if (Boolean.class == targetType) {
            return (Boolean.class == sourceType || String.class == sourceType);
        }
        if (Character.class == targetType) {
            return (Character.class == sourceType || String.class == sourceType);
        }
        if (targetType.isEnum()) {
            return String.class == sourceType;
        }
        if (UUID.class == targetType) {
            return String.class == sourceType;
        }
        return false;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> @Nullable T convert(@Nullable Object source, Class<T> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (source == null) {
            return null;
        }
        if (targetType.isInstance(source)) {
            return targetType.cast(source);
        }
        if (String.class == targetType) {
            return (T) source.toString();
        }
        if (Number.class.isAssignableFrom(targetType)) {
            if (source instanceof Number number) {
                return (T) NumberUtils.convertNumberToTargetClass(number, (Class<Number>) targetType);
            }
            return (T) NumberUtils.parseNumber(source.toString(), (Class<Number>) targetType);
        }
        if (Boolean.class == targetType && source instanceof String text) {
            return (T) Boolean.valueOf(text);
        }
        if (Character.class == targetType && source instanceof String text) {
            if (text.length() != 1) {
                throw new IllegalArgumentException("Cannot convert String [" + text + "] to Character");
            }
            return (T) Character.valueOf(text.charAt(0));
        }
        if (targetType.isEnum() && source instanceof String text) {
            return (T) Enum.valueOf((Class<Enum>) targetType.asSubclass(Enum.class), text);
        }
        if (UUID.class == targetType && source instanceof String text) {
            return (T) UUID.fromString(text);
        }
        throw new IllegalArgumentException("Value [" + source + "] of type [" + source.getClass().getName() +
                "] cannot be converted to required type [" + targetType.getName() + "]");
    }
}
