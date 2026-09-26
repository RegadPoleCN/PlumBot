plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(project(":api"))
    // gson
    compileOnly(libs.gson)
    // config
    compileOnly(libs.configurateYaml)
    compileOnly(libs.configurateExtraKotlin)
    // cache
    compileOnly(libs.aedile)
    // database
    compileOnly(libs.taboolibDatabase)
    compileOnly(libs.hikaricp)
    compileOnly(libs.mysql)
    compileOnly(libs.sqlite)
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    implementation(libs.bundles.kotlinxEcosystem)
    // adventure
    compileOnly(libs.adventureApi)
    compileOnly(libs.adventureTextMinimessage)
    compileOnly(libs.adventureTextSerializerLegacy)
    // libby
    implementation(libs.libbyCore)
}
