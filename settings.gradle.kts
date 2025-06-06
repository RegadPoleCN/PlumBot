dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc-repo"
        }
        maven("https://oss.sonatype.org/content/groups/public/") {
            name = "sonatype"
        }
        maven("https://jitpack.io") {
            name = "jitpack.io"
        }
        maven("https://repo.opencollab.dev/main/") {
            name = "opencollab-snapshot"
        }
        maven("https://repo.alessiodp.com/releases/") {
            name = "AlessioDP"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// 只保留需要的模块
include(":common") 
include(":bukkit")

rootProject.name = "PlumBot-V3"
