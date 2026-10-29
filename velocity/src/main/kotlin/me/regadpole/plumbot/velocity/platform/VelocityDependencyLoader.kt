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

    /** 最近一次 Playwright 装载失败的原因（供 `/plumbot render doctor` 展示）；成功后清空。 */
    @Volatile
    private var playwrightFailure: String? = null

    /**
     * 按需下载并装载 Playwright 运行时（接口 / 原生 node 驱动 / API 三件，
     * 清单见 [RuntimeLibraries.PLAYWRIGHT]）。
     *
     * 调用方必须是异步线程：`driver-bundle` 约 190 MB，首次调用即一次真实下载。
     * **禁止 relocate** —— Playwright 依赖其原生 node driver 的路径结构。
     *
     * 与 Bukkit / Hytale 不同，这里**没有主线程守卫**：Velocity 没有"主线程 tick"概念，
     * 事件与命令都跑在独立线程池上，不存在"卡住服务端 tick"这条失败模式；单飞与
     * 冷却由门户的 [me.regadpole.plumbot.render.PlaywrightInstallGate] 负责。
     */
    override fun ensurePlaywright(): Boolean {
        if (playwrightReady) return true
        return synchronized(this) {
            if (playwrightReady) return true
            var failure: String? = null
            val ok = RuntimeLibraries.PLAYWRIGHT.all { spec ->
                runCatching {
                    libraryManager.loadLibrary(
                        Library.builder()
                            .groupId(spec.groupId)
                            .artifactId(spec.artifactId)
                            .version(spec.version)
                            .resolveTransitiveDependencies(spec.transitive)
                            .build()
                    )
                }.onFailure {
                    failure = "${spec.artifactId}: ${it.message ?: it.javaClass.simpleName}"
                }.isSuccess
            }
            playwrightReady = ok
            playwrightFailure = if (ok) null else failure ?: "未知原因（Libby 未返回异常）"
            ok
        }
    }

    override fun lastFailure(): String? = playwrightFailure

    fun loadDependencies() {
        val config = configProvider()

        // 自定义仓库：服主显式配置，优先级最高
        config?.getStringList("libraries", "custom_repositories")
            ?.filterNotNull()
            ?.map { it.trim().removeSuffix("/") }
            ?.filter { it.isNotBlank() }
            ?.forEach { customRepo ->
                libraryManager.addRepository(customRepo)
            }

        // 阿里云镜像，国内机房拉取快
        val useAliyun = config?.getBoolean("libraries", "use_aliyun_mirror") ?: true
        if (useAliyun) {
            libraryManager.addRepository("https://maven.aliyun.com/repository/public")
        }

        // Maven Central 核心兜底
        libraryManager.addMavenCentral()

        // JitPack：AOneBot 与 taboolib-database 托管于此
        libraryManager.addJitPack()

        // PaperMC 公共仓库（默认开启）
        val usePaperRepo = config?.getBoolean("libraries", "use_papermc_repo") ?: true
        if (usePaperRepo) {
            libraryManager.addRepository("https://repo.papermc.io/repository/maven-public")
        }

        loadCommonLibraries()
    }

    /**
     * 按 [RuntimeLibraries] 清单装载（与 Bukkit/Hytale 使用同一份清单）。
     *
     * 只装数据库驱动时代理端连 `config.yml` 都解析不了（configurate 缺失），产物中已被
     * 重定位的 Hikari/aedile/Caffeine 引用也没有对应类。
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
