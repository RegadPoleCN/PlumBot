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
import me.regadpole.plumbot.internal.KyoriSupport
import me.regadpole.plumbot.internal.RuntimeLibraries
import me.regadpole.plumbot.internal.RuntimeLibraryVersions
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.render.RenderLibraryLoader
import org.slf4j.Logger
import java.nio.file.Path

class VelocityDependencyLoader(
    private val plugin: Any,
    logger: Logger,
    dataDirectory: Path,
    pluginManager: PluginManager,
    private val configProvider: () -> PlatformConfig?
) : RenderLibraryLoader {
    private val libraryManager: VelocityLibraryManager<Any> by lazy {
        VelocityLibraryManager(plugin, logger, dataDirectory, pluginManager)
    }

    @Volatile
    private var playwrightReady = false

    /**
     * 按需下载并装载 Playwright（仅 `engine=playwright` 且真正出图时才会被调用）。
     * **禁止 relocate** —— Playwright 依赖其原生 node driver 的路径结构。
     */
    override fun ensurePlaywright(): Boolean {
        if (playwrightReady) return true
        return synchronized(this) {
            if (playwrightReady) return true
            val ok = runCatching {
                libraryManager.loadLibrary(
                    Library.builder()
                        .groupId("com{}microsoft{}playwright")
                        .artifactId("playwright")
                        .version(RuntimeLibraryVersions.PLAYWRIGHT)
                        .resolveTransitiveDependencies(true)
                        .build()
                )
            }.isSuccess
            playwrightReady = ok
            ok
        }
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

    /**
     * 按 [RuntimeLibraries] 清单装载（与 Bukkit/Hytale 使用同一份清单）。
     *
     * 此前这里只装数据库驱动，导致代理端连 `config.yml` 都解析不了（configurate 缺失），
     * 且产物中已被重定位的 Hikari/aedile/Caffeine 引用无对应类。
     *
     * Adventure 例外：Velocity 自带 kyori，KyoriSupport 会跳过回退副本，直接共享宿主类。
     */
    private fun loadCommonLibraries() {
        KyoriSupport.manifestFor(plugin.javaClass.classLoader).forEach { spec ->
            val builder = Library.builder()
                .groupId(spec.groupId)
                .artifactId(spec.artifactId)
                .version(spec.version)
                .resolveTransitiveDependencies(spec.transitive)
            spec.relocates.forEach { builder.relocate(it.from, it.to) }
            libraryManager.loadLibrary(builder.build())
        }
    }
}
