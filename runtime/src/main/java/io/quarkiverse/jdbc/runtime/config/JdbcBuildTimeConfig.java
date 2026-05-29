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

import java.util.List;
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
 * Build-time fixed configuration for quarkus-jdbc.
 */
@ConfigMapping(prefix = "quarkus.jdbc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface JdbcBuildTimeConfig {

    @WithName("datasource")
    @WithDefaults
    Map<String, DataSourceBuildTimeConfig> dataSources();

    @ConfigGroup
    interface DataSourceBuildTimeConfig {

        @WithDefaults
        SqlInitBuildTimeConfig sqlInit();
    }

    @ConfigGroup
    interface SqlInitBuildTimeConfig {

        /**
         * Whether SQL script initialization is enabled.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * Controls when SQL script initialization should run.
         */
        @WithDefault("embedded")
        DatabaseInitializationMode mode();

        /**
         * Schema script locations.
         */
        Optional<List<String>> schemaLocations();

        /**
         * Data script locations.
         */
        Optional<List<String>> dataLocations();

        /**
         * Platform suffix used when resolving database-specific script locations.
         */
        Optional<String> platform();

        /**
         * SQL script cleanup configuration.
         */
        @WithDefaults
        CleanupBuildTimeConfig cleanup();

        default boolean isEnabledFor(String dbKind) {
            if (!enabled() || mode() == DatabaseInitializationMode.NEVER) {
                return false;
            }
            if (mode() == DatabaseInitializationMode.ALWAYS) {
                return true;
            }
            return "h2".equalsIgnoreCase(dbKind) ||
                    "hsql".equalsIgnoreCase(dbKind) ||
                    "hsqldb".equalsIgnoreCase(dbKind) ||
                    "derby".equalsIgnoreCase(dbKind);
        }
    }

    @ConfigGroup
    interface CleanupBuildTimeConfig {

        /**
         * Whether SQL script cleanup is enabled.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * Cleanup script locations.
         */
        Optional<List<String>> locations();
    }
}
