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

package me.regadpole.plumbot.internal

/**
 * 运行期类库清单 —— **单一真源**。
 *
 * ## 为什么必须集中
 * 这些库在构建期是 `compileOnly`（不进产物），只能由各平台在启动时用 Libby 装入。
 * 历史上三端各写一份清单，结果 Bukkit 装了 9 个、Velocity/Hytale 只装了数据库驱动，
 * 代理端与 Hytale 端连 `config.yml` 都解析不了。
 *
 * ## 重定位规则必须与产物一致
 * Shadow 会把**我们自己的类**里对这些库的引用一起改写成重定位名，例如产物中
 * `HikariDataSource` 实际引用的是
 * `me.regadpole.plumbot.lib.com.zaxxer.hikari.HikariDataSource`。
 * 因此运行期装载**必须带上同一套 relocate**，否则运行时报
 * `NoClassDefFoundError`，且编译期毫无提示。
 * 目标前缀必须等于 `buildSrc/.../platform-distribution.gradle.kts` 的 `relocateRoot`。
 *
 * **`relocate` 的 from 是 Java 包名，不是 Maven groupId。**
 * 反例：AOneBot 曾写成 `com.google.code.gson`（那只是 gson 的 groupId，gson 的包名是
 * `com.google.gson`），规则静默失效；真正修好它是删掉规则（gson 按 D7 不重定位）。
 *
 * ## 为什么每条都显式列出传递依赖（`transitive = false`）
 * Libby 的传递解析**不区分 `<optional>`**：HikariCP 会带进 javassist/micrometer，
 * mysql-connector-j 会带进 OCI SDK/BouncyCastle/Jackson/Resilience4j/Vavr 等约 25 个
 * 与 JDBC 无关的 jar；同一坐标还会被不同父依赖重复装载（一次启动 68 行 "Loading library"，
 * 其中 slf4j 四份、kotlin-stdlib 两份）。这些类全部不进产物，只会在启动时拖慢主线程并
 * 制造类身份歧义。因此清单**只列运行期真正需要的坐标**，传递依赖必须在这里列全。
 *
 * 坐标中的 `{}` 是 Libby 的分隔符（等价于 `.`）。
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
     */
    data class Spec(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val transitive: Boolean = false,
        val relocates: List<Relocate> = emptyList(),
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
        // ---- 基础库 ----
        Spec(
            groupId = "com{}google{}guava",
            artifactId = "guava",
            version = RuntimeLibraryVersions.GUAVA,
            // 只有 taboolib-database 引用 guava（产物自身不引用），它必须拿到重定位后的类名
            relocates = listOf(relocateTo("com{}google{}common")),
        ),
        Spec(
            groupId = "org{}slf4j",
            artifactId = "slf4j-api",
            version = RuntimeLibraryVersions.SLF4J,
            // HikariCP / sqlite-jdbc / mysql-connector-j / taboolib / AOneBot 都引用 slf4j；
            // Bukkit 与 Velocity 宿主自带，但 Hytale 服务端 jar 里没有，必须显式装载。
            // 这里只装 API：Bukkit/Velocity 用宿主绑定；Hytale 的 provider 见 HYTALE_ONLY。
        ),
        Spec(
            groupId = "com{}zaxxer",
            artifactId = "HikariCP",
            version = RuntimeLibraryVersions.HIKARI_CP,
            // 不装 javassist/micrometer：只有显式启用代理或 metrics 的代码路径才会用到
            relocates = listOf(relocateTo("com{}zaxxer{}hikari")),
        ),
        Spec(
            groupId = "com{}google{}code{}gson",
            artifactId = "gson",
            version = RuntimeLibraryVersions.GSON,
            // D7：gson 不重定位，接受宿主自带（Paper/Hytale 都自带）；AOneBot 也按原包名引用
        ),
        // ---- 数据库驱动 ----
        Spec(
            groupId = "org{}xerial",
            artifactId = "sqlite-jdbc",
            version = RuntimeLibraryVersions.SQLITE_JDBC,
        ),
        Spec(
            groupId = "com{}mysql",
            artifactId = "mysql-connector-j",
            version = RuntimeLibraryVersions.MYSQL_CONNECTOR,
            // 不装 protobuf/OCI SDK：它们只服务 X DevAPI 与 OCI IAM 认证，标准 JDBC 连接不加载
        ),
        // ---- 配置 ----
        Spec(
            groupId = "io{}leangen{}geantyref",
            artifactId = "geantyref",
            version = RuntimeLibraryVersions.GEANTYREF,
            // configurate-core 的泛型 TypeToken 依赖（注意 groupId 与包名同形，容易写错）
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "option",
            version = RuntimeLibraryVersions.KYORI_OPTION,
            // configurate-core/yaml 按**原包名**引用它；产物里那份是 Adventure 用的重定位副本，不能复用
        ),
        Spec(
            groupId = "org{}spongepowered",
            artifactId = "configurate-core",
            version = RuntimeLibraryVersions.CONFIGURATE_CORE,
        ),
        Spec(
            groupId = "org{}spongepowered",
            artifactId = "configurate-yaml",
            version = RuntimeLibraryVersions.CONFIGURATE_YAML,
            // 自带 SnakeYAML（org.yaml.snakeyaml 在 jar 内），不需要额外装载
        ),
        // ---- 数据库访问层 ----
        Spec(
            groupId = "com{}github{}RegadPoleCN",
            artifactId = "taboolib-database",
            version = RuntimeLibraryVersions.TABOOLIB_DATABASE,
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
            relocates = listOf(relocateTo("com{}github{}benmanes{}caffeine")),
        ),
        // ---- 机器人适配 ----
        Spec(
            groupId = "org{}java-websocket",
            artifactId = "Java-WebSocket",
            version = RuntimeLibraryVersions.JAVA_WEBSOCKET,
            // AOneBot 的 WebSocket 客户端依赖；此前靠传递依赖偶然装入
        ),
        Spec(
            groupId = "com{}github{}alazeprt",
            artifactId = "AOneBot",
            version = RuntimeLibraryVersions.AONE_BOT,
            // 只按原包名引用 gson / Java-WebSocket / slf4j（均在上方显式装载），自身不重定位
        ),
    )

    /**
     * **仅 Hytale** 需要补充装载的项。
     *
     * 宿主差异：Paper 与 Velocity 都自带 SLF4J provider，而 Hytale Server 0.7.2 的
     * 40629 个条目里 **0 个** `org/slf4j` 类 —— 清单里的 slf4j-api 会退化为 NOP
     * （实测 `SLF4J: No SLF4J providers were found`），Hikari 池告警、驱动异常、
     * taboolib 错误全部丢失。故只给 Hytale 补一个 provider，且不解析传递依赖。
     */
    val HYTALE_ONLY: List<Spec> = listOf(
        Spec(
            groupId = "org{}slf4j",
            artifactId = "slf4j-simple",
            version = RuntimeLibraryVersions.SLF4J,
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
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "adventure-key",
            version = RuntimeLibraryVersions.ADVENTURE,
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "examination-api",
            version = RuntimeLibraryVersions.EXAMINATION_API,
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "examination-string",
            version = RuntimeLibraryVersions.EXAMINATION_API,
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "adventure-text-minimessage",
            version = RuntimeLibraryVersions.ADVENTURE,
        ),
        Spec(
            groupId = "net{}kyori",
            artifactId = "adventure-text-serializer-legacy",
            version = RuntimeLibraryVersions.ADVENTURE,
        ),
    )

    /** 需要重定位的包名清单（测试与排查用）。 */
    val RELOCATED_PACKAGES: Set<String> = ALL.flatMap { it.relocates }.map { it.from }.toSet()
}
