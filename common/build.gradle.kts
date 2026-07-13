plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // gson
    compileOnly(libs.gson)
    // config
    compileOnly(libs.configurateYaml)
    compileOnly(libs.configurateHocon)
    compileOnly(libs.configurateExtraKotlin)
    // cache
    // 已统一使用 common 内置的 ConcurrentHashMap 缓存，无外部缓存库依赖
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
    // logger
//    implementation("org.slf4j:slf4j-api:2.0.17")
//    implementation("org.slf4j:slf4j-jdk14:2.0.17")
//    implementation("ch.qos.logback:logback-classic:1.3.14")
    // adventure
    compileOnly(libs.adventureApi)
    compileOnly(libs.adventureTextMinimessage)
    compileOnly(libs.adventureTextSerializerLegacy)
    // libby
    implementation(libs.libbyCore)
}
