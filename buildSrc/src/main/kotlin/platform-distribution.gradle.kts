// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.gradleup.shadow")
}

val relocateRoot = "me.regadpole.plumbot.lib"

tasks.shadowJar {
    archiveClassifier.set("")

    dependencies {
        exclude(dependency("com.mojang:brigadier"))
    }

    // 第三方库共 6 条 relocate 规则，统一搬进 $relocateRoot，避免与宿主或其他插件撞包。
    // Adventure（net.kyori.*）是显式例外：既不打包也不重定位 —— Paper/Leaf、Velocity 自带 kyori，
    // 插件直接用宿主类（Component 才能跨 API 边界互通）；Spigot/Hytale 等不带 kyori 的宿主
    // 由 Libby 在启动时按原包名装入（见 KyoriSupport）。
    relocate("com.sksamuel.aedile", "$relocateRoot.com.sksamuel.aedile")
    relocate("com.github.benmanes.caffeine", "$relocateRoot.com.github.benmanes.caffeine")
    relocate("com.zaxxer.hikari", "$relocateRoot.com.zaxxer.hikari")
    relocate("com.alessiodp.libby", "$relocateRoot.com.alessiodp.libby")
    relocate("me.lucko.commodore", "$relocateRoot.me.lucko.commodore")
    relocate("org.bstats", "$relocateRoot.org.bstats")

    minimize {
        exclude(dependency("org.bstats:.*"))
    }

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}