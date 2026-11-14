// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.gson)
    compileOnly(libs.aonebot)
    testImplementation(kotlin("test"))
    // 单测要真实构造 WebsocketBotClient（gson 静态初始化）并实现 PlatformContext（adventure Component 签名）。
    testImplementation(libs.aonebot)
    testImplementation(libs.gson)
    testImplementation(libs.adventureApi)
    // 构造 AbstractBotAdapter 子类会初始化 DefaultBotCache，其缓存层依赖 aedile/caffeine（生产为 compileOnly）。
    testImplementation(libs.aedile)
}
