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

package me.regadpole.plumbot.velocity.platform

import com.alessiodp.libby.VelocityLibraryManager
import com.alessiodp.libby.Library
import com.velocitypowered.api.plugin.PluginManager
import me.regadpole.plumbot.internal.RuntimeLibraryVersions
import me.regadpole.plumbot.api.platform.PlatformConfig
import org.slf4j.Logger
import java.nio.file.Path

class VelocityDependencyLoader(
    plugin: Any,
    logger: Logger,
    dataDirectory: Path,
    pluginManager: PluginManager,
    private val configProvider: () -> PlatformConfig?
) {
    private val libraryManager: VelocityLibraryManager<Any> by lazy {
        VelocityLibraryManager(plugin, logger, dataDirectory, pluginManager)
    }

    fun loadDependencies() {
        val config = configProvider()

        // 1. 自定义仓库
        config?.getStringList("libraries", "custom_repositories")
            ?.filterNotNull()
            ?.map { it.trim().removeSuffix("/") }
            ?.filter { it.isNotBlank() }
            ?.forEach { customRepo ->
                libraryManager.addRepository(customRepo)
            }

        // 2. 阿里云镜像
        val useAliyun = config?.getBoolean("libraries", "use_aliyun_mirror") ?: true
        if (useAliyun) {
            libraryManager.addRepository("https://maven.aliyun.com/repository/public")
        }

        // 3. MavenCentral 核心兜底
        libraryManager.addMavenCentral()

        // 4. JitPack 专属源
        libraryManager.addJitPack()

        // 5. PaperMC 官方源
        val usePaperRepo = config?.getBoolean("libraries", "use_papermc_repo") ?: true
        if (usePaperRepo) {
            libraryManager.addRepository("https://repo.papermc.io/repository/maven-public")
        }

        // 加载基础依赖组件
        loadCommonLibraries()
    }

    private fun loadCommonLibraries() {
        libraryManager.loadLibrary(
            Library.builder()
                .groupId("org{}xerial")
                .artifactId("sqlite-jdbc")
                .version(RuntimeLibraryVersions.SQLITE_JDBC)
                .build()
        )

        libraryManager.loadLibrary(
            Library.builder()
                .groupId("com{}mysql")
                .artifactId("mysql-connector-j")
                .version(RuntimeLibraryVersions.MYSQL_CONNECTOR)
                .build()
        )
    }
}
