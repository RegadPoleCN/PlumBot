// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(25)
}

val generateBuildConstants = tasks.register("generateBuildConstants") {
    val outputDir = layout.buildDirectory.dir("generated/sources/buildConstants/kotlin")
    val projectVersion = rootProject.version.toString()
    val constantsPackage = "me.regadpole.plumbot.internal"
    inputs.property("version", projectVersion)
    inputs.property("constantsPackage", constantsPackage)
    outputs.dir(outputDir)

    doLast {
        val root = outputDir.get().asFile
        root.deleteRecursively()
        val dir = root.resolve(constantsPackage.replace('.', '/'))
        dir.mkdirs()
        dir.resolve("BuildConstants.kt").writeText(
            """
            package $constantsPackage

            object BuildConstants {
                const val VERSION: String = "$projectVersion"
                const val NAME: String = "PlumBot"
            }
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.named("main") {
    // BuildConstants **按需生成**：它含构建版本号，而旧实现对每个模块都生成一份，
    // 于是 api/render/common/bukkit/velocity/hytale/onebot/miraimc 各有一份同名的
    // me.regadpole.plumbot.internal.BuildConstants，shading 时同类相撞、且 7 份是纯死重量。
    // 需要的模块在自身 build.gradle.kts 中声明：extra["plumbotBuildConstants"] = true
    //
    // 必须用 provider **延迟求值**：模块的 extra[...] 在 plugins {} 块之后才赋值，
    // 而约定插件是在 plugins {} 期间应用的，此处立即求值会永远看到 false（kapt 会因此编译失败）。
    kotlin.srcDir(
        provider {
            if (project.hasProperty("plumbotBuildConstants")) listOf(generateBuildConstants) else emptyList()
        }
    )
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // 测试临时目录落地到 build/ 内，不再写系统 %TEMP%。
    //
    // 背景：测试里裸用 `Files.createTempDirectory(...)` 且从不删除（`deleteOnExit`
    // 对非空目录无效），每次 `gradlew build` 都在 %TEMP% 里长一批 `plumbot-*`
    // —— 实测累积到 1300 个目录。逐处改成 @TempDir 需要改动 18 个测试类、
    // 且没有共享的 testFixtures，容易漏；在本文件改一次即可覆盖全部现有与新增测试。
    //
    // java.io.tmpdir 必须显式指定：Gradle 不会替测试 JVM 覆盖它（正因如此原先漏到 %TEMP%）。
    val testTempDir = layout.buildDirectory.dir("tmp/test-jvm").get().asFile
    systemProperty("java.io.tmpdir", testTempDir.absolutePath)

    // 目录必须先存在：java.io.tmpdir 不存在时 Files.createTempDirectory 直接抛 NoSuchFileException。
    doFirst { testTempDir.mkdirs() }
    // 跑完就清掉。删不掉不算失败（Windows 上可能有句柄未释放），目录已在 build/ 内，
    // 下一次 clean 或构建都会重来。
    doLast { runCatching { testTempDir.deleteRecursively() } }

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
