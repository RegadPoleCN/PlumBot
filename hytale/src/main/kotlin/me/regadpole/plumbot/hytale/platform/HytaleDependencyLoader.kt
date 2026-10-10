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
import me.regadpole.plumbot.internal.RuntimeLibraries
import me.regadpole.plumbot.internal.RuntimeLibraryVersions
import me.regadpole.plumbot.render.RenderLibraryLoader

class HytaleDependencyLoader(
    plugin: JavaPlugin,
    private val configProvider: () -> PlatformConfig?
) : RenderLibraryLoader {
    private val libraryManager by lazy { HytaleLibraryManager(plugin) }

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

        // 与 Bukkit/Velocity 使用同一份清单：此前只装数据库驱动，
        // 导致 Hytale 端无法解析 config.yml，且产物中已重定位的引用无对应类。
        RuntimeLibraries.ALL.forEach { spec ->
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
