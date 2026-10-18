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
    // Hytale 宿主不带 kyori：运行期由 KyoriSupport 检测并交给 Libby 装载
    compileOnly(libs.adventureApi)
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
