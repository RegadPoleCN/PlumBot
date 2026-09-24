plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    // miraimc
    compileOnly(libs.miraimcIntegration)
    compileOnly(libs.gson)
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    implementation(libs.bundles.kotlinxEcosystem)
    // adventure
    compileOnly(libs.adventureApi)
    testImplementation(kotlin("test"))
}
