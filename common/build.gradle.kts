plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // gson
    compileOnly("com.google.code.gson:gson:2.11.0")
    // config
    compileOnly("org.spongepowered:configurate-yaml:4.2.0")
    compileOnly("org.spongepowered:configurate-hocon:4.2.0")
    compileOnly("org.spongepowered:configurate-extra-kotlin:4.2.0")
    // cache
//    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    compileOnly("com.sksamuel.aedile:aedile-core:2.0.3")
    // OneBot
    compileOnly("com.github.alazeprt:AOneBot:1.0.11-beta")
    // database
    compileOnly("com.github.RegadPoleCN:taboolib-database:1.0.2")
    compileOnly("com.mysql:mysql-connector-j:8.2.0")
    compileOnly("org.xerial:sqlite-jdbc:3.42.0.0")
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
    compileOnly("net.kyori:adventure-api:4.21.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.21.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.21.0")
    // libby
    implementation("com.alessiodp.libby:libby-core:2.0.0-SNAPSHOT")
    // miraimc
    compileOnly("io.github.dreamvoid:MiraiMC-Integration:1.9")
}