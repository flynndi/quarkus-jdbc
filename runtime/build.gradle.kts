plugins {
    alias(libs.plugins.quarkus.extension)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

base {
    archivesName.set("quarkus-jdbc")
}

quarkusExtension {
    deploymentModule.set("deployment")
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(libs.jboss.logging)
    api(libs.jspecify)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.agroal)
    compileOnly(libs.kotlin.reflect)
    compileOnly(libs.kotlin.stdlib)

    testImplementation(platform(libs.quarkus.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "quarkus-jdbc",
        providers.gradleProperty("VERSION_NAME").get(),
    )

    pom {
        name.set("Quarkus JDBC")
        description.set("Quarkus extension runtime for Spring-style JDBC template APIs.")
        url.set("https://github.com/flynndi/quarkus-jdbc")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("flynndi")
                name.set("flynndi")
                email.set("lixuan0520@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/flynndi/quarkus-jdbc")
            connection.set("scm:git:https://github.com/flynndi/quarkus-jdbc.git")
            developerConnection.set("scm:git:ssh://git@github.com/flynndi/quarkus-jdbc.git")
        }
    }
}
