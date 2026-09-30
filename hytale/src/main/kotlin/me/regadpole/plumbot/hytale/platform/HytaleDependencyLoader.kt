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
import me.regadpole.plumbot.internal.RuntimeLibraryVersions

class HytaleDependencyLoader(
    plugin: JavaPlugin,
    private val configProvider: () -> PlatformConfig?
) {
    private val libraryManager by lazy { HytaleLibraryManager(plugin) }

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
