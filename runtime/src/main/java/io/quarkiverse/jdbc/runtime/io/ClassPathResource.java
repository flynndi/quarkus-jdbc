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

package io.quarkiverse.jdbc.runtime.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Classpath-backed resource used by SQL script initialization.
 */
public class ClassPathResource implements Resource {

    private final String path;

    public ClassPathResource(String location) {
        this.path = toResourceName(location);
    }

    public static String toResourceName(String location) {
        if (location == null) {
            throw new IllegalArgumentException("Classpath resource location must not be null");
        }
        String path = location;
        if (path.startsWith("classpath*:")) {
            path = path.substring("classpath*:".length());
        }
        else if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
        }
        else if (path.contains(":")) {
            throw new IllegalArgumentException("Only classpath resource locations are supported: " + location);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(this.path);
        if (inputStream == null) {
            inputStream = ClassPathResource.class.getClassLoader().getResourceAsStream(this.path);
        }
        if (inputStream == null) {
            throw new FileNotFoundException("Classpath resource '" + this.path + "' does not exist");
        }
        return inputStream;
    }

    @Override
    public long contentLength() throws IOException {
        try (InputStream inputStream = getInputStream()) {
            return inputStream.transferTo(OutputStream.nullOutputStream());
        }
    }

    @Override
    public String toString() {
        return "classpath:" + this.path;
    }

}
