plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.miraimcIntegration)
    compileOnly(libs.gson)
    testImplementation(kotlin("test"))
}
