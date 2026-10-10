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
 * 坐标中的 `{}` 是 Libby 的分隔符（等价于 `.`），relocate 的 from 用**包名**。
 */
object RuntimeLibraries {

    /** Libby 重定位规则（from 为原包名，to 为重定位后包名）。 */
    data class Relocate(val from: String, val to: String)

    /**
     * 一个待装载的类库。
     *
     * @param transitive 是否解析传递依赖（关掉时必须自己把依赖也列进来）
     */
    data class Spec(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val transitive: Boolean = true,
        val relocates: List<Relocate> = emptyList(),
    ) {
        /** 便于测试与日志的坐标字符串（Libby 形式）。 */
        val coordinate: String get() = "$groupId:$artifactId:$version"
    }

    /** 与产物重定位目标一致的前缀。 */
    const val RELOCATE_ROOT = "me{}regadpole{}plumbot{}lib"

    private fun relocateTo(pkg: String) = Relocate(pkg, "$RELOCATE_ROOT{}$pkg")

    /** 所有平台都必须装载的类库。 */
    val ALL: List<Spec> = listOf(
        Spec(
            groupId = "com{}google{}guava",
            artifactId = "guava",
            version = RuntimeLibraryVersions.GUAVA,
            relocates = listOf(relocateTo("com{}google{}common")),
        ),
        Spec(
            groupId = "com{}zaxxer",
            artifactId = "HikariCP",
            version = RuntimeLibraryVersions.HIKARI_CP,
            relocates = listOf(relocateTo("com{}zaxxer{}hikari")),
        ),
        Spec(
            groupId = "org{}xerial",
            artifactId = "sqlite-jdbc",
            version = RuntimeLibraryVersions.SQLITE_JDBC,
        ),
        Spec(
            groupId = "com{}mysql",
            artifactId = "mysql-connector-j",
            version = RuntimeLibraryVersions.MYSQL_CONNECTOR,
        ),
        Spec(
            groupId = "com{}github{}RegadPoleCN",
            artifactId = "taboolib-database",
            version = RuntimeLibraryVersions.TABOOLIB_DATABASE,
            // 上游未开传递依赖，其运行期依赖（guava）由上面的显式条目提供，两边 relocate 目标必须一致
            transitive = false,
            relocates = listOf(relocateTo("com{}google{}common")),
        ),
        Spec(
            groupId = "com{}sksamuel{}aedile",
            artifactId = "aedile-core",
            version = RuntimeLibraryVersions.AEDILE,
            relocates = listOf(relocateTo("com{}sksamuel{}aedile")),
        ),
        Spec(
            groupId = "com{}github{}ben-manes{}caffeine",
            artifactId = "caffeine",
            version = RuntimeLibraryVersions.CAFFEINE,
            // 显式装载：aedile 的传递依赖不会带上 relocate，而产物引用的是重定位后的类名
            transitive = false,
            relocates = listOf(relocateTo("com{}github{}benmanes{}caffeine")),
        ),
        Spec(
            groupId = "com{}github{}alazeprt",
            artifactId = "AOneBot",
            version = RuntimeLibraryVersions.AONE_BOT,
            relocates = listOf(relocateTo("com{}google{}code{}gson")),
        ),
        Spec(
            groupId = "com{}google{}code{}gson",
            artifactId = "gson",
            version = RuntimeLibraryVersions.GSON,
        ),
        Spec(
            groupId = "org{}spongepowered",
            artifactId = "configurate-yaml",
            version = RuntimeLibraryVersions.CONFIGURATE_YAML,
        ),
        Spec(
            groupId = "org{}spongepowered",
            artifactId = "configurate-extra-kotlin",
            version = RuntimeLibraryVersions.CONFIGURATE_EXTRA_KOTLIN,
        ),
    )

    /** 需要重定位的包名清单（测试与排查用）。 */
    val RELOCATED_PACKAGES: Set<String> = ALL.flatMap { it.relocates }.map { it.from }.toSet()
}
