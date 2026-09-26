plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api(libs.adventureApi)
    api(libs.kotlinxCoroutines)
}
