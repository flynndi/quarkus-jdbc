plugins {
    alias(libs.plugins.maven.publish)
}

base {
    archivesName.set("quarkus-jdbc-deployment")
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(project(":runtime"))
    implementation(libs.quarkus.core.deployment)
    implementation(libs.quarkus.arc.deployment)
    implementation(libs.quarkus.agroal.deployment)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "quarkus-jdbc-deployment",
        providers.gradleProperty("VERSION_NAME").get(),
    )

    pom {
        name.set("Quarkus JDBC Deployment")
        description.set("Quarkus extension deployment module for Spring-style JDBC template APIs.")
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
