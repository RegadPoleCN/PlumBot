// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
    // 引擎指纹的回归用例必须用**真实** YAML 节点：手写 FakeConfig 不会像 configurate
    // 的 `boolean`/`int`/`double` 属性那样把默认值写回树，正是这一点让 B1 回归漏网。
    testImplementation(libs.configurateYaml)
    testImplementation(kotlin("test"))
}
