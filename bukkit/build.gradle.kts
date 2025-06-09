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
    compileOnly("org.spigotmc:spigot-api:1.13-R0.1-SNAPSHOT")
    implementation("net.kyori:adventure-platform-bukkit:4.4.0")
    // libby
    implementation("com.alessiodp.libby:libby-bukkit:2.0.0-SNAPSHOT")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(rootProject.properties)
        }
    }
    
    shadowJar {
        dependencies {
            exclude(dependency("com.mojang:brigadier"))
//            include(dependency("com.alessiodp.libby:libby-bukkit:2.0.0-SNAPSHOT"))
        }

        relocate("com.alessiodp.libby", "me.regadpole.plumbot.lib.com.alessiodp.libby")
        relocate("me.lucko.commodore", "me.regadpole.plumbot.lib.me.lucko.commodore")
        relocate("net.kyori.adventure", "me.regadpole.plumbot.lib.net.kyori.adventure")

        minimize()

        archiveBaseName.set("PlumBot-Bukkit")
        archiveVersion.set("")
        archiveClassifier.set("")
//        archiveFile.get().asFile.copyTo(File("${rootProject.buildDir}/libs/PlumBot-Bukkit.jar"), overwrite = true)
    }
    
    build {
        dependsOn(shadowJar)
    }
}