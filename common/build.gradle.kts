plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // gson
    compileOnly(libs.gson)
    // config
    compileOnly(libs.configurateYaml)
    compileOnly(libs.configurateHocon)
    compileOnly(libs.configurateExtraKotlin)
    // cache
    compileOnly(libs.aedile)
    // database
    compileOnly(libs.taboolibDatabase)
    compileOnly(libs.hikaricp)
    compileOnly(libs.mysql)
    compileOnly(libs.sqlite)
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    implementation(libs.bundles.kotlinxEcosystem)
    // adventure
    compileOnly(libs.adventureApi)
    compileOnly(libs.adventureTextMinimessage)
    compileOnly(libs.adventureTextSerializerLegacy)
    // libby
    implementation(libs.libbyCore)
}

/**
 * Verifies that:
 *  1. Files under `common/.../internal/` do not carry any `@PublicApi` /
 *     `@StableApi` / `@ExperimentalApi` annotation.
 *  2. The well-known public-API types in `common` carry at least one of those
 *     annotations.
 *
 * Implemented as a plain Gradle task so we don't pull detekt or ktlint as a
 * hard dependency. Failures are formatted with file:line for easy fixing.
 */
tasks.register("checkApiContract") {
    group = "verification"
    description = "Validate @PublicApi / @StableApi contract on common modules."

    val srcDir = file("src/main/kotlin")
    val internalDir = file("src/main/kotlin/me/regadpole/plumbot/internal")
    val publicApiRe = Regex("""@(?:PublicApi|StableApi|ExperimentalApi)\b""")

    doLast {
        val violations = mutableListOf<String>()

        // (1) Forbidden in `internal/`.
        if (internalDir.exists()) {
            internalDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { f ->
                    val text = f.readText()
                    publicApiRe.findAll(text).forEach { m ->
                        violations.add("${f.path}:${lineOf(text, m.range.first)}: public-API annotation on internal type")
                    }
                }
        }

        // (2) Required on public types.
        val requiredApis = listOf(
            "me/regadpole/plumbot/api/PlumBotAPI.kt",
            "me/regadpole/plumbot/PlumBot.kt",
            "me/regadpole/plumbot/api/bot/IBot.kt",
            "me/regadpole/plumbot/api/Plugin.kt",
            "me/regadpole/plumbot/api/PublicApi.kt",
            "me/regadpole/plumbot/api/ListenerHandle.kt",
            "me/regadpole/plumbot/api/event/BotEvents.kt",
            "me/regadpole/plumbot/api/config/Messages.kt",
            "me/regadpole/plumbot/bot/BotFactory.kt",
            "me/regadpole/plumbot/bot/BotAdapterMetadata.kt",
            "me/regadpole/plumbot/bot/BotCapability.kt",
            "me/regadpole/plumbot/bot/BotImpl.kt",
            "me/regadpole/plumbot/bot/AbstractBotAdapter.kt",
            "me/regadpole/plumbot/bot/BotRegistry.kt",
            "me/regadpole/plumbot/bot/BotProvider.kt",
            "me/regadpole/plumbot/bot/BotExtensionRegistry.kt",
            "me/regadpole/plumbot/platform/PlatformContext.kt",
            "me/regadpole/plumbot/platform/PlatformLogger.kt",
            "me/regadpole/plumbot/platform/PlatformScheduler.kt",
            "me/regadpole/plumbot/platform/PlatformMessenger.kt",
            "me/regadpole/plumbot/platform/PlatformPlayerService.kt",
            "me/regadpole/plumbot/platform/PlatformTaskHandle.kt",
            "me/regadpole/plumbot/platform/PlatformType.kt",
            "me/regadpole/plumbot/task/TaskProvider.kt",
            "me/regadpole/plumbot/task/TaskProviderImpl.kt",
        )
        requiredApis.forEach { rel ->
            val f = file("$srcDir/$rel")
            if (!f.exists()) {
                violations.add("$rel: missing (was a public type deleted?)")
                return@forEach
            }
            val text = f.readText()
            if (!publicApiRe.containsMatchIn(text)) {
                violations.add("$rel: missing public-API annotation (no @PublicApi / @StableApi / @ExperimentalApi)")
            }
        }

        if (violations.isNotEmpty()) {
            val msg = buildString {
                appendLine("checkApiContract failed with ${violations.size} violation(s):")
                violations.forEach { appendLine("  - $it") }
            }
            throw GradleException(msg)
        }
        println("checkApiContract: ${requiredApis.size} public types verified, internal types contain no public-API annotations.")
    }
}

fun lineOf(text: String, offset: Int): Int {
    var line = 1
    var i = 0
    while (i < offset && i < text.length) {
        if (text[i] == '\n') line++
        i++
    }
    return line
}
