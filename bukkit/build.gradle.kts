plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    // 显式依赖：平台层需要装配渲染门户（RenderProvider）并实现 RenderLibraryLoader SPI
    implementation(project(":render"))
    implementation(project(":adapter:onebot"))
    implementation(project(":adapter:miraimc"))
    testImplementation(kotlin("test"))

    // bukkit
    compileOnly("org.spigotmc:spigot-api:1.13-R0.1-SNAPSHOT")
    implementation(libs.adventureBukkit)
    implementation(libs.libbyBukkit)
    compileOnly(libs.miraimcBukkit)
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
