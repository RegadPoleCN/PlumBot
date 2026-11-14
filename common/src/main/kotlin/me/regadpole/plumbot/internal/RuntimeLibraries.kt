// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

/**
 * 运行期类库清单 —— **单一真源**。
 *
 * 这些库构建期是 `compileOnly`（不进产物），只能由各平台在启动时用 Libby 从远端装入，
 * 故按宿主差异分成四份：`ALL` 三端启动即装；`HYTALE_ONLY` 补 Hytale 缺失的 SLF4J
 * provider（缺了日志会整晚静默）；`KYORI_FALLBACK` 只在宿主不带 Adventure 时装入同包名
 * 副本，以便共享宿主那份 `Component`；`PLAYWRIGHT` 只在真正出图时按需下载约 190 MB 的
 * 原生驱动。前三份在启动路径上，装不上即启动失败；`PLAYWRIGHT` 的失败由渲染门户降级为
 * doctor 提示，不影响插件其余功能。
 *
 * **装载必须带上与产物同一套 relocate。** Shadow 会把我们自己的类里对这些库的引用一并
 * 改写成重定位名（`HikariDataSource` → `me.regadpole.plumbot.lib.com.zaxxer.hikari.HikariDataSource`），
 * 目标前缀须等于 `buildSrc/.../platform-distribution.gradle.kts` 的 `relocateRoot`；漏一条
 * 就在运行期炸 `NoClassDefFoundError`，编译期毫无提示。`relocate.from` 是 **Java 包名**
 * 而非 Maven groupId（写成 `com.google.code.gson` 会静默失效）。
 *
 * 每条显式 `transitive = false`：Libby 的传递解析**不区分 `<optional>`**，打开会带进
 * javassist/micrometer、整套 OCI SDK/Jackson，同一坐标还会被重复装载。缺失的传递依赖
 * 必须在这里列全。坐标中的 `{}` 是 Libby 的分隔符（等价于 `.`）。
 */
object RuntimeLibraries {

    /** Libby 重定位规则（from 为原包名，to 为重定位后包名）。 */
    data class Relocate(val from: String, val to: String)

    /**
     * 一个待装载的类库。
     *
     * @param transitive 是否解析传递依赖。默认 `false`（显式清单）；打开会重新引入
     *   `<optional>` 依赖与重复坐标，必须先在单测里说明理由。
     * @param relocates 该 jar 自身字节码里需要跟着改写的包（必须与 Shadow 规则一致）
     * @param sha256 构件的十六进制小写 SHA-256（Maven 原始字节，重定位前）。Libby 下载后
     *   自动比对，不匹配即拒绝装载并尝试下一个仓库，全部失败抛异常。JitPack 构件的构建
     *   不可复现，不在此钉定。
     */
    data class Spec(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val transitive: Boolean = false,
        val relocates: List<Relocate> = emptyList(),
        val sha256: String? = null,
    ) {
        /** 便于测试与日志的坐标字符串（Libby 形式）。 */
        val coordinate: String get() = "$groupId:$artifactId:$version"
    }

    /** 与产物重定位目标一致的前缀。 */
    const val RELOCATE_ROOT = "me{}regadpole{}plumbot{}lib"

    private fun relocateTo(pkg: String) = Relocate(pkg, "$RELOCATE_ROOT{}$pkg")

