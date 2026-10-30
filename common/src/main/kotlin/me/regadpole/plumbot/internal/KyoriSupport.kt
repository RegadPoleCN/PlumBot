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
 * Adventure（kyori）装载策略：**宿主优先，缺失才由 Libby 装载**。
 *
 * 原因：把 kyori 打进产物再重定位，会让插件里的 `Component` 与宿主（Paper/Velocity）的不是
 * 同一个类，把重定位后的组件传给宿主 API 会直接 `NoSuchMethodError` / `NoClassDefFoundError`
 * —— 重定位解决不了这类跨 API 边界问题，只能共享宿主那一份。
 *
 * 因此产物里**不含**任何 `net.kyori` 类、也不再重定位；启动时先探测宿主是否已提供
 * `net.kyori.adventure.text.Component`：提供（Paper/Leaf/Velocity）就什么都不装；未提供
 * （Spigot/Hytale）则装载 [RuntimeLibraries.KYORI_FALLBACK]（原包名）。
 *
 * 探测必须在任何 Libby 装载**之前**、且针对插件自身的类加载器进行，否则会探测到我们刚装进去的
 * 回退副本，失去「宿主是否自带」的语义。
 */
object KyoriSupport {

    /** 用来判断宿主是否提供 Adventure 的类名（最稳定的锚点）。 */
    const val ADVENTURE_COMPONENT_CLASS = "net.kyori.adventure.text.Component"

    /**
     * 宿主类加载器里能否解析 Adventure 的 `Component`。用 `initialize = false` 只做可见性探测，
     * 不触发静态初始化；解析失败按「宿主未提供」处理。
     */
    fun isProvidedByHost(loader: ClassLoader): Boolean =
        runCatching { Class.forName(ADVENTURE_COMPONENT_CLASS, false, loader) }.isSuccess

    /**
     * 本次启动应装载的清单：宿主自带 kyori 时就是 [RuntimeLibraries.ALL]，否则把回退项排在前面，
     * 保证任何引用 Adventure 的库加载时类已可见。
     */
    fun manifestFor(hostLoader: ClassLoader): List<RuntimeLibraries.Spec> =
        if (isProvidedByHost(hostLoader)) {
            RuntimeLibraries.ALL
        } else {
            RuntimeLibraries.KYORI_FALLBACK + RuntimeLibraries.ALL
        }
}
