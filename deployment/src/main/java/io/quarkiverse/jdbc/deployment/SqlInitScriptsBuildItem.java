package io.quarkiverse.jdbc.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.quarkiverse.jdbc.runtime.config.JdbcBuildTimeConfig;
import io.quarkiverse.jdbc.runtime.io.ClassPathResource;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;

final class SqlInitScriptsBuildItem extends MultiBuildItem {

    private final String dataSourceName;

    private final List<String> locations;

    private final List<String> cleanupLocations;

    private SqlInitScriptsBuildItem(String dataSourceName, List<String> locations, List<String> cleanupLocations) {
        this.dataSourceName = dataSourceName;
        this.locations = List.copyOf(locations);
        this.cleanupLocations = List.copyOf(cleanupLocations);
    }

    static Optional<SqlInitScriptsBuildItem> resolve(String dataSourceName,
            JdbcBuildTimeConfig.SqlInitBuildTimeConfig sqlInit,
            ApplicationArchivesBuildItem applicationArchives) {

        List<String> locations = new ArrayList<>();
        Optional<List<String>> configuredSchemaLocations = sqlInit.schemaLocations();
        if (configuredSchemaLocations.isPresent() && !configuredSchemaLocations.get().isEmpty()) {
            locations.addAll(configuredSchemaLocations.get());
        } else {
            List<String> candidates = new ArrayList<>();
            candidates.add("schema.sql");
            sqlInit.platform().ifPresent(platform -> candidates.add("schema-" + platform + ".sql"));
            for (String candidate : candidates) {
                for (ApplicationArchive archive : applicationArchives.getAllApplicationArchives()) {
                    if (archive.getChildPath(candidate) != null) {
                        locations.add(candidate);
                        break;
                    }
                }
            }
        }

        Optional<List<String>> configuredDataLocations = sqlInit.dataLocations();
        if (configuredDataLocations.isPresent() && !configuredDataLocations.get().isEmpty()) {
            locations.addAll(configuredDataLocations.get());
        } else {
            List<String> candidates = new ArrayList<>();
            candidates.add("data.sql");
            sqlInit.platform().ifPresent(platform -> candidates.add("data-" + platform + ".sql"));
            for (String candidate : candidates) {
                for (ApplicationArchive archive : applicationArchives.getAllApplicationArchives()) {
                    if (archive.getChildPath(candidate) != null) {
                        locations.add(candidate);
                        break;
                    }
                }
            }
        }

        List<String> cleanupLocations = new ArrayList<>();
        if (sqlInit.cleanup().enabled()) {
            sqlInit.cleanup().locations().ifPresent(cleanupLocations::addAll);
        }
        if (locations.isEmpty() && cleanupLocations.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SqlInitScriptsBuildItem(dataSourceName, locations, cleanupLocations));
    }

    String dataSourceName() {
        return this.dataSourceName;
    }

    List<String> locations() {
        return this.locations;
    }

    List<String> cleanupLocations() {
        return this.cleanupLocations;
    }

    List<String> resourceNames() {
        List<String> resourceNames = new ArrayList<>();
        for (String location : this.locations) {
            resourceNames.add(ClassPathResource.toResourceName(location));
        }
        for (String cleanupLocation : this.cleanupLocations) {
            resourceNames.add(ClassPathResource.toResourceName(cleanupLocation));
        }
        return resourceNames;
    }
}
