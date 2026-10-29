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

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.PublicApi

@PublicApi
interface PlatformConfig {
    fun getString(vararg path: String?): String?
    fun getLong(vararg path: String?): Long
    fun getInteger(vararg path: String?): Int
    fun getBoolean(vararg path: String?): Boolean
    fun getLongList(vararg path: String?): List<Long>
    fun getStringList(vararg path: String?): List<String?>
    fun getSubConfig(vararg path: String?): PlatformConfig

    /**
     * 读取浮点配置项；缺失或非数值时返回 [Double.NaN]。
     *
     * 默认实现返回 NaN，由具体实现覆写（`AbstractConfigurator` 已覆写）。
     * 之所以需要它：YAML 的 `4.5` 是浮点节点，`getInteger` 会截断、`getString` 取不到值。
     */
    fun getDouble(vararg path: String?): Double = Double.NaN

    /**
     * 键是否存在（未被缺失）。
     *
     * 默认实现用 [getString] 判空 —— **纯读**，不会改变配置树；具体实现若有更精确的判据
     * （`AbstractConfigurator` 用 `node.virtual()`）应覆写。
     *
     * 之所以需要它：「显式配成 `false`」与「压根没配」必须能区分开，而 configurate 的
     * `getBoolean` / `boolean` 属性是**带默认值拷贝**的读法，会把缺失键实体化成 `false`，
     * 正好抹掉这个差别（详见 `AbstractConfigurator.getBoolean` 的 KDoc）。
     */
    fun contains(vararg path: String?): Boolean = getString(*path) != null

    /**
     * 键的**原始标量类型名**（`String` / `Boolean` / `Integer` / `Double` / `missing` / `null`）。
     *
     * 只用于诊断输出，默认 null = 未知。存在的意义是让「配置树被就地写坏」一眼可见：
     * 曾出现 `render.engine` 被布尔读取写回成 `Boolean false`，doctor 里所有值都成了 `false`。
     */
    fun rawTypeOf(vararg path: String?): String? = null
}
