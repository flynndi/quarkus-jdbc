package io.quarkiverse.jdbc.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.jdbc.runtime.JdbcBeansRecorder;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import io.quarkiverse.jdbc.runtime.config.JdbcBuildTimeConfig;
import io.quarkiverse.jdbc.runtime.core.JdbcOperations;
import io.quarkiverse.jdbc.runtime.core.JdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcOperations;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.simple.JdbcClient;
import io.quarkiverse.jdbc.runtime.datasource.init.JdbcSqlDataSourceInitializer;
import io.quarkiverse.jdbc.runtime.datasource.init.JdbcSqlDataSourceInitializerDestroyer;
import io.quarkiverse.jdbc.runtime.support.SQLErrorCodesFactory;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.deployment.AgroalDataSourceBuildUtil;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;

@BuildSteps
public class JdbcProcessor {

    private static final String FEATURE = "jdbc";

    private static String jdbcTemplateBeanName(String dataSourceName) {
        return FEATURE + "_template_" + dataSourceName;
    }

    private static String namedParameterJdbcTemplateBeanName(String dataSourceName) {
        return FEATURE + "_named_parameter_" + dataSourceName;
    }

    private static String jdbcClientBeanName(String dataSourceName) {
        return FEATURE + "_client_" + dataSourceName;
    }

    private static String sqlDataSourceInitializerBeanName(String dataSourceName) {
        return FEATURE + "_sql_init_" + dataSourceName;
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }


    @BuildStep
    NativeImageResourceBuildItem sqlErrorCodesResource() {
        return new NativeImageResourceBuildItem(
                SQLErrorCodesFactory.SQL_ERROR_CODE_DEFAULT_PATH,
                SQLErrorCodesFactory.SQL_ERROR_CODE_OVERRIDE_PATH);
    }

