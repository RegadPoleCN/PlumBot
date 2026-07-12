plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    // miraimc
    compileOnly("io.github.dreamvoid:MiraiMC-Integration:1.9")
    // cache
    // 已统一使用 common 内置的 ConcurrentHashMap 缓存，无外部缓存库依赖
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    implementation(libs.bundles.kotlinxEcosystem)
    // adventure
    compileOnly("net.kyori:adventure-api:4.21.0")
    testImplementation(kotlin("test"))
}
