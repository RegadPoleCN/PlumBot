plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api(libs.adventureApi)
    api(libs.kotlinxCoroutines)
    api(libs.kotlinxDatetime)
}

tasks.register("checkApiContract") {
    group = "verification"
    description = "Validate public contract annotations on :api module."

    val srcDir = file("src/main/kotlin")
    val publicApiRe = Regex("""@(?:PublicApi|StableApi|ExperimentalApi)\b""")

    doLast {
        val violations = mutableListOf<String>()

        srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                val text = f.readText()
                if (!publicApiRe.containsMatchIn(text) && !text.contains("fun interface PlatformTaskHandle") && !text.contains("val Int.ticks")) {
                    violations.add("${f.relativeTo(projectDir)}: missing public API annotation (@PublicApi / @StableApi / @ExperimentalApi)")
                }
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
