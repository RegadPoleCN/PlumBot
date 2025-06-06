plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":common"))
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
    // bukkit
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-platform-bukkit:4.4.0")
    // libby
    implementation("com.alessiodp.libby:libby-bukkit:2.0.0-SNAPSHOT")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(project.properties)
        }
    }
    
    shadowJar {
        relocate("net.byteflux.libby", "me.regadpole.plumbot.lib.net.byteflux.libby")
        
        dependencies {
            include(dependency("com.alessiodp.libby:libby-bukkit:2.0.0-SNAPSHOT"))
        }

        minimize()
    }
    
    build {
        dependsOn(shadowJar)
    }
}