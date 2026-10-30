// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.platform

import com.alessiodp.libby.BukkitLibraryManager
import com.alessiodp.libby.Library
import me.regadpole.plumbot.internal.KyoriSupport
import me.regadpole.plumbot.internal.RuntimeLibraries
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.render.RenderLibraryLoader
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class BukkitDependencyLoader(
    private val plugin: JavaPlugin,
    private val configProvider: () -> PlatformConfig?
) : RenderLibraryLoader {
    private val libraryManager: BukkitLibraryManager by lazy { BukkitLibraryManager(plugin) }

    @Volatile
    private var playwrightReady = false

    /** 最近一次 Playwright 装载失败的原因（供 `/plumbot render doctor` 展示）；成功后清空。 */
    @Volatile
    private var playwrightFailure: String? = null

    /**
     * 按需下载并装载 Playwright 运行时（接口 / 原生 node 驱动 / API 三件，
     * 清单见 [RuntimeLibraries.PLAYWRIGHT]）。
     *
     * 只允许异步线程调用：`driver-bundle` 约 190 MB，第一次调用就是一次真实下载，
     * 在主线程上会直接卡住服务端。单飞、只记成功：失败不置成功标志（下次调用可重试），
     * 失败原因留给 [lastFailure]，由 `/plumbot render doctor` 原样展示。
     * **禁止 relocate** —— Playwright 依赖其原生 node driver 的路径结构。
     */
    override fun ensurePlaywright(): Boolean {
        if (playwrightReady) return true
        // 主线程守卫。门户侧的装载门已保证"只调度不内联下载"，但装载器本身是本模块对外的
        // 最后一道闸：任何绕过门的调用（第三方插件、将来的新入口）在主线程上必须被拒绝，
        // 否则一次约 190 MB 的下载会直接卡死服务端 tick。
        if (Bukkit.isPrimaryThread()) {
            playwrightFailure = "ensurePlaywright() 在主线程被调用，已拒绝下载（会卡住服务端 tick）；请由异步泳道触发 /plumbot render doctor"
            plugin.logger.warning("[PlumBot-Render] $playwrightFailure")
            return false
        }
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

        // 阿里云公共镜像（默认开启，国内机房拉取快）
        val useAliyun = config?.getBoolean("libraries", "use_aliyun_mirror") ?: true
        if (useAliyun) {
            libraryManager.addRepository("https://maven.aliyun.com/repository/public")
        }

        // Maven Central 中央仓库，必须保留兜底
        libraryManager.addMavenCentral()

        // JitPack：AOneBot 与 taboolib-database 托管于此，任何环境均必须在列
        libraryManager.addJitPack()

        // PaperMC 公共仓库（默认开启，保障 Minecraft 领域构件可用）
        val usePaperMc = config?.getBoolean("libraries", "use_papermc_repo") ?: true
        if (usePaperMc) {
            libraryManager.addRepository("https://repo.papermc.io/repository/maven-public")
        }
        loadManifestLibraries()
    }

    /**
     * 按 [RuntimeLibraries] 清单装载。
     *
     * 清单是单一真源（三端共用），这里只负责把 `Spec` 映射成 Libby 的 `Library`：
     * 传递依赖开关与 relocate 规则都由清单给出 —— 产物里对这些库的引用已被 Shadow
     * 改写成重定位名，漏掉任一 relocate 都会在运行期炸 `NoClassDefFoundError`。
     *
     * Adventure 例外：宿主自带 kyori（Paper/Leaf）时直接复用宿主类；只有 Spigot 这类
     * 不带的宿主才装载回退副本（见 [KyoriSupport]）。
     */
    private fun loadManifestLibraries() {
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
