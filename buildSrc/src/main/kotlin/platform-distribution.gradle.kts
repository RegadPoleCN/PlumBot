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
    // Adventure（net.kyori.*）不再随产物打包，也不再重定位：
    // Paper/Leaf、Velocity 自带 kyori，插件直接使用宿主类（组件才能跨 API 边界互通）；
    // Spigot/Hytale 这类不带 kyori 的宿主由 Libby 在启动时装入（见 KyoriSupport）。
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