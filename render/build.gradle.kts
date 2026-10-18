plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":api"))

    // 显式声明：渲染层直接使用协程（Mutex/withContext/Channel/超时），
    // 不再依赖 :api 的 api(...) 传递导出。
    implementation(libs.kotlinxCoroutines)

    compileOnly(libs.playwright)
    // 测试里的 FakeContext 需要实现 PlatformContext.sendMessage(Component)，
    // 主代码的 Adventure 由各宿主提供，测试类路径需单独声明。
    testImplementation(libs.adventureApi)
    testImplementation(kotlin("test"))
}