    @BuildStep
    void sqlInitScripts(JdbcBuildTimeConfig jdbcBuildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            ApplicationArchivesBuildItem applicationArchives,
            BuildProducer<SqlInitScriptsBuildItem> sqlInitScripts) {

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            JdbcBuildTimeConfig.DataSourceBuildTimeConfig dataSourceConfig = jdbcBuildTimeConfig.dataSources().get(jdbcDataSourceBuildItem.getName());
            JdbcBuildTimeConfig.SqlInitBuildTimeConfig sqlInit = dataSourceConfig != null ? dataSourceConfig.sqlInit() : null;
            if (sqlInit != null && sqlInit.isEnabledFor(jdbcDataSourceBuildItem.getDbKind())) {
                SqlInitScriptsBuildItem.resolve(jdbcDataSourceBuildItem.getName(), sqlInit, applicationArchives)
                        .ifPresent(sqlInitScripts::produce);
            }
        }
    }

    @BuildStep
    void sqlInitResources(List<SqlInitScriptsBuildItem> sqlInitScripts,
            BuildProducer<NativeImageResourceBuildItem> resources) {

        List<String> resourcePaths = new ArrayList<>();
        for (SqlInitScriptsBuildItem sqlInitScript : sqlInitScripts) {
            resourcePaths.addAll(sqlInitScript.resourceNames());
        }
        if (!resourcePaths.isEmpty()) {
            resources.produce(new NativeImageResourceBuildItem(resourcePaths.toArray(String[]::new)));
        }
    }

    @BuildStep
    @Produce(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(SqlInitScriptsBuildItem.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void initializeJdbc(JdbcBeansRecorder jdbcBeansRecorder,
                        List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
                        List<SqlInitScriptsBuildItem> sqlInitScripts,
                        BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        Map<String, SqlInitScriptsBuildItem> sqlInitScriptsByDataSourceName = new HashMap<>();
        for (SqlInitScriptsBuildItem sqlInitScript : sqlInitScripts) {
            sqlInitScriptsByDataSourceName.put(sqlInitScript.dataSourceName(), sqlInitScript);
        }
        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();
            AnnotationInstance jdbcQualifier = AgroalDataSourceBuildUtil.qualifier(dataSourceName);
            SqlInitScriptsBuildItem scripts = sqlInitScriptsByDataSourceName.get(dataSourceName);
            boolean sqlInitEnabled = scripts != null;

            if (sqlInitEnabled) {
                syntheticBeans.produce(configureSqlDataSourceInitializer(jdbcBeansRecorder, dataSourceName, jdbcQualifier, scripts.locations(), scripts.cleanupLocations()).done());
            }
            syntheticBeans.produce(configureJdbcTemplate(jdbcBeansRecorder, dataSourceName, jdbcQualifier, sqlInitEnabled).done());
            syntheticBeans.produce(configureNamedParameterJdbcTemplate(jdbcBeansRecorder, dataSourceName, jdbcQualifier).done());
            syntheticBeans.produce(configureJdbcClient(jdbcBeansRecorder, dataSourceName, jdbcQualifier).done());
        }
    }

    private SyntheticBeanBuildItem.ExtendedBeanConfigurator configureSqlDataSourceInitializer(JdbcBeansRecorder recorder, String dataSourceName, AnnotationInstance jdbcQualifier, List<String> locations, List<String> cleanupLocations) {
        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(JdbcSqlDataSourceInitializer.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .unremovable()
                .startup()
                .addInjectionPoint(ClassType.create(DotName.createSimple(javax.sql.DataSource.class.getName())), jdbcQualifier)
                .createWith(recorder.sqlDataSourceInitializerFunction(dataSourceName, locations, cleanupLocations))
                .destroyer(JdbcSqlDataSourceInitializerDestroyer.class);
        return addQualifiers(configurator, dataSourceName, sqlDataSourceInitializerBeanName(dataSourceName));
    }

    private SyntheticBeanBuildItem.ExtendedBeanConfigurator configureJdbcTemplate(JdbcBeansRecorder recorder, String dataSourceName, AnnotationInstance jdbcQualifier, boolean sqlInitEnabled) {
        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(JdbcTemplate.class)
                .addType(JdbcOperations.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .unremovable()
                .addInjectionPoint(ClassType.create(DotName.createSimple(javax.sql.DataSource.class.getName())), jdbcQualifier)
                .createWith(recorder.jdbcTemplateFunction(dataSourceName, sqlInitEnabled));
        if (sqlInitEnabled) {
            configurator.addInjectionPoint(ClassType.create(DotName.createSimple(JdbcSqlDataSourceInitializer.class.getName())), jdbcQualifier);
        }
        return addQualifiers(configurator, dataSourceName, jdbcTemplateBeanName(dataSourceName));
    }

    private SyntheticBeanBuildItem.ExtendedBeanConfigurator configureNamedParameterJdbcTemplate(JdbcBeansRecorder recorder, String dataSourceName, AnnotationInstance jdbcQualifier) {
        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(NamedParameterJdbcTemplate.class)
                .addType(NamedParameterJdbcOperations.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .unremovable()
                .addInjectionPoint(ClassType.create(DotName.createSimple(JdbcTemplate.class.getName())), jdbcQualifier)
                .createWith(recorder.namedParameterJdbcTemplateFunction(dataSourceName));
        return addQualifiers(configurator, dataSourceName, namedParameterJdbcTemplateBeanName(dataSourceName));
    }

    private SyntheticBeanBuildItem.ExtendedBeanConfigurator configureJdbcClient(JdbcBeansRecorder recorder, String dataSourceName, AnnotationInstance jdbcQualifier) {
        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(JdbcClient.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .unremovable()
                .addInjectionPoint(ClassType.create(DotName.createSimple(NamedParameterJdbcTemplate.class.getName())), jdbcQualifier)
                .createWith(recorder.jdbcClientFunction(dataSourceName));
        return addQualifiers(configurator, dataSourceName, jdbcClientBeanName(dataSourceName));
    }

    private SyntheticBeanBuildItem.ExtendedBeanConfigurator addQualifiers(SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator, String dataSourceName, String beanName) {
        if (DataSourceUtil.isDefault(dataSourceName)) {
            configurator.addQualifier(Default.class);
            configurator.priority(10);
        } else {
            configurator.name(beanName);
            configurator.priority(5);
            configurator.addQualifier().annotation(DotNames.NAMED).addValue("value", beanName).done();
            configurator.addQualifier().annotation(DataSource.class).addValue("value", dataSourceName).done();
        }
        return configurator;
    }

}
