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
        maven {
            name = "hytalemoddingSnapshots"
            url = uri("https://maven.hytalemodding.dev/snapshots")
        }
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
            name = "maven-snapshots"
        }
        maven {
            name = "hytale"
            url = uri("https://maven.hytale.com/release") // 预发布版本改用 "hytale-pre-release"
        }
        maven {
            name = "ArikSquad"
            url = uri("https://repo.codemc.io/repository/ArikSquad/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":api")
include(":render")
include(":common") 
include(":bukkit")
include(":velocity")
include(":hytale")
include(":adapter:onebot")
include(":adapter:miraimc")

rootProject.name = "PlumBot-V3"
