import org.gradle.kotlin.dsl.ext

plugins {
    `maven-publish`
    java
    kotlin("jvm") version "2.0.20-Beta1"
    kotlin("kapt") version "2.0.20-Beta1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.regadpole"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://jitpack.io") {
        name = "jitpack.io"
    }
    maven("https://repo.opencollab.dev/main/") {
        name = "opencollab-snapshot"
    }
    maven ("https://nexus.lucko.me/repository/maven-hytale/")
}

dependencies {
    // hytale
    compileOnly("com.hypixel.hytale:HytaleServer:2026.01.13-dcad8778f-SNAPSHOT")
    // cache
//    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("com.sksamuel.aedile:aedile-core:2.0.3")
    // OneBot
    implementation("com.github.alazeprt:AOneBot:1.0.7-beta")
    // database
    implementation("com.github.RegadPoleCN:taboolib-database:1.0.2")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
}

val targetJavaVersion = 17
kotlin {
    jvmToolchain(targetJavaVersion)
    compilerOptions {
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }

}

tasks.processResources {
    filesMatching('manifest.json') {
        "version" to  project.version
    }
}

tasks.build {
    dependsOn("shadowJar")
}
tasks.jar {
    archiveBaseName.set(rootProject.name)
    archiveClassifier.set("sources")
}
tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set(rootProject.name)
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-XDenableSunApiLintControl"))
}
publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        artifact(tasks["kotlinSourcesJar"])
    }
}