    /**
     * 所有平台都必须装载的类库。
     *
     * 顺序 = 装载顺序：被依赖者在前，便于首次启动时尽早可用。
     */
    val ALL: List<Spec> = listOf(
        Spec(
            groupId = "com{}google{}guava",
            artifactId = "guava",
            version = RuntimeLibraryVersions.GUAVA,
            sha256 = "972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480",
            // 只有 taboolib-database 引用 guava（产物自身不引用），它必须拿到重定位后的类名
            relocates = listOf(relocateTo("com{}google{}common")),
        ),
        Spec(
            groupId = "org{}slf4j",
            artifactId = "slf4j-api",
            version = RuntimeLibraryVersions.SLF4J,
            sha256 = "a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a",
            // HikariCP / sqlite-jdbc / mysql-connector-j / taboolib / AOneBot 都引用 slf4j；
            // Bukkit 与 Velocity 宿主自带，但 Hytale 服务端 jar 里没有，必须显式装载。
            // 这里只装 API：Bukkit/Velocity 用宿主绑定；Hytale 的 provider 见 HYTALE_ONLY。
        ),
        Spec(
            groupId = "com{}zaxxer",
            artifactId = "HikariCP",
            version = RuntimeLibraryVersions.HIKARI_CP,
            sha256 = "7c024aeff1c1063576d74453513f9de6447d8e624d17f8e27f30a2e97688c6c9",
            // 不装 javassist/micrometer：只有显式启用代理或 metrics 的代码路径才会用到
            relocates = listOf(relocateTo("com{}zaxxer{}hikari")),
        ),
        Spec(
            groupId = "com{}google{}code{}gson",
            artifactId = "gson",
            version = RuntimeLibraryVersions.GSON,
            sha256 = "57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b",
            // gson 不重定位：接受宿主自带（Paper/Hytale 都自带），AOneBot 也按原包名引用
        ),
        Spec(
            groupId = "org{}xerial",
            artifactId = "sqlite-jdbc",
            version = RuntimeLibraryVersions.SQLITE_JDBC,
            sha256 = "1df1b9404b1e434f7215b45e88d3a10558ccf5e905dcd36aff9d7f2c85bc225a",
        ),
        Spec(
            groupId = "com{}mysql",
            artifactId = "mysql-connector-j",
            version = RuntimeLibraryVersions.MYSQL_CONNECTOR,
            sha256 = "94e7fa815370cdcefed915db7f53f88445fac110f8c3818392b992ec9ee6d295",
            // 不装 protobuf/OCI SDK：它们只服务 X DevAPI 与 OCI IAM 认证，标准 JDBC 连接不加载
        ),
        Spec(
            groupId = "io{}leangen{}geantyref",
            artifactId = "geantyref",
            version = RuntimeLibraryVersions.GEANTYREF,
            sha256 = "7f1d591092d5142b6aaa7cf59f94c4c74d57dbeef03b2f8262949f8f36bac6e3",
            // configurate-core 的泛型 TypeToken 依赖（注意 groupId 与包名同形，容易写错）
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "option",
            version = RuntimeLibraryVersions.KYORI_OPTION,
            sha256 = "97b69b4b17dfe02217c9131ad342564cbc9aebd04c75eb689639b5f78fd4b11c",
            // configurate-core/yaml 按**原包名**引用它；产物里那份是 Adventure 用的重定位副本，不能复用
        ),
        Spec(
            groupId = "org{}spongepowered",
            artifactId = "configurate-core",
            version = RuntimeLibraryVersions.CONFIGURATE_CORE,
            sha256 = "06c1e9f7789a189af0c0156ebe9f469da7cc674233f43e8133c83131edd9f7e0",
        ),
        Spec(
            groupId = "org{}spongepowered",
            artifactId = "configurate-yaml",
            version = RuntimeLibraryVersions.CONFIGURATE_YAML,
            sha256 = "6294f085316188bffb71bb05ef36bf8755a288ed925c791952e1e8d7bfc8ba4c",
            // 自带 SnakeYAML（org.yaml.snakeyaml 在 jar 内），不需要额外装载
        ),
        Spec(
            groupId = "com{}github{}RegadPoleCN",
            artifactId = "taboolib-database",
            version = RuntimeLibraryVersions.TABOOLIB_DATABASE,
            // sha256 不钉定：JitPack 构件无不可变构建保证
            // 该 jar 同时引用 guava 与 hikari，两者都必须改写为上面的重定位名
            relocates = listOf(
                relocateTo("com{}google{}common"),
                relocateTo("com{}zaxxer{}hikari"),
            ),
        ),
        Spec(
            groupId = "com{}sksamuel{}aedile",
            artifactId = "aedile-core",
            version = RuntimeLibraryVersions.AEDILE,
            sha256 = "a64066797de8f4e31e7d717ee0d96fc3358319f27153feca217385dfb61d2f1a",
            // aedile 的字节码引用 caffeine（原本靠传递依赖拿到未重定位版本 → 类身份歧义）
            relocates = listOf(
                relocateTo("com{}sksamuel{}aedile"),
                relocateTo("com{}github{}benmanes{}caffeine"),
            ),
        ),
        Spec(
            groupId = "com{}github{}ben-manes{}caffeine",
            artifactId = "caffeine",
            version = RuntimeLibraryVersions.CAFFEINE,
            sha256 = "7dd15f9df1be238ffaa367ce6f556737a88031de4294dad18eef57c474ddf1d3",
            relocates = listOf(relocateTo("com{}github{}benmanes{}caffeine")),
        ),
        Spec(
            groupId = "org{}java-websocket",
            artifactId = "Java-WebSocket",
            version = RuntimeLibraryVersions.JAVA_WEBSOCKET,
            sha256 = "28d8392a882660e636603c0a72d3e1ed5da1993210214645754e93655c0d806a",
            // AOneBot 的 WebSocket 客户端依赖；此前靠传递依赖偶然装入
        ),
        Spec(
            groupId = "com{}github{}alazeprt",
            artifactId = "AOneBot",
            version = RuntimeLibraryVersions.AONE_BOT,
            // sha256 不钉定：JitPack 构件无不可变构建保证
            // 只按原包名引用 gson / Java-WebSocket / slf4j（均在上方显式装载），自身不重定位
        ),
    )

