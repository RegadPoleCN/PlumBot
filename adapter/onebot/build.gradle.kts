plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    // gson
    compileOnly(libs.gson)
    // OneBot
    compileOnly(libs.aonebot)
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    implementation(libs.bundles.kotlinxEcosystem)
    // adventure
    compileOnly(libs.adventureApi)
    testImplementation(kotlin("test"))
}
