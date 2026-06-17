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

package io.quarkiverse.jdbc.runtime.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithName;

/**
 * Runtime configuration for quarkus-jdbc.
 */
@ConfigMapping(prefix = "quarkus.jdbc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface JdbcRuntimeConfig {

    @WithName("datasource")
    @WithDefaults
    Map<String, DataSourceRuntimeConfig> dataSources();

    @ConfigGroup
    interface DataSourceRuntimeConfig {

        @WithDefaults
        SqlInitRuntimeConfig sqlInit();
    }

    @ConfigGroup
    interface SqlInitRuntimeConfig {

        /**
         * Whether SQL script execution should continue after an error.
         */
        @WithDefault("false")
        boolean continueOnError();

        /**
         * Whether failed DROP statements should be ignored.
         */
        @WithDefault("false")
        boolean ignoreFailedDrops();

        /**
         * SQL script encoding.
         */
        Optional<String> encoding();

        /**
         * SQL statement separator.
         */
        @WithDefault(";")
        String separator();
    }
}
