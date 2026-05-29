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

import java.util.Arrays;
import java.util.Collection;

import org.jspecify.annotations.Nullable;

/**
 * Minimal String utilities used by the JDBC runtime.
 */
public abstract class StringUtils {

    public static boolean hasLength(@Nullable CharSequence str) {
        return (str != null && !str.isEmpty());
    }

    public static boolean hasLength(@Nullable String str) {
        return (str != null && !str.isEmpty());
    }

    public static boolean hasText(@Nullable CharSequence str) {
        if (str == null) {
            return false;
        }
        int strLen = str.length();
        if (strLen == 0) {
            return false;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasText(@Nullable String str) {
        return hasText((CharSequence) str);
    }

    public static String trimAllWhitespace(String str) {
        if (!hasLength(str)) {
            return str;
        }
        int len = str.length();
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String delete(String inString, String pattern) {
        if (!hasLength(inString) || !hasLength(pattern)) {
            return inString;
        }
        return inString.replace(pattern, "");
    }

    public static boolean startsWithIgnoreCase(@Nullable String str, @Nullable String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public static String[] sortStringArray(String[] array) {
        if (array.length == 0) {
            return array;
        }
        Arrays.sort(array);
        return array;
    }

    public static String[] toStringArray(Collection<String> collection) {
        return collection.toArray(new String[0]);
    }
}
