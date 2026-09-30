plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":adapter:onebot"))
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
    // hytale
    compileOnly("com.hypixel.hytale:Server:+")
    // libby
    implementation(libs.libbyHytale)
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
