import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "me.regadpole"

/**
 * 边界规则的**逐行判定**（纯函数，便于用负例自检）。
 *
 * @param forbidRenderImport 适配层不得出现任何 `:render` 导入
 * @param forbidInternals 不得出现 `:render` 内部类型名（**包括注释中的提及**）
 * @return 违规描述列表（空表示通过）
 */
fun boundaryLineViolations(
    rel: String,
    lines: List<String>,
    forbidRenderImport: Boolean,
    forbidInternals: Boolean,
    renderInternals: List<String>
): List<String> {
    val found = mutableListOf<String>()
    lines.forEachIndexed { index, line ->
        val lineNo = index + 1
        if (forbidRenderImport && line.contains("import me.regadpole.plumbot.render")) {
            found += "$rel:$lineNo 适配层不得依赖 :render —— $line"
        }
        if (forbidInternals) {
            renderInternals.forEach { internal ->
                if (Regex("\\b$internal\\b").containsMatchIn(line)) {
                    found += "$rel:$lineNo 跨模块引用了 :render 内部类型 $internal —— $line"
                }
            }
        }
    }
    return found
}

/**
 * 协议清单的**路由一致性判定**（纯函数，便于用负例自检）。
 *
 * 规则：① 清单 route 集合与 `RenderRoute` 枚举双向一致；
 * ② 每条 route 必须声明 `legacyGate` / `defaultEngine` / 非空 `payload`。
 */
fun protocolRouteViolations(
    manifestRoutes: Map<String, Any?>,
    enumRoutes: Set<String>
): List<String> {
    val violations = mutableListOf<String>()

    if (enumRoutes != manifestRoutes.keys) {
        (enumRoutes - manifestRoutes.keys).forEach { violations += "枚举中存在但清单缺失的 route: $it" }
        (manifestRoutes.keys - enumRoutes).forEach { violations += "清单中存在但枚举缺失的 route: $it" }
    }

    manifestRoutes.forEach { (name, raw) ->
        val entry = raw as? Map<*, *> ?: run {
            violations += "route '$name' 的配置不是对象"
            return@forEach
        }
        if (!entry.containsKey("legacyGate")) violations += "route '$name' 缺少 legacyGate"
        if (!entry.containsKey("defaultEngine")) violations += "route '$name' 缺少 defaultEngine"
        val payload = entry["payload"]
        if (payload !is Map<*, *> || payload.isEmpty()) {
            violations += "route '$name' 缺少非空 payload 声明"
        }
    }

    return violations
}

val copyDist = tasks.register<Copy>("dist") {    group = "distribution"
    description = "Collects distribution ShadowJars from all platform modules into /dist"
    destinationDir = file("dist")
}

/**
 * 模块边界机械校验（基线 §3.6.7 层 3）。
 *
 * 1. **适配层零依赖 `:render`** —— 适配层只应"把字节发出去"，任何渲染引用都属越级；
 * 2. **`:render` 内部类型不得被跨模块引用** —— 门户是唯一入口；
 * 3. **`:api` 不得依赖渲染实现**。
 *
 * 与 `:api` 的 `checkApiContract` 互补，防止门户纪律回退。
 */
