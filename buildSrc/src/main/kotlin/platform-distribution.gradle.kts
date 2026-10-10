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

    relocate("com.sksamuel.aedile", "$relocateRoot.com.sksamuel.aedile")
    relocate("com.github.benmanes.caffeine", "$relocateRoot.com.github.benmanes.caffeine")
    relocate("com.zaxxer.hikari", "$relocateRoot.com.zaxxer.hikari")
    relocate("com.alessiodp.libby", "$relocateRoot.com.alessiodp.libby")
    relocate("me.lucko.commodore", "$relocateRoot.me.lucko.commodore")
    relocate("net.kyori.adventure", "$relocateRoot.net.kyori.adventure")
    // Adventure 的兄弟库：它们随产物一起打包（Bukkit 31+27、Hytale 4、Velocity 27 个条目），
    // 若不重定位就会与宿主自带的同名类冲突（版本不一致时 `NoSuchMethodError` 且难排查）。
    // 决策 D7：**只补这两个 kyori 包**；Hytale 侧 `com.google.gson` 明确不补（接受宿主自带）。
    relocate("net.kyori.option", "$relocateRoot.net.kyori.option")
    relocate("net.kyori.examination", "$relocateRoot.net.kyori.examination")
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