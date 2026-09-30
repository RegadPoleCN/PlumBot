package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.gradleup.shadow")
}

tasks.shadowJar {
    archiveClassifier.set("")

    dependencies {
        exclude(dependency("com.mojang:brigadier"))
    }

    relocate("com.sksamuel.aedile", "me.regadpole.plumbot.lib.com.sksamuel.aedile")
    relocate("com.github.benmanes.caffeine", "me.regadpole.plumbot.lib.com.github.benmanes.caffeine")
    relocate("com.zaxxer.hikari", "me.regadpole.plumbot.lib.com.zaxxer.hikari")
    relocate("com.alessiodp.libby", "me.regadpole.plumbot.lib.com.alessiodp.libby")
    relocate("me.lucko.commodore", "me.regadpole.plumbot.lib.me.lucko.commodore")
    relocate("net.kyori.adventure", "me.regadpole.plumbot.lib.net.kyori.adventure")
    relocate("org.bstats", project.group.toString())

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