val checkRenderBoundaries = tasks.register("checkRenderBoundaries") {
    group = "verification"
    description = "Validates module boundaries around :render (portal-only access)."

    val rootDir = rootProject.projectDir
    doLast {
        val renderInternals = listOf(
            "TextToImg",
            "ThemeManager",
            "AwtImageRenderer",
            "PlaywrightImageRenderer",
            "PayloadGovernor",
            "RenderSecurity",
            "AvatarComposer",
            "ImageRenderManager",
            "ImageRenderer"
        )
        val violations = mutableListOf<String>()

        // 负例自检：规则引擎的每条判定都必须真的能拦住违规，否则"全绿"毫无意义。
        // 运行方式：gradlew checkRenderBoundaries -PrenderBoundarySelfTest=true
        if (providers.gradleProperty("renderBoundarySelfTest").map { it.toBoolean() }.getOrElse(false)) {
            val failures = mutableListOf<String>()

            val adapterImport = boundaryLineViolations(
                "adapter/x/A.kt", listOf("import me.regadpole.plumbot.render.RenderPortal"), true, true, renderInternals
            )
            if (adapterImport.isEmpty()) failures += "漏报：适配层 import :render 未被拦下"

            val internalInComment = boundaryLineViolations(
                "common/Y.kt", listOf("// 这里提到 ThemeManager 也算违规"), false, true, renderInternals
            )
            if (internalInComment.isEmpty()) failures += "漏报：注释中的内部类型提及未被拦下"

            val portalOnly = boundaryLineViolations(
                "common/Z.kt",
                listOf("import me.regadpole.plumbot.render.RenderPortal", "val portal: RenderPortal? = null"),
                false, true, renderInternals
            )
            if (portalOnly.isNotEmpty()) failures += "误报：门户契约类型被当成内部类型：$portalOnly"

            if (failures.isNotEmpty()) {
                throw GradleException(
                    "checkRenderBoundaries 自检失败（${failures.size} 项）：\n" + failures.joinToString("\n") { "  - $it" }
                )
            }
            println("checkRenderBoundaries: 自检通过（负例 2 项全部检出，正例 1 项无误报）。")
            return@doLast
        }

        fun scanModule(moduleName: String, forbidRenderImport: Boolean, forbidInternals: Boolean) {
            val moduleDir = File(rootDir, moduleName)
            if (!moduleDir.isDirectory) return
            moduleDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.path.contains("${File.separator}build${File.separator}") }
                .forEach { file ->
                    val rel = file.relativeTo(rootDir).path
                    violations += boundaryLineViolations(
                        rel = rel,
                        lines = file.readLines(),
                        forbidRenderImport = forbidRenderImport,
                        forbidInternals = forbidInternals,
                        renderInternals = renderInternals
                    )
                }
        }

        // 适配层：零依赖 :render
        scanModule("adapter", forbidRenderImport = true, forbidInternals = true)
        // 其余模块：只能使用门户契约，不得触碰内部类型
        listOf("api", "common", "bukkit", "velocity", "hytale").forEach { module ->
            scanModule(module, forbidRenderImport = false, forbidInternals = true)
        }

        // 规则 4：:render 中任何非 internal 的顶层声明，其所在文件必须带契约注解
        val contractRe = Regex("""@(?:PublicApi|StableApi|ExperimentalApi)\b""")
        val topLevelDeclRe = Regex(
            """^(?:public\s+)?(?:class|interface|object|enum class|data class|sealed interface|sealed class|fun|val|const val)\s"""
        )
        val renderSrc = File(rootDir, "render/src/main/kotlin")
        if (renderSrc.isDirectory) {
            renderSrc.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val rel = file.relativeTo(rootDir).path
                    val lines = file.readLines()
                    val hasContract = lines.any { contractRe.containsMatchIn(it) }
                    lines.forEachIndexed { index, line ->
                        if (topLevelDeclRe.containsMatchIn(line) && !line.startsWith("internal") && !hasContract) {
                            violations += "$rel:${index + 1} :render 的公开声明缺少契约注解（@PublicApi/@StableApi）—— $line"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkRenderBoundaries failed with ${violations.size} violation(s):\n" +
                    violations.joinToString("\n") { "  - $it" }
            )
        }
        println("checkRenderBoundaries: render portal boundaries verified successfully.")
    }
}

/**
 * 极简的缩进感知 YAML 取值（仅用于构建期校验，不追求通用性）。
 *
 * 按层级向下定位 `path` 指向的块，返回该块内**一层**的 `键 -> 值` 映射。
 * 之所以不用 SnakeYAML：它不在 Gradle 构建脚本的 classpath 上，
 * 而离线构建时也无法临时拉取依赖。
 */
