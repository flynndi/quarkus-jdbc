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

package io.quarkiverse.jdbc.runtime.exception;

import org.jspecify.annotations.Nullable;

/**
 * Root of the hierarchy of data access exceptions.
 *
 * <p>This is derived from Spring Framework's {@code DataAccessException} but
 * belongs to this extension's Quarkus-native exception package.
 *
 * @author Rod Johnson
 */
@SuppressWarnings("serial")
public abstract class DataAccessException extends RuntimeException {

    /**
     * Constructor for DataAccessException.
     * @param msg the detail message
     */
    public DataAccessException(@Nullable String msg) {
        super(msg);
    }

    /**
     * Constructor for DataAccessException.
     * @param msg the detail message
     * @param cause the root cause (usually from using an underlying
     * data access API such as JDBC)
     */
    public DataAccessException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }

    /**
     * Retrieve the innermost cause of this exception, if any.
     * @return the innermost exception, or {@code null} if none
     */
    public @Nullable Throwable getRootCause() {
        return getRootCause(this);
    }

    /**
     * Retrieve the most specific cause of this exception.
     * @return the most specific cause (never {@code null})
     */
    public Throwable getMostSpecificCause() {
        Throwable rootCause = getRootCause();
        return (rootCause != null ? rootCause : this);
    }

    /**
     * Check whether this exception contains an exception of the given type.
     * @param exType the exception type to look for
     * @return whether there is a nested exception of the specified type
     */
    public boolean contains(@Nullable Class<?> exType) {
        if (exType == null) {
            return false;
        }
        if (exType.isInstance(this)) {
            return true;
        }
        Throwable cause = getCause();
        if (cause == this) {
            return false;
        }
        if (cause instanceof DataAccessException exception) {
            return exception.contains(exType);
        }
        while (cause != null) {
            if (exType.isInstance(cause)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static @Nullable Throwable getRootCause(@Nullable Throwable original) {
        if (original == null) {
            return null;
        }
        Throwable rootCause = null;
        Throwable cause = original.getCause();
        while (cause != null && cause != rootCause) {
            rootCause = cause;
            cause = cause.getCause();
        }
        return rootCause;
    }
}
