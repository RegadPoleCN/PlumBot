/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
