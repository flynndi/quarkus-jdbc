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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import org.jspecify.annotations.Nullable;

import io.quarkiverse.jdbc.runtime.util.Assert;

/**
 * Minimal encoded resource wrapper used by SQL script initialization.
 */
public class EncodedResource {

    private final Resource resource;

    private final @Nullable String encoding;

    public EncodedResource(Resource resource) {
        this(resource, null);
    }

    public EncodedResource(Resource resource, @Nullable String encoding) {
        Assert.notNull(resource, "Resource must not be null");
        this.resource = resource;
        this.encoding = encoding;
    }

    public Resource getResource() {
        return this.resource;
    }

    public @Nullable String getEncoding() {
        return this.encoding;
    }

    public Reader getReader() throws IOException {
        if (this.encoding != null) {
            return new InputStreamReader(this.resource.getInputStream(), Charset.forName(this.encoding));
        }
        return new InputStreamReader(this.resource.getInputStream());
    }

    @Override
    public String toString() {
        return this.resource.toString();
    }
}
