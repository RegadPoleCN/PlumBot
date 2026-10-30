// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
    kotlin("kapt")
}

// 本模块需要生成 BuildConstants（构建版本号）；其他模块一律不生成，避免 shading 时同类相撞
extra["plumbotBuildConstants"] = true

dependencies {
    implementation(project(":common"))
    // 显式依赖：平台层需要装配渲染门户（RenderProvider）并实现 RenderLibraryLoader SPI
    implementation(project(":render"))
    implementation(project(":adapter:onebot"))
    implementation(project(":adapter:miraimc"))

    compileOnly(libs.velocityApi)
    // Adventure 不再由 :api 传递暴露；Velocity 宿主自带 kyori，这里只编译期可见
    compileOnly(libs.adventureApi)
    compileOnly(libs.adventureTextMinimessage)
    compileOnly(libs.miraimcVelocity)
    kapt(libs.velocityApi)

    implementation(libs.libbyVelocity)
    implementation(libs.bstatsVelocity)
}

tasks {
    shadowJar {
        archiveBaseName.set("PlumBot-Velocity")
        archiveVersion.set("")
    }
}
