plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.gson)
    compileOnly(libs.aonebot)
    testImplementation(kotlin("test"))
}
