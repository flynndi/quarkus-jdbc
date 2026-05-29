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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Minimal synchronized LRU cache used by the JDBC runtime.
 */
public class ConcurrentLruCache<K, V> {

    private final int capacity;

    private final Function<K, V> generator;

    private final Map<K, V> cache;

    public ConcurrentLruCache(int capacity, Function<K, V> generator) {
        this.capacity = Math.max(capacity, 0);
        this.generator = generator;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return ConcurrentLruCache.this.capacity > 0 && size() > ConcurrentLruCache.this.capacity;
            }
        };
    }

    public V get(K key) {
        if (this.capacity == 0) {
            return this.generator.apply(key);
        }
        synchronized (this.cache) {
            V value = this.cache.get(key);
            if (value == null && !this.cache.containsKey(key)) {
                value = this.generator.apply(key);
                this.cache.put(key, value);
            }
            return value;
        }
    }

    public int capacity() {
        return this.capacity;
    }
}
