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

package me.regadpole.plumbot.hytale.platform

import com.alessiodp.libby.HytaleLibraryManager
import com.alessiodp.libby.Library
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.internal.KyoriSupport
import me.regadpole.plumbot.internal.RuntimeLibraries
import me.regadpole.plumbot.render.RenderLibraryLoader

class HytaleDependencyLoader(
    private val plugin: JavaPlugin,
    private val configProvider: () -> PlatformConfig?
) : RenderLibraryLoader {
    private val libraryManager by lazy { HytaleLibraryManager(plugin) }

    @Volatile
    private var playwrightReady = false

    /** 最近一次 Playwright 装载失败的原因（供 `/plumbot render doctor` 展示）；成功后清空。 */
    @Volatile
    private var playwrightFailure: String? = null

    /**
     * 按需下载并装载 Playwright 运行时（接口 / 原生 node 驱动 / API 三件，
     * 清单见 [RuntimeLibraries.PLAYWRIGHT]）。
     *
     * 只允许异步线程调用：`driver-bundle` 约 190 MB，首次调用即一次真实下载。
     * **禁止 relocate** —— Playwright 依赖其原生 node driver 的路径结构。
     */
    override fun ensurePlaywright(): Boolean {
        if (playwrightReady) return true
        // 主线程守卫。Hytale 的 tick 线程由 [HytalePlatformScheduler] 登记，
        // 命中说明当前调用会卡住世界 tick；一次约 190 MB 的下载绝不允许发生在这里。
        if (HytaleTickThreads.isOnGameThread()) {
            playwrightFailure = "ensurePlaywright() 在世界 tick 线程被调用，已拒绝下载（会卡住世界 tick）；请由异步泳道触发 /plumbot render doctor"
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

        config?.getStringList("libraries", "custom_repositories")
            ?.filterNotNull()
            ?.map { it.trim().removeSuffix("/") }
            ?.filter { it.isNotBlank() }
            ?.forEach { libraryManager.addRepository(it) }

        val useAliyun = config?.getBoolean("libraries", "use_aliyun_mirror") ?: true
        if (useAliyun) {
            libraryManager.addRepository("https://maven.aliyun.com/repository/public")
        }

        libraryManager.addMavenCentral()
        libraryManager.addJitPack()
        libraryManager.addHytaleModding()

        // 与 Bukkit/Velocity 共用同一份清单：只装数据库驱动时 Hytale 端连 config.yml
        // 都解析不了，产物里已重定位的引用也没有对应类。Hytale 宿主不带 kyori，由
        // KyoriSupport 补原包名运行时；HYTALE_ONLY 只多一个 SLF4J provider，缺了日志
        // 会整晚静默。
        (KyoriSupport.manifestFor(plugin.javaClass.classLoader) + RuntimeLibraries.HYTALE_ONLY).forEach { spec ->
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
