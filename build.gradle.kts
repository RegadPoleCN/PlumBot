import io.izzel.taboolib.gradle.BUKKIT
import io.izzel.taboolib.gradle.UNIVERSAL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.11"
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
}

taboolib {
    env {
        // 安装模块
        install(UNIVERSAL, BUKKIT)
    }
    version { taboolib = "6.1.2-beta10" }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.nova-committee.cn/releases")
    }
    maven {
        url = uri("https://jitpack.io")
    }

}

dependencies {
    taboo("cn.evole.onebot:OneBot-Client:0.4.1")
    taboo("com.github.SNWCreations:KookBC:0.27.4")
//    compileOnly("cn.evole.onebot:OneBot-Client:0.4.1")
//    compileOnly("com.github.SNWCreations:KookBC:0.27.4")
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
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

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
