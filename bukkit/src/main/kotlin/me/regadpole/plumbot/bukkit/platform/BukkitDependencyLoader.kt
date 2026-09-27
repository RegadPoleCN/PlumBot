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

package me.regadpole.plumbot.bukkit.platform

import com.alessiodp.libby.BukkitLibraryManager
import com.alessiodp.libby.Library
import me.regadpole.plumbot.internal.RuntimeLibraryVersions
import me.regadpole.plumbot.api.platform.PlatformConfig
import org.bukkit.plugin.java.JavaPlugin

class BukkitDependencyLoader(
    plugin: JavaPlugin,
    private val configProvider: () -> PlatformConfig?
) {
    private val libraryManager: BukkitLibraryManager by lazy { BukkitLibraryManager(plugin) }

    fun loadDependencies() {
        val config = configProvider()

        // 1. 【第一优先级】：服主明确自定义配置的仓库列表
        config?.getStringList("libraries", "custom_repositories")
            ?.filterNotNull()
            ?.map { it.trim().removeSuffix("/") }
            ?.filter { it.isNotBlank() }
            ?.forEach { customRepo ->
                libraryManager.addRepository(customRepo)
            }

        // 2. 【第二优先级】：国内阿里云公共镜像（默认开启，国内机房秒拉取）
        val useAliyun = config?.getBoolean("libraries", "use_aliyun_mirror") ?: true
        if (useAliyun) {
            libraryManager.addRepository("https://maven.aliyun.com/repository/public")
        }

        // 3. 【核心基础源】：Maven Central（官方中央仓库，必须保留兜底）
        libraryManager.addMavenCentral()

        // 4. 【专属构建源】：JitPack（AOneBot 与 taboolib-database 托管于此，任何环境均必须在列）
        libraryManager.addJitPack()

        // 5. 【Minecraft 生态源】：PaperMC 官方公共仓库（保障领域构件高可用）
        val usePaperMc = config?.getBoolean("libraries", "use_papermc_repo") ?: true
        if (usePaperMc) {
            libraryManager.addRepository("https://repo.papermc.io/repository/maven-public")
        }

        val databaseLib = Library.builder()
            .groupId("com{}github{}RegadPoleCN")
            .artifactId("taboolib-database")
            .version(RuntimeLibraryVersions.TABOOLIB_DATABASE)
            .relocate("com{}google{}common", "me{}regadpole{}plumbot{}lib{}com{}google{}common")
            .build()
        val hikaricpLib = Library.builder()
            .groupId("com{}zaxxer")
            .artifactId("HikariCP")
            .version(RuntimeLibraryVersions.HIKARI_CP)
            .resolveTransitiveDependencies(true)
            .build()
        val guavaLib = Library.builder()
            .groupId("com{}google{}guava")
            .artifactId("guava")
            .version(RuntimeLibraryVersions.GUAVA)
            .relocate("com{}google{}common", "me{}regadpole{}plumbot{}lib{}com{}google{}common")
            .resolveTransitiveDependencies(true)
            .build()
        val sqliteLib = Library.builder()
            .groupId("org{}xerial")
            .artifactId("sqlite-jdbc")
            .version(RuntimeLibraryVersions.SQLITE_JDBC)
            .resolveTransitiveDependencies(true)
            .build()
        val mysqlLib = Library.builder()
            .groupId("com{}mysql")
            .artifactId("mysql-connector-j")
            .version(RuntimeLibraryVersions.MYSQL_CONNECTOR)
            .resolveTransitiveDependencies(true)
            .build()
        val aonebotLib = Library.builder()
            .groupId("com{}github{}alazeprt")
            .artifactId("AOneBot")
            .version(RuntimeLibraryVersions.AONE_BOT)
            .relocate("com{}google{}code{}gson", "me{}regadpole{}plumbot{}lib{}com{}google{}code{}gson")
            .resolveTransitiveDependencies(true)
            .build()
        val gsonLib = Library.builder()
            .groupId("com{}google{}code{}gson")
            .artifactId("gson")
            .version(RuntimeLibraryVersions.GSON)
            .resolveTransitiveDependencies(true)
            .build()
        val configurateYamlLib = Library.builder()
            .groupId("org{}spongepowered")
            .artifactId("configurate-yaml")
            .version(RuntimeLibraryVersions.CONFIGURATE_YAML)
            .resolveTransitiveDependencies(true)
            .build()
        val configurateExtraKotlinLib = Library.builder()
            .groupId("org{}spongepowered")
            .artifactId("configurate-extra-kotlin")
            .version(RuntimeLibraryVersions.CONFIGURATE_EXTRA_KOTLIN)
            .resolveTransitiveDependencies(true)
            .build()

        libraryManager.loadLibraries(
            guavaLib, hikaricpLib, sqliteLib, mysqlLib, databaseLib,
            aonebotLib, gsonLib, configurateYamlLib, configurateExtraKotlinLib
        )
    }
}
