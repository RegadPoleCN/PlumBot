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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 运行期类库清单的护栏测试。
 *
 * 背景：三端曾各自维护装载清单，结果 Velocity/Hytale 只装了数据库驱动 —— 代理端连
 * `config.yml` 都解析不了；Hikari/aedile/Caffeine 又因为**产物里引用已被重定位**
 * 而必须带 relocate 装载。这些约束都在编译期看不出来，只能靠断言钉住。
 *
 * 本测试同时钉住两组历史事故：
 * 1. [RuntimeLibraries] 的 `transitive` 一旦打开，Libby 会连 POM 的 `<optional>` 一起解析，
 *    一次启动多拉 40+ 个无关 jar（见待办 H1 的启动日志）。
 * 2. `relocate` 的 `from` 写成 Maven groupId（如 `com.google.code.gson`）会**静默失效**，
 *    所以每条规则都必须对应一个真实引用它的 jar。
 */
class RuntimeLibrariesTest {

    private val specs = RuntimeLibraries.ALL

    @Test
    fun `manifest covers every compile-only runtime dependency`() {
        val expected = setOf(
            "com{}google{}guava:guava:${RuntimeLibraryVersions.GUAVA}",
            "org{}slf4j:slf4j-api:${RuntimeLibraryVersions.SLF4J}",
            "com{}zaxxer:HikariCP:${RuntimeLibraryVersions.HIKARI_CP}",
            "org{}xerial:sqlite-jdbc:${RuntimeLibraryVersions.SQLITE_JDBC}",
            "com{}mysql:mysql-connector-j:${RuntimeLibraryVersions.MYSQL_CONNECTOR}",
            "io{}leangen{}geantyref:geantyref:${RuntimeLibraryVersions.GEANTYREF}",
            "net{}kyori:option:${RuntimeLibraryVersions.KYORI_OPTION}",
            "org{}spongepowered:configurate-core:${RuntimeLibraryVersions.CONFIGURATE_CORE}",
            "org{}spongepowered:configurate-yaml:${RuntimeLibraryVersions.CONFIGURATE_YAML}",
            "com{}github{}RegadPoleCN:taboolib-database:${RuntimeLibraryVersions.TABOOLIB_DATABASE}",
            "com{}sksamuel{}aedile:aedile-core:${RuntimeLibraryVersions.AEDILE}",
            "com{}github{}ben-manes{}caffeine:caffeine:${RuntimeLibraryVersions.CAFFEINE}",
            "org{}java-websocket:Java-WebSocket:${RuntimeLibraryVersions.JAVA_WEBSOCKET}",
            "com{}github{}alazeprt:AOneBot:${RuntimeLibraryVersions.AONE_BOT}",
            "com{}google{}code{}gson:gson:${RuntimeLibraryVersions.GSON}",
        )
        assertEquals(expected, specs.map { it.coordinate }.toSet(), "清单不得悄悄增删条目")
    }

    @Test
    fun `every spec is fully specified and has no transitive resolution`() {
        specs.forEach { spec ->
            assertTrue(spec.groupId.isNotBlank(), "groupId 不能为空: $spec")
            assertTrue(spec.artifactId.isNotBlank(), "artifactId 不能为空: $spec")
            assertTrue(spec.version.isNotBlank(), "version 不能为空: $spec")
            assertTrue(
                !spec.transitive,
                "${spec.artifactId} 打开了传递解析：Libby 会把 POM 的 optional 依赖也装进来，必须显式列出所需坐标",
            )
        }
    }

    @Test
    fun `coordinates are unique`() {
        assertEquals(specs.size, specs.map { it.coordinate }.toSet().size, "同一坐标只能出现一次")
    }

    @Test
    fun `relocated packages match the relocation contract`() {
        // 与 buildSrc/.../platform-distribution.gradle.kts 的 relocate 规则一一对应。
        // com.google.common 是唯一的例外：Shadow 不重定位它（产物自身不引用 guava），
        // 但 taboolib-database 的字节码引用它，Libby 必须把它改写到同一个重定位前缀，
        // 因此运行期装载的 guava 也必须使用相同规则。
        assertEquals(
            setOf(
                "com{}google{}common",
                "com{}zaxxer{}hikari",
                "com{}sksamuel{}aedile",
                "com{}github{}benmanes{}caffeine",
            ),
            RuntimeLibraries.RELOCATED_PACKAGES,
        )
    }

    @Test
    fun `relocation targets use the shared root prefix`() {
        specs.flatMap { it.relocates }.forEach { relocate ->
            assertEquals(
                "${RuntimeLibraries.RELOCATE_ROOT}{}${relocate.from}",
                relocate.to,
                "重定位目标必须等于 ${RuntimeLibraries.RELOCATE_ROOT} + 原包名，否则与产物引用对不上",
            )
        }
    }

