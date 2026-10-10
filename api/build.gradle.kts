plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(libs.adventureApi)
    api(libs.kotlinxCoroutines)
    api(libs.kotlinxDatetime)
}

/**
 * API 契约检测器（纯函数，便于用负例自检）。
 *
 * 规则：`:api` 下每个 Kotlin 源码文件都必须带公开 API 注解
 * （`@PublicApi` / `@StableApi` / `@ExperimentalApi`），以下两处为历史豁免：
 * - `fun interface PlatformTaskHandle`：平台任务句柄，跨模块实现由平台提供；
 * - `val Int.ticks`：单位扩展属性，随接口一起暴露。
 *
 * @return 违规描述列表（空表示通过）
 */
fun apiContractViolations(text: String, relPath: String): List<String> {
    val publicApiRe = Regex("""@(?:PublicApi|StableApi|ExperimentalApi)\b""")
    // 先剥离注释：否则"被注释掉的注解"会被当成真注解，契约校验形同虚设
    val code = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//.*"""), "")
    if (publicApiRe.containsMatchIn(code)) return emptyList()
    if (code.contains("fun interface PlatformTaskHandle")) return emptyList()
    if (code.contains("val Int.ticks")) return emptyList()
    return listOf("$relPath: missing public API annotation (@PublicApi / @StableApi / @ExperimentalApi)")
}

tasks.register("checkApiContract") {
    group = "verification"
    description = "Validate public contract annotations on :api module."

    val srcDir = file("src/main/kotlin")
    val selfTest = providers.gradleProperty("apiContractSelfTest").map { it.toBoolean() }.getOrElse(false)

    doLast {
        if (selfTest) {
            // 负例自检：检测器的每条判定分支都必须被覆盖，否则"漏报"会让契约静默失效。
            // 运行方式：gradlew :api:checkApiContract -PapiContractSelfTest=true
            val negatives = listOf(
                "未加注解的类型" to "package me.regadpole.plumbot.api\n\nclass Foo",
                "注解只出现在行注释里" to "package x\n\n// @PublicApi\nclass Foo",
                "注解只出现在块注释里" to "package x\n\n/* @PublicApi */\ninterface Bar",
                "只有普通 KDoc 的实现类" to "package x\n\n/** 文档 */\ninterface Baz",
            )
            val positives = listOf(
                "@PublicApi 注解" to "package x\n\n@PublicApi\ninterface Foo",
                "@ExperimentalApi 注解" to "package x\n\n@ExperimentalApi\nclass Bar",
                "PlatformTaskHandle 豁免" to "package x\n\nfun interface PlatformTaskHandle { fun cancel() }",
                "val Int.ticks 豁免" to "package x\n\nval Int.ticks: Long get() = this.toLong()",
            )

            val failures = mutableListOf<String>()
            negatives.forEach { (name, text) ->
                if (apiContractViolations(text, "SelfTest.kt").isEmpty()) {
                    failures += "漏报：$name 应当被判为违规"
                }
            }
            positives.forEach { (name, text) ->
                if (apiContractViolations(text, "SelfTest.kt").isNotEmpty()) {
                    failures += "误报：$name 不应被判为违规"
                }
            }
            if (failures.isNotEmpty()) {
                throw GradleException(
                    "checkApiContract 自检失败（${failures.size} 项）：\n" + failures.joinToString("\n") { "  - $it" }
                )
            }
            println("checkApiContract: 自检通过（负例 ${negatives.size} 项全部检出，正例 ${positives.size} 项无误报）。")
            return@doLast
        }

        val violations = mutableListOf<String>()
        srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                violations += apiContractViolations(f.readText(), f.relativeTo(projectDir).path)
            }

        if (violations.isNotEmpty()) {
            throw GradleException("checkApiContract failed with ${violations.size} violation(s):\n" + violations.joinToString("\n") { "  - $it" })
        }
        println("checkApiContract: :api module contract verified successfully.")
    }
}

tasks.named("check") {
    dependsOn("checkApiContract")
}