fun yamlBlock(file: File, path: List<String>): Map<String, String>? {
    val lines = file.readLines()
    var index = 0
    var indent = -1

    for (segment in path) {
        var found = -1
        var foundIndent = -1
        while (index < lines.size) {
            val raw = lines[index]
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) { index++; continue }
            val currentIndent = raw.indexOfFirst { it != ' ' }
            if (indent >= 0 && currentIndent <= indent) break // 跳出当前块
            if (trimmed == "$segment:" || trimmed.startsWith("$segment:")) {
                found = index
                foundIndent = currentIndent
                break
            }
            index++
        }
        if (found < 0) return null
        indent = foundIndent
        index = found + 1
    }

    val result = mutableMapOf<String, String>()
    while (index < lines.size) {
        val raw = lines[index]
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) { index++; continue }
        val currentIndent = raw.indexOfFirst { it != ' ' }
        if (currentIndent <= indent) break
        val colon = trimmed.indexOf(':')
        if (colon > 0) {
            val key = trimmed.substring(0, colon).trim()
            val value = trimmed.substring(colon + 1).trim().trim('"', '\'')
            if (value.isNotEmpty()) result[key] = value
        }
        index++
    }
    return result
}

/**
 * 渲染协议清单一致性校验（基线 §4.5 轻档）。
 *
 * 单一真源为 `render/src/main/resources/render-protocol.json`。本任务保证：
 * 1. 清单中的 route 集合与 Kotlin 的 [RenderRoute] 枚举**完全一致**（双向，防止任一侧漂移）；
 * 2. 每条 route 都声明了 `legacyGate` / `defaultEngine` / `payload`；
 * 3. 若前端 SDK 存在（`web-t/src/sdk/protocol.ts`），其协议版本与 route 集合必须与清单一致。
 */
