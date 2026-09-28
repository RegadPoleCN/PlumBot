plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":adapter:onebot"))
    implementation(project(":adapter:miraimc"))
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
    // miraimc
    compileOnly("io.github.dreamvoid:MiraiMC-Bukkit:1.9")
}

tasks {
    processResources {
        withGroovyBuilder {
            "filesMatching"("plugin.yml") {
                "expand"("version" to rootProject.version.toString())
            }
        }
    }
    
    shadowJar {
        archiveBaseName.set("PlumBot-Bukkit")
        archiveVersion.set("")
    }
}
