plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
    kotlin("kapt")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":adapter:onebot"))

    compileOnly(libs.velocityApi)
    kapt(libs.velocityApi)

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.libbyVelocity)
}

tasks {
    shadowJar {
        archiveBaseName.set("PlumBot-Velocity")
        archiveVersion.set("")
    }
}
