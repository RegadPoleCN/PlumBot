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
import me.regadpole.plumbot.internal.KyoriSupport
import me.regadpole.plumbot.internal.RuntimeLibraries
import me.regadpole.plumbot.internal.RuntimeLibraryVersions
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.render.RenderLibraryLoader
import org.bukkit.plugin.java.JavaPlugin

class BukkitDependencyLoader(
    private val plugin: JavaPlugin,
    private val configProvider: () -> PlatformConfig?
) : RenderLibraryLoader {
    private val libraryManager: BukkitLibraryManager by lazy { BukkitLibraryManager(plugin) }

    @Volatile
    private var playwrightReady = false

    /**
     * 按需下载并装载 Playwright（仅 `engine=playwright` 且真正出图时才会被调用）。
     *
     * 单飞、只记成功：失败不缓存，交由门户的冷却策略控制重试节奏。
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
