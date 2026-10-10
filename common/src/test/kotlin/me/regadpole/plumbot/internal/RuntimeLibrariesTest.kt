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
 */
class RuntimeLibrariesTest {

    private val specs = RuntimeLibraries.ALL

    @Test
    fun `manifest covers every compile-only runtime dependency`() {
        val expected = setOf(
            "com{}google{}guava:guava:${RuntimeLibraryVersions.GUAVA}",
            "com{}zaxxer:HikariCP:${RuntimeLibraryVersions.HIKARI_CP}",
            "org{}xerial:sqlite-jdbc:${RuntimeLibraryVersions.SQLITE_JDBC}",
            "com{}mysql:mysql-connector-j:${RuntimeLibraryVersions.MYSQL_CONNECTOR}",
            "com{}github{}RegadPoleCN:taboolib-database:${RuntimeLibraryVersions.TABOOLIB_DATABASE}",
            "com{}sksamuel{}aedile:aedile-core:${RuntimeLibraryVersions.AEDILE}",
            "com{}github{}ben-manes{}caffeine:caffeine:${RuntimeLibraryVersions.CAFFEINE}",
            "com{}github{}alazeprt:AOneBot:${RuntimeLibraryVersions.AONE_BOT}",
            "com{}google{}code{}gson:gson:${RuntimeLibraryVersions.GSON}",
            "org{}spongepowered:configurate-yaml:${RuntimeLibraryVersions.CONFIGURATE_YAML}",
            "org{}spongepowered:configurate-extra-kotlin:${RuntimeLibraryVersions.CONFIGURATE_EXTRA_KOTLIN}",
        )
        assertEquals(expected, specs.map { it.coordinate }.toSet(), "清单不得悄悄增删条目")
    }

    @Test
    fun `every spec is fully specified`() {
        specs.forEach { spec ->
            assertTrue(spec.groupId.isNotBlank(), "groupId 不能为空: $spec")
            assertTrue(spec.artifactId.isNotBlank(), "artifactId 不能为空: $spec")
            assertTrue(spec.version.isNotBlank(), "version 不能为空: $spec")
        }
    }

    @Test
    fun `relocated packages match the build-time relocation set`() {
        // 与 buildSrc/.../platform-distribution.gradle.kts 的 relocate 规则一一对应
        assertEquals(
            setOf(
                "com{}google{}common",
                "com{}zaxxer{}hikari",
                "com{}sksamuel{}aedile",
                "com{}github{}benmanes{}caffeine",
                "com{}google{}code{}gson",
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

    @Test
    fun `libraries without a build-time relocation stay unrelocated`() {
        val mustStayPlain = setOf("sqlite-jdbc", "mysql-connector-j", "configurate-yaml", "configurate-extra-kotlin", "gson")
        specs.filter { it.artifactId in mustStayPlain }.forEach { spec ->
            assertTrue(spec.relocates.isEmpty(), "${spec.artifactId} 没有构建期重定位，运行期也不能重定位")
        }
    }
}
