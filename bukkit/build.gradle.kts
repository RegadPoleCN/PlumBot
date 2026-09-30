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
//    implementation(libs.adventureBukkit)
    // libby
    implementation(libs.libbyBukkit)
    // miraimc
    compileOnly(libs.miraimcBukkit)
    // bstats
    implementation(libs.bstatsBukkit)
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
