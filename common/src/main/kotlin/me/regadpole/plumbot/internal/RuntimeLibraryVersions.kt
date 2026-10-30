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
 * 所有版本号都是 [gradle/libs.versions.toml] 中对应库版本的镜像，修改时必须同步更新，以保证编译期
 * 依赖与运行时下载一致。清单只覆盖 `common/build.gradle.kts` 中 `compileOnly` 的运行期库
 * （Kotlin 标准库/协程由产物内联）；各库的传递依赖**不随 POM 解析**，需要什么必须显式列出。
 */
object RuntimeLibraryVersions {
    const val SQLITE_JDBC = "3.49.0.0"
    const val MYSQL_CONNECTOR = "8.3.0"
    const val HIKARI_CP = "4.0.3"
    const val GUAVA = "21.0"
    const val SLF4J = "2.0.16"
    const val TABOOLIB_DATABASE = "1.0.2"
    const val AONE_BOT = "1.0.11-beta"
    const val GSON = "2.11.0"
    const val CONFIGURATE_CORE = "4.2.0"
    const val CONFIGURATE_YAML = "4.2.0"
    const val GEANTYREF = "1.3.16"
    const val KYORI_OPTION = "1.1.0"
    const val JAVA_WEBSOCKET = "1.5.7"

    /**
     * Adventure（kyori）版本：只在宿主不带 kyori 时由 Libby 装载（见 [KyoriSupport]），与
     * `gradle/libs.versions.toml` 的 adventureVersion / examination 版本同步。
     */
    const val ADVENTURE = "4.21.0"
    const val EXAMINATION_API = "1.3.0"

    /**
     * 缓存（[me.regadpole.plumbot.cache.DefaultBotCache] 等）使用。
     *
     * aedile-core 自身也引用 caffeine（POM 声明 3.1.8 为 compile 依赖），而运行期关闭了传递解析，
     * 因此 caffeine 必须在清单里**显式装载并重定位**，产物里对这些类的引用已改写为
     * `me.regadpole.plumbot.lib.com.github.benmanes.caffeine...`。升级 aedile 时必须同步核对
     * 它声明的 caffeine 版本。
     */
    const val AEDILE = "2.0.3"
    const val CAFFEINE = "3.1.8"

    /**
     * Playwright（高清渲染引擎）：**仅在 `engine=playwright` 且真正出图时**才由
     * [me.regadpole.plumbot.render.RenderLibraryLoader] 按需下载装载 —— 默认 `engine=awt` 时
     * 零下载、零装载、类不加载。
     */
    const val PLAYWRIGHT = "1.49.0"
}