val checkRenderProtocol = tasks.register("checkRenderProtocol") {
    group = "verification"
    description = "Validates render-protocol.json against the Kotlin route registry and the TS SDK."

    val rootDir = rootProject.projectDir
    doLast {
        val manifestFile = File(rootDir, "render/src/main/resources/render-protocol.json")
        if (!manifestFile.isFile) throw GradleException("缺少协议清单: ${manifestFile.path}")

        @Suppress("UNCHECKED_CAST")
        val manifest = groovy.json.JsonSlurper().parse(manifestFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val routeEntries = manifest["routes"] as? Map<String, Any?>
            ?: throw GradleException("render-protocol.json 缺少 routes 节点")

        val violations = mutableListOf<String>()

        // 负例自检：路由一致性规则必须真的能拦住违规。
        // 运行方式：gradlew checkRenderProtocol -PrenderProtocolSelfTest=true
        if (providers.gradleProperty("renderProtocolSelfTest").map { it.toBoolean() }.getOrElse(false)) {
            val failures = mutableListOf<String>()
            val okEntry = mapOf<String, Any?>(
                "legacyGate" to "status", "defaultEngine" to "awt", "payload" to mapOf("a" to 1)
            )

            val enumExtra = protocolRouteViolations(mapOf("status" to okEntry), setOf("status", "kick"))
            if (enumExtra.none { it.contains("清单缺失") }) failures += "漏报：枚举多出的 route 未被拦下"

            val manifestExtra = protocolRouteViolations(mapOf("status" to okEntry, "ghost" to okEntry), setOf("status"))
            if (manifestExtra.none { it.contains("枚举缺失") }) failures += "漏报：清单多出的 route 未被拦下"

            val missingFields = protocolRouteViolations(
                mapOf("status" to mapOf<String, Any?>("legacyGate" to null, "payload" to emptyMap<String, Any?>())),
                setOf("status")
            )
            if (missingFields.none { it.contains("defaultEngine") }) failures += "漏报：缺少 defaultEngine 未被拦下"
            if (missingFields.none { it.contains("非空 payload") }) failures += "漏报：空 payload 未被拦下"

            val consistent = protocolRouteViolations(mapOf("status" to okEntry), setOf("status"))
            if (consistent.isNotEmpty()) failures += "误报：一致输入被判违规：$consistent"

            if (failures.isNotEmpty()) {
                throw GradleException(
                    "checkRenderProtocol 自检失败（${failures.size} 项）：\n" + failures.joinToString("\n") { "  - $it" }
                )
            }
            println("checkRenderProtocol: 自检通过（负例 3 项全部检出，正例 1 项无误报）。")
            return@doLast
        }

        // 1. 与 Kotlin 枚举双向一致
        val enumSource = File(
            rootDir,
            "render/src/main/kotlin/me/regadpole/plumbot/render/RenderPortal.kt"
        ).readText()
        val enumRoutes = Regex("""^\s*([A-Z][A-Z0-9_]*)\("([A-Za-z0-9_]+)",\s*("([A-Za-z0-9_]+)"|null)\)""", RegexOption.MULTILINE)
            .findAll(enumSource)
            .map { it.groupValues[2] }
            .toSet()

        violations += protocolRouteViolations(routeEntries, enumRoutes)

        // 3. 前端 SDK 一致性（若存在）
        val sdkProtocol = File(rootDir, "web-t/src/sdk/protocol.ts")
        if (sdkProtocol.isFile) {
            val ts = sdkProtocol.readText()
            val tsProtocol = Regex("""PROTOCOL\s*=\s*(\d+)""").find(ts)?.groupValues?.get(1)
            val expected = manifest["protocol"].toString()
            if (tsProtocol != expected) {
                violations += "前端 SDK 协议版本 ($tsProtocol) 与清单 ($expected) 不一致"
            }
            routeEntries.keys.forEach { route ->
                // 同时接受单引号与双引号字面量
                if (!ts.contains("\"$route\"") && !ts.contains("'$route'")) {
                    violations += "前端 SDK 未声明 route '$route'"
                }
            }
        }

        // 4. 前端路由路径必须与协议 route 名**逐字一致**（若模板存在）
        //
        // 这一条是补上的：此前路由写作 `/list` 而协议名是 `playerList`，
        // 插件导航到 `#/playerList` 会命中 404 → 握手判为 RouteUnsupported →
        // 该 route 的 Playwright 路径**永远静默降级**，且构建期毫无提示。
        val routerFile = File(rootDir, "web-t/src/router.ts")
        if (routerFile.isFile) {
            val routerSource = routerFile.readText()
            val declaredPaths = Regex("""path:\s*['"]([^'"]+)['"]""")
                .findAll(routerSource)
                .map { it.groupValues[1].removePrefix("/") }
                .filter { it.isNotEmpty() }
                .toSet()

            val routeNames = routeEntries.keys
            (routeNames - declaredPaths).forEach {
                violations += "前端路由表缺失 route '$it' 的视图路径（会静默降级为 awt）"
            }
            (declaredPaths - routeNames).forEach {
                violations += "前端路由表存在协议未声明的路径 '/$it'"
            }
            // 明确禁止改名的历史写法，避免再次回退
            if (declaredPaths.contains("list")) {
                violations += "前端路由表出现 '/list'：协议 route 名为 'playerList'，必须逐字一致"
            }
        }

        // 5. 文本可读性规则**四方一致**：协议清单 ↔ config.yml ↔ 引擎默认值 ↔ 运行时载体
        //
        // 这一条是补上的：此前清单与前端 SDK 都声明了底板色/描边阈值，但 AWT 引擎用的是
        // 写死的 Style.DEFAULT，配置里根本没有 text 节点 —— 三方数值只是"恰好一致"，
        // 改任何一处都不会同步，属"有声明、无消费者"的假契约。
        val manifestText = manifest["text"] as? Map<*, *>
        if (manifestText == null) {
            violations += "render-protocol.json 缺少 text 节点（文本可读性规则真源）"
        } else {
            fun normColor(raw: Any?): String? =
                raw?.toString()?.trim()?.removePrefix("#")?.uppercase()?.takeIf { it.length == 6 }

            fun normNum(raw: Any?): String? =
                raw?.toString()?.trim()?.toDoubleOrNull()?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }

            val expectBase = normColor(manifestText["base"])
            val expectColor = normColor(manifestText["color"])
            val expectThreshold = normNum(manifestText["threshold"])

            // 5.1 config.yml（无第三方依赖的缩进感知解析：feature → img → text）
            val configYml = File(rootDir, "common/src/main/resources/config.yml")
            if (configYml.isFile) {
                val textNode = yamlBlock(configYml, listOf("feature", "img", "text"))
                if (textNode == null) {
                    violations += "config.yml 缺少 feature.img.text 节点（文本可读性规则无处可配）"
                } else {
                    if (normColor(textNode["base"]) != expectBase) {
                        violations += "文本底板色不一致：清单=$expectBase 配置=${normColor(textNode["base"])}"
                    }
                    if (normColor(textNode["color"]) != expectColor) {
                        violations += "文本默认色不一致：清单=$expectColor 配置=${normColor(textNode["color"])}"
                    }
                    if (normNum(textNode["contrast_threshold"]) != expectThreshold) {
                        violations += "描边阈值不一致：清单=$expectThreshold 配置=${normNum(textNode["contrast_threshold"])}"
                    }
                }
            }

            // 5.2 引擎默认值（TextToImg.Style.DEFAULT）
            val engineSource = File(
                rootDir,
                "render/src/main/kotlin/me/regadpole/plumbot/render/TextToImg.kt"
            ).readText()
            val enginePlate = Regex("""plateColor\s*:\s*Color\s*=\s*Color\(0x([0-9A-Fa-f]{6})\)""")
                .find(engineSource)?.groupValues?.get(1)?.uppercase()
            val engineText = Regex("""textColor\s*:\s*Color\s*=\s*Color\(0x([0-9A-Fa-f]{6})\)""")
                .find(engineSource)?.groupValues?.get(1)?.uppercase()
            val engineThreshold = Regex("""contrastThreshold:\s*Double\s*=\s*([0-9.]+)""")
                .find(engineSource)?.groupValues?.get(1)

            if (enginePlate != expectBase) {
                violations += "引擎底板色默认值不一致：清单=$expectBase TextToImg=$enginePlate"
            }
            if (engineText != expectColor) {
                violations += "引擎文字色默认值不一致：清单=$expectColor TextToImg=$engineText"
            }
            if (normNum(engineThreshold) != expectThreshold) {
                violations += "引擎描边阈值默认值不一致：清单=$expectThreshold TextToImg=$engineThreshold"
            }

            // 5.3 运行时载体（RenderTextStyle 默认值，配置缺失时由它兜底）
            val portalSource = File(
                rootDir,
                "render/src/main/kotlin/me/regadpole/plumbot/render/RenderPortal.kt"
            ).readText()
            val carrierBase = Regex("""baseColor:\s*String\s*=\s*"(#[0-9A-Fa-f]{6})"""")
                .find(portalSource)?.groupValues?.get(1)?.let { normColor(it) }
            if (carrierBase != expectBase) {
                violations += "运行时载体底板色默认值不一致：清单=$expectBase RenderTextStyle=$carrierBase"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkRenderProtocol failed with ${violations.size} violation(s):\n" +
                    violations.joinToString("\n") { "  - $it" }
            )
        }
        println("checkRenderProtocol: protocol manifest verified successfully (${routeEntries.size} routes, protocol ${manifest["protocol"]}).")
    }
}

subprojects {
    group = "me.regadpole"

    plugins.withId("com.gradleup.shadow") {
        val shadowJarTask = tasks.named<ShadowJar>("shadowJar")
        copyDist.configure {
            from(shadowJarTask)
        }
    }
}

/**
 * 产物重定位断言（A24 / D7 验收；发布前必跑）。
 *
 * 需要 `dist` 产物，因此**不挂进 `check`**（否则每次 check 都要跑一遍 shadowJar）；
 * 单独运行：`gradlew checkRelocation`。
 *
 * 断言内容：
 * 1. `net/kyori/option`、`net/kyori/examination` **不得**以原前缀出现（D7 要求重定位）；
 * 2. 不得出现 `top/` 命名空间（历史方案的回归防线）；
 * 3. `com/google/gson` **允许**保留原前缀（D7 明确豁免 —— 接受宿主自带 Gson）。
 */
val checkRelocation = tasks.register("checkRelocation") {
    group = "verification"
    description = "Asserts relocated prefixes and the top/* namespace are absent from dist jars."
    dependsOn(copyDist)

    val distDir = layout.projectDirectory.dir("dist")
    val mustBeRelocated = listOf("net/kyori/option/", "net/kyori/examination/")
    val forbiddenPrefixes = listOf("top/")

    doLast {
        val jars = distDir.asFile.listFiles { f -> f.isFile && f.extension == "jar" }?.toList().orEmpty()
        if (jars.isEmpty()) {
            throw GradleException("checkRelocation: dist/ 下没有产物，请先运行 gradlew dist")
        }

        val violations = mutableListOf<String>()
        jars.forEach { jar ->
            java.util.zip.ZipFile(jar).use { zip ->
                val names = zip.entries().toList().map { it.name }
                mustBeRelocated.forEach { prefix ->
                    val hits = names.count { it.startsWith(prefix) }
                    if (hits > 0) {
                        violations += "${jar.name}: 仍有 $hits 个条目以未重定位前缀 $prefix 开头（D7 要求重定位）"
                    }
                }
                forbiddenPrefixes.forEach { prefix ->
                    val hits = names.count { it.startsWith(prefix) }
                    if (hits > 0) violations += "${jar.name}: 出现禁用命名空间前缀 $prefix（$hits 条）"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkRelocation failed with ${violations.size} violation(s):\n" + violations.joinToString("\n") { "  - $it" }
            )
        }
        println("checkRelocation: 产物重定位断言通过（${jars.size} 个包；com/google/gson 按 D7 豁免）。")
    }
}

/**
 * A02：**「编译期依赖 − 运行期装载」差集校验**。
 *
 * ## 为什么需要
 * 这些库在构建期是 `compileOnly`（**不进产物**），只能由各平台启动时用 Libby 装入。
 * 若某个 `compileOnly` 依赖既没被 Shadow 打包、也不在 `RuntimeLibraries.ALL` 里，
 * 就到运行期才炸 `NoClassDefFoundError` —— 编译期毫无提示（N13/A02 的成因）。
 *
 * ## 防空转
 * 解析结果或 compileOnly 声明为空时**直接失败**：否则"解析不到 → 差集为空 → 通过"就是假绿。
 *
 * 负例自检：`gradlew checkRuntimeLibraries -PruntimeLibrarySelfTest=true`
 * 会注入一个必然缺失的坐标，并断言校验**确实能发现**它。
 */
val checkRuntimeLibraries = tasks.register("checkRuntimeLibraries") {
    group = "verification"
    description = "Asserts every compileOnly library that is not bundled is loaded at runtime via RuntimeLibraries."

    val sourceFile = layout.projectDirectory
        .file("common/src/main/kotlin/me/regadpole/plumbot/internal/RuntimeLibraries.kt")
    val selfTest = providers.gradleProperty("runtimeLibrarySelfTest").map { it.toBoolean() }.orElse(false)

    // 配置期读取（只看声明，不解析依赖、不触网）
    val compileOnlyByModule: Map<String, List<String>> = subprojects.associate { sub ->
        val declared = sub.configurations.findByName("compileOnly")
            ?.dependencies
            ?.filterIsInstance<org.gradle.api.artifacts.ModuleDependency>()
            ?.map { "${it.group}:${it.name}" }
            ?.sorted()
            .orEmpty()
        sub.path to declared
    }

    /** 豁免项：**必须带理由**，否则这个校验会退化成"看不懂就加白名单"。 */
    val exempt = mapOf(
        "org.spigotmc:spigot-api" to "宿主机 API（服务端自带）",
        "com.hypixel.hytale:Server" to "宿主机 API（Hytale 服务端自带）",
        "com.velocitypowered:velocity-api" to "宿主机 API（代理端自带）",
        "net.kyori:adventure-api" to "随产物打包（Shadow 重定位；由 checkRelocation 兜底断言）",
        "net.kyori:adventure-text-minimessage" to "随产物打包（Shadow 重定位）",
        "net.kyori:adventure-text-serializer-legacy" to "随产物打包（Shadow 重定位）",
        // MiraiMC 是**宿主插件**：本适配器运行在它内部，由它提供这些类（不是我们要装载的库）
        "io.github.dreamvoid:MiraiMC-Bukkit" to "宿主插件 API（适配器运行于 MiraiMC 内）",
        "io.github.dreamvoid:MiraiMC-Velocity" to "宿主插件 API（适配器运行于 MiraiMC 内）",
        "io.github.dreamvoid:MiraiMC-Integration" to "宿主插件 API（适配器运行于 MiraiMC 内）",
        // Playwright 走**按需装载**（RenderLibraryLoader.ensurePlaywright），不在常驻清单里
        "com.microsoft.playwright:playwright" to "按需装载（RenderLibraryLoader.ensurePlaywright）",
    )

    doLast {
        val text = sourceFile.asFile.readText()
        val groups = Regex("""groupId\s*=\s*"([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
        val artifacts = Regex("""artifactId\s*=\s*"([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()

        if (groups.isEmpty() || groups.size != artifacts.size) {
            throw GradleException(
                "checkRuntimeLibraries: 解析 RuntimeLibraries.kt 失败（groupId=${groups.size} artifactId=${artifacts.size}）" +
                    " —— 解析不到不等于没有问题，请检查声明格式。"
            )
        }

        val loaded = groups.zip(artifacts)
            .map { (g, a) -> "${g.replace("{}", ".")}:${a.replace("{}", ".")}" }
            .toSortedSet()

        val declaredAll = compileOnlyByModule.values.flatten().toSortedSet()
        if (declaredAll.isEmpty()) {
            throw GradleException("checkRuntimeLibraries: 未读到任何 compileOnly 声明 —— 校验会静默通过，判失败。")
        }

        val synthetic = if (selfTest.get()) listOf("com.example:definitely-missing") else emptyList()
        val missing = (declaredAll + synthetic).toSortedSet()
            .filterNot { it in loaded }
            .filterNot { it in exempt.keys }

        if (selfTest.get()) {
            if (missing.isEmpty()) {
                throw GradleException("checkRuntimeLibraries 自检失败：注入的缺失坐标未被发现（校验形同虚设）")
            }
            println("checkRuntimeLibraries: 负例自检通过（成功发现注入的缺失坐标）。")
            return@doLast
        }

        if (missing.isNotEmpty()) {
            val detail = missing.joinToString("\n") { coord ->
                val modules = compileOnlyByModule.filterValues { coord in it }.keys.sorted().joinToString(", ")
                "  - $coord（声明于 $modules）"
            }
            throw GradleException(
                "checkRuntimeLibraries failed: ${missing.size} 个 compileOnly 依赖既未打包、也未在 RuntimeLibraries 中装载，\n" +
                    "运行期会以 NoClassDefFoundError 形式爆炸：\n$detail\n" +
                    "修法：① 加入 RuntimeLibraries.ALL；或 ② 若由宿主/宿主插件提供、或随产物打包，" +
                    "则在本任务的 exempt 表里补充**带理由**的豁免。"
            )
        }

        println(
            "checkRuntimeLibraries: 差集校验通过（装载清单 ${loaded.size} 项；compileOnly 声明 ${declaredAll.size} 项；" +
                "豁免 ${exempt.size} 项）。"
        )
    }
}

/**
 * 把两个渲染纪律检查挂到每个模块的 `check` 上。
 *
 * 背景：这两个任务此前**只注册、未接入生命周期** —— `gradlew check` 不会执行它们，
 * 于是"本地全绿 / CI 全绿"并不包含模块边界与协议清单校验，纪律可以静默回退。
 * 与 `:api` 的 `checkApiContract`（已挂在 `:api:check`）保持一致。
 */
/**
 * 单一真源校验：某些"只应有一份"的定义不得在源码里出现第二份。
 *
 * 为什么放进构建期而不是 JVM 测试：这项断言原先写在 `PlayerNameRulesTest` 里（约 90 行，
 * 且依赖"测试工作目录"这一脆弱前提）。同样的事在构建期只需一次目录扫描 —— **校验位置越靠边越好**。
 *
 * 与"5 个校验冻结数量"的关系：本任务不是新增安全面，而是**把一处测试断言搬到更合适的位置**
 * （净减约 65 行），已按冻结规则说明理由。
 *
 * 防空转：源码目录缺失或没扫到任何 `.kt` 文件时**判失败**（否则"扫不到 → 通过"就是假绿）。
 */
val checkSingleDefinitions = tasks.register("checkSingleDefinitions") {
    group = "verification"
    description = "Asserts single-source definitions are not duplicated across modules."

    val roots = listOf(
        layout.projectDirectory.dir("common/src/main/kotlin"),
        layout.projectDirectory.dir("api/src/main/kotlin"),
    )
    val needles = mapOf(
        // 玩家名校验规则：只允许存在于 PlayerNameRules（A05）。两份副本会各自演化，
        // 造成"群里能绑、走 API 绑不上"这类静默不一致。
        "a-zA-Z0-9_.*]{3,16}" to "玩家名校验规则（应仅存在于 PlayerNameRules）",
    )

    doLast {
        val missing = roots.filterNot { it.asFile.isDirectory }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "checkSingleDefinitions: 源码目录缺失 —— 扫描会静默通过，判失败：\n" +
                    missing.joinToString("\n") { "  - ${it.asFile.path}" }
            )
        }

        var scanned = 0
        needles.forEach { (needle, label) ->
            val hits = mutableListOf<String>()
            roots.forEach { root ->
                root.asFile.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        scanned++
                        file.readLines().forEachIndexed { index, line ->
                            if (line.contains(needle)) {
                                hits += "${file.relativeTo(layout.projectDirectory.asFile)}:${index + 1}"
                            }
                        }
                    }
            }
            if (scanned == 0) {
                throw GradleException("checkSingleDefinitions: 未扫描到任何 .kt 文件 —— 判失败。")
            }
            if (hits.size != 1) {
                throw GradleException(
                    "checkSingleDefinitions failed: 「$label」应有且仅有一处定义，实际 ${hits.size} 处：\n" +
                        hits.joinToString("\n") { "  - $it" }
                )
            }
        }

        println("checkSingleDefinitions: 单一真源校验通过（${needles.size} 条规则，扫描 $scanned 个文件）。")
    }
}

/**
 * 与 `:api` 的 `checkApiContract`（已挂在 `:api:check`）保持一致。
 */
subprojects {
    plugins.withId("base") {
        tasks.named("check") {
            dependsOn(checkRenderBoundaries, checkRenderProtocol, checkRuntimeLibraries, checkSingleDefinitions)
        }
    }
}
