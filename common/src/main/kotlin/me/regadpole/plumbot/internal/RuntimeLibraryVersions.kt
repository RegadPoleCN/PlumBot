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
 * 运行时通过 Libby 下载的依赖版本。
 *
 * 本文件中所有版本号均为 [gradle/libs.versions.toml] 中对应库版本的镜像，
 * 修改时必须同步更新 gradle/libs.versions.toml，以保证编译期依赖与运行时下载依赖一致。
 */
object RuntimeLibraryVersions {
    const val SQLITE_JDBC = "3.49.0.0"
    const val MYSQL_CONNECTOR = "8.3.0"
    const val HIKARI_CP = "4.0.3"
    const val GUAVA = "21.0"
    const val TABOOLIB_DATABASE = "1.0.2"
    const val AONE_BOT = "1.0.11-beta"
    const val GSON = "2.11.0"
    const val CONFIGURATE_YAML = "4.2.0"
    const val CONFIGURATE_EXTRA_KOTLIN = "4.2.0"

    /**
     * 协程缓存（[me.regadpole.plumbot.cache.DefaultBotCache] 等）使用。
     *
     * 注意：caffeine 是 aedile 的**传递依赖**（见 `libs.versions.toml` 的 aedile 条目），
     * 但运行期必须**显式装载并重定位**：产物里对这些类的引用已被改写为
     * `me.regadpole.plumbot.lib.com.github.benmanes.caffeine...`，仅靠传递依赖拿到的是未重定位版本。
     * 升级 aedile 时必须同步核对它声明的 caffeine 版本。
     */
    const val AEDILE = "2.0.3"
    const val CAFFEINE = "3.1.8"

    /**
     * Playwright（高清渲染引擎）。
     *
     * **仅在 `engine=playwright` 且真正出图时**才由 [me.regadpole.plumbot.render.RenderLibraryLoader]
     * 按需下载并装载 —— 默认 `engine=awt` 时零下载、零装载、类不加载。
     */
    const val PLAYWRIGHT = "1.49.0"
}
