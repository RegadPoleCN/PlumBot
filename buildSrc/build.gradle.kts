plugins {
    // `kotlin-dsl`：约定插件放在 src/main/kotlin/*.gradle.kts，由各模块的 build.gradle.kts 按需应用。
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    // 约定插件要应用 Kotlin 插件，因此构建逻辑自身必须依赖 Kotlin Gradle 插件。
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.shadowGradlePlugin)
}