    /**
     * 每条重定位规则都必须有真实消费者，且消费者必须是预期的那几个 jar。
     *
     * 这条测试能挡住历史上出现过的一类“死规则”：`relocate("com.google.code.gson", …)`
     * 的 `from` 是 Maven groupId 而非包名，Libby 匹配不到任何类，规则静默失效。
     */
    @Test
    fun `every relocation rule has a known consumer`() {
        val expectedConsumers = mapOf(
            "com{}google{}common" to setOf("guava", "taboolib-database"),
            "com{}zaxxer{}hikari" to setOf("taboolib-database", "HikariCP"),
            "com{}sksamuel{}aedile" to setOf("aedile-core"),
            "com{}github{}benmanes{}caffeine" to setOf("aedile-core", "caffeine"),
        )
        val actualConsumers = specs
            .flatMap { spec -> spec.relocates.map { it.from to spec.artifactId } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, artifacts) -> artifacts.toSet() }

        assertEquals(expectedConsumers, actualConsumers, "重定位规则集合变化时必须同步核对真实引用它的 jar")
    }

    @Test
    fun `libraries that reference original package names stay unrelocated`() {
        val mustStayPlain = setOf(
            "sqlite-jdbc",
            "mysql-connector-j",
            "slf4j-api",
            "geantyref",
            "option",
            "configurate-core",
            "configurate-yaml",
            "Java-WebSocket",
            "AOneBot",
            "gson", // D7：接受宿主自带 gson
        )
        specs.filter { it.artifactId in mustStayPlain }.forEach { spec ->
            assertTrue(spec.relocates.isEmpty(), "${spec.artifactId} 的字节码按原包名引用依赖，运行期不能重定位")
        }
    }

    @Test
    fun `hytale-only extras are providers that other hosts already ship`() {
        assertEquals(
            listOf("org{}slf4j:slf4j-simple:${RuntimeLibraryVersions.SLF4J}"),
            RuntimeLibraries.HYTALE_ONLY.map { it.coordinate },
            "Hytale 只补 SLF4J provider：Paper/Velocity 宿主自带，重复装载会多出绑定告警",
        )
        RuntimeLibraries.HYTALE_ONLY.forEach { spec ->
            assertTrue(!spec.transitive, "宿主补充项同样不得解析传递依赖")
            assertTrue(spec.relocates.isEmpty(), "provider 不能重定位（SPI 类名必须保持原样）")
        }
        assertTrue(
            RuntimeLibraries.ALL.none { it.artifactId == "slf4j-simple" },
            "provider 不能混进跨平台清单（Bukkit/Velocity 已由宿主提供绑定）",
        )
    }

    /**
     * 回退清单只在宿主**没有** Adventure 时使用（Spigot/Hytale）。
     *
     * 一旦这里出现重定位规则，或者在 [RuntimeLibraries.ALL] 里重复装载，
     * 就会重新制造「插件 Component ≠ 宿主 Component」的跨 API 事故。
     */
    @Test
    fun `kyori fallback mirrors the host package names and never relocates`() {
        assertEquals(
            listOf(
                "net{}kyori:adventure-api:${RuntimeLibraryVersions.ADVENTURE}",
                "net{}kyori:adventure-key:${RuntimeLibraryVersions.ADVENTURE}",
                "net{}kyori:examination-api:${RuntimeLibraryVersions.EXAMINATION_API}",
                "net{}kyori:examination-string:${RuntimeLibraryVersions.EXAMINATION_API}",
                "net{}kyori:adventure-text-minimessage:${RuntimeLibraryVersions.ADVENTURE}",
                "net{}kyori:adventure-text-serializer-legacy:${RuntimeLibraryVersions.ADVENTURE}",
            ),
            RuntimeLibraries.KYORI_FALLBACK.map { it.coordinate },
            "回退项必须与宿主同版本、同包名",
        )
        RuntimeLibraries.KYORI_FALLBACK.forEach { spec ->
            assertTrue(!spec.transitive, "回退项不得解析传递依赖（optional 会带进一堆无关 jar）")
            assertTrue(spec.relocates.isEmpty(), "回退项必须保持原包名，重定位会再次割裂宿主类身份")
        }

        val base = RuntimeLibraries.ALL.map { it.coordinate }.toSet()
        RuntimeLibraries.KYORI_FALLBACK.forEach { spec ->
            assertTrue(spec.coordinate !in base, "回退项 ${spec.coordinate} 不能同时出现在 ALL 里")
        }
    }
}
