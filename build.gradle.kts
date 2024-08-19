import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.11"
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
}

subprojects {
    apply<JavaPlugin>()
    apply(plugin = "io.izzel.taboolib")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    taboolib {
        env {
            // 安装模块
            install(UNIVERSAL, DATABASE, METRICS)
            install(BUKKIT_ALL)
            install(VELOCITY)
        }
        version { taboolib = "6.1.2-beta10" }
    }

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        taboo(files("libs/OneBot-Client-0.4.1.jar"))
        taboo("com.github.SNWCreations:KookBC:0.27.4")
        compileOnly("ink.ptms.core:v12004:12004:mapped")
        compileOnly("ink.ptms.core:v12004:12004:universal")
        compileOnly(kotlin("stdlib"))
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }
}