    /**
     * **仅 Hytale** 需要补充装载的项。
     *
     * Paper 与 Velocity 都自带 SLF4J provider；Hytale Server 0.7.2 的 40629 个条目里
     * **0 个** `org/slf4j` 类，清单里的 slf4j-api 会退化为 NOP（实测
     * `SLF4J: No SLF4J providers were found`），Hikari 池告警、驱动异常、taboolib 错误
     * 全部丢失。故只补一个 provider，且不解析传递依赖。
     */
    val HYTALE_ONLY: List<Spec> = listOf(
        Spec(
            groupId = "org{}slf4j",
            artifactId = "slf4j-simple",
            version = RuntimeLibraryVersions.SLF4J,
            sha256 = "effc32018658bea09d1e08c7d1060ccad46c086960f583d07dd7ffe9c1172a47",
        ),
    )

    /**
     * **仅当宿主不带 kyori（Adventure）时**补充装载的运行时。
     *
     * 装载策略见 [KyoriSupport]：Paper/Leaf 与 Velocity 自带 `net.kyori`，插件直接用宿主类
     * （只有共享同一份 `Component`，组件才能跨 API 边界互通）；Spigot/Hytale 没有，才由 Libby
     * 装入下面这套**原包名**类（不重定位，因为产物里不再有重定位副本）。
     *
     * 顺序 = 依赖顺序；`net.kyori:option` 不在此列（[ALL] 已为 configurate 显式装载）。
     */
    val KYORI_FALLBACK: List<Spec> = listOf(
        Spec(
            groupId = "net{}kyori",
            artifactId = "adventure-api",
            version = RuntimeLibraryVersions.ADVENTURE,
            sha256 = "64a765760401e78d433a87dbd4cda57016b303853c6fd1dc97087b5a3aa68133",
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "adventure-key",
            version = RuntimeLibraryVersions.ADVENTURE,
            sha256 = "6a9398c9d402089f9da060a56b45cae7d58cbf199371a6d8ec1a26d8f94cc6bb",
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "examination-api",
            version = RuntimeLibraryVersions.EXAMINATION_API,
            sha256 = "c9237ffecb05428f6eff86216246ac70ce0b47b04c08ea7ca35020fde57f8492",
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "examination-string",
            version = RuntimeLibraryVersions.EXAMINATION_API,
            sha256 = "7d01fc25a4bb3af0e1662685455f4541fbf4626216ea5846e455c1491e156b8c",
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "adventure-text-minimessage",
            version = RuntimeLibraryVersions.ADVENTURE,
            sha256 = "51cb4878851b72633bea1d1b5b24e8de34614bc928ae8a1dba96b638e9a4e8c4",
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "adventure-text-serializer-legacy",
            version = RuntimeLibraryVersions.ADVENTURE,
            sha256 = "9ac1b99f828458e19008f675781a0e8c8517cdfa22a1fc5bb87a7bf69554c35c",
        ),
    )

    /**
     * **仅按需**装载的 Playwright 运行时（`engine=playwright` 首次真实出图、或服主执行
     * `/plumbot render install|doctor` 时）。
     *
     * 三件缺一不可，且**不能**用 `playwright` 的传递解析代替：`driver` 只是装载器接口，
     * `driver-bundle` 才带原生 node 与 cli.js（**约 190 MB**，首次经 Libby 下载），
     * `playwright` 是 API 与 CLI 入口。`transitive = false` 是硬要求：其 POM 把
     * junit-jupiter / opentest4j 列为 compile 依赖，打开传递解析会把测试框架装进服务端
     * 类路径。它需要的 gson 与 Java-WebSocket 已在 [ALL]。
     *
     * 顺序 = 装载顺序：接口 → 实现/原生 node → API。
     */
    val PLAYWRIGHT: List<Spec> = listOf(
        Spec(
            groupId = "com{}microsoft{}playwright",
            artifactId = "driver",
            version = RuntimeLibraryVersions.PLAYWRIGHT,
            sha256 = "11e9ad53d59a885bc765804a9c84f8f613adbfc2f06bd5050e3533431f2fd419",
        ),
        Spec(
            groupId = "com{}microsoft{}playwright",
            artifactId = "driver-bundle",
            version = RuntimeLibraryVersions.PLAYWRIGHT,
            sha256 = "9daf56421e746bc55e7be4ddff7f9ea887901ddaec4b7b8b0361134927e0455f",
        ),
        Spec(
            groupId = "com{}microsoft{}playwright",
            artifactId = "playwright",
            version = RuntimeLibraryVersions.PLAYWRIGHT,
            sha256 = "e30a312f31efacab23f606447e6f130d71ae5554840c775e3bc63d135df6cbff",
        ),
    )

    /** 需要重定位的包名清单（测试与排查用）。 */
    val RELOCATED_PACKAGES: Set<String> = ALL.flatMap { it.relocates }.map { it.from }.toSet()
}
