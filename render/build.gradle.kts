plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":api"))

    // 显式声明：渲染层直接使用协程（Mutex/withContext/Channel/超时），
    // 不再依赖 :api 的 api(...) 传递导出。
    implementation(libs.kotlinxCoroutines)

    compileOnly(libs.playwright)
    testImplementation(kotlin("test"))
}
