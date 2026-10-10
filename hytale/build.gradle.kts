plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    // 显式依赖：平台层需要装配渲染门户（RenderProvider）并实现 RenderLibraryLoader SPI
    implementation(project(":render"))
    implementation(project(":adapter:onebot"))
    testImplementation(kotlin("test"))

    // hytale
    compileOnly("com.hypixel.hytale:Server:+")
    implementation(libs.adventureHytale)
    implementation(libs.libbyHytale)
    implementation(libs.bstatsHytale)
}

tasks {
    processResources {
        withGroovyBuilder {
            "filesMatching"("manifest.json") {
                "expand"("version" to rootProject.version.toString())
            }
        }
    }
    
    shadowJar {
        archiveBaseName.set("PlumBot-Hytale")
        archiveVersion.set("")
    }
}
