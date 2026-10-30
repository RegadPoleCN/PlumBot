dependencyResolutionManagement {

    // buildSrc 的依赖从 Maven Central 解析。
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    // 复用主构建的版本目录（gradle/libs.versions.toml）。
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "buildSrc"