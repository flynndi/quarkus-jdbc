package io.quarkiverse.jdbc.runtime;

import java.util.List;
import java.util.function.Function;

import io.quarkiverse.jdbc.runtime.config.JdbcRuntimeConfig;
import io.quarkiverse.jdbc.runtime.core.JdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.simple.JdbcClient;
import io.quarkiverse.jdbc.runtime.datasource.init.DataSourceInitializer;
import io.quarkiverse.jdbc.runtime.datasource.init.JdbcSqlDataSourceInitializer;
import io.quarkiverse.jdbc.runtime.datasource.init.ResourceDatabasePopulator;
import io.quarkiverse.jdbc.runtime.io.ClassPathResource;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

import javax.sql.DataSource;

@Recorder
public class JdbcBeansRecorder {

    private final RuntimeValue<JdbcRuntimeConfig> runtimeConfig;

    public JdbcBeansRecorder(RuntimeValue<JdbcRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<JdbcTemplate>, JdbcTemplate> jdbcTemplateFunction(String dataSourceName, boolean sqlInitEnabled) {
        return context -> {
            if (sqlInitEnabled) {
                context.getInjectedReference(JdbcSqlDataSourceInitializer.class, AgroalDataSourceUtil.qualifier(dataSourceName));
            }
            DataSource dataSource = context.getInjectedReference(DataSource.class, AgroalDataSourceUtil.qualifier(dataSourceName));
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.afterPropertiesSet();
            return jdbcTemplate;
        };
    }

    public Function<SyntheticCreationalContext<NamedParameterJdbcTemplate>, NamedParameterJdbcTemplate> namedParameterJdbcTemplateFunction(String dataSourceName) {
        return context -> {
            JdbcTemplate jdbcTemplate = context.getInjectedReference(
                    JdbcTemplate.class, AgroalDataSourceUtil.qualifier(dataSourceName));
            return new NamedParameterJdbcTemplate(jdbcTemplate);
        };
    }

    public Function<SyntheticCreationalContext<JdbcClient>, JdbcClient> jdbcClientFunction(String dataSourceName) {
        return context -> {
            NamedParameterJdbcTemplate namedParameterJdbcTemplate = context.getInjectedReference(
                    NamedParameterJdbcTemplate.class, AgroalDataSourceUtil.qualifier(dataSourceName));
            return JdbcClient.create(namedParameterJdbcTemplate);
        };
    }

    public Function<SyntheticCreationalContext<JdbcSqlDataSourceInitializer>, JdbcSqlDataSourceInitializer> sqlDataSourceInitializerFunction(String dataSourceName, List<String> locations, List<String> cleanupLocations) {
        return context -> {
            DataSource dataSource = context.getInjectedReference(DataSource.class, AgroalDataSourceUtil.qualifier(dataSourceName));
            DataSourceInitializer initializer = new DataSourceInitializer();
            initializer.setDataSource(dataSource);
            if (!locations.isEmpty()) {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                JdbcRuntimeConfig.DataSourceRuntimeConfig dataSourceConfig = this.runtimeConfig.getValue().dataSources().get(dataSourceName);
                if (dataSourceConfig != null) {
                    JdbcRuntimeConfig.SqlInitRuntimeConfig sqlInit = dataSourceConfig.sqlInit();
                    populator.setContinueOnError(sqlInit.continueOnError());
                    populator.setIgnoreFailedDrops(sqlInit.ignoreFailedDrops());
                    populator.setSeparator(sqlInit.separator());
                    sqlInit.encoding().ifPresent(populator::setSqlScriptEncoding);
                }
                for (String location : locations) {
                    populator.addScript(new ClassPathResource(location));
                }
                initializer.setDatabasePopulator(populator);
            }
            if (!cleanupLocations.isEmpty()) {
                ResourceDatabasePopulator cleaner = new ResourceDatabasePopulator();
                for (String cleanupLocation : cleanupLocations) {
                    cleaner.addScript(new ClassPathResource(cleanupLocation));
                }
                initializer.setDatabaseCleaner(cleaner);
            }
            initializer.afterPropertiesSet();
            return new JdbcSqlDataSourceInitializer(dataSourceName, initializer);
        };
    }
}
