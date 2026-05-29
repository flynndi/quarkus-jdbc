plugins {
    java
    alias(libs.plugins.quarkus.application)
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(project(":runtime"))

    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.agroal)
    implementation(libs.quarkus.config.yaml)
    implementation(libs.quarkus.jdbc.h2)

    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.rest.assured)
    testRuntimeOnly(libs.junit.platform.launcher)
}
