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

package me.regadpole.plumbot.internal.config

import me.regadpole.plumbot.render.RenderRoute
import org.spongepowered.configurate.CommentedConfigurationNode

/**
 * 渲染图片门控的**旧键 → 新键**迁移。
 *
 * ## 为什么需要它
 *
 * 基线要求把门控统一到 `feature.img.views.<route>.pic`。但 `PlatformConfig.getBoolean`
 * 返回的是**非空 Boolean**，无法区分"键缺失"与"显式 false"。若直接改读新键，
 * 所有现存用户的图片会被**静默关闭**（旧键还在，但已无人读）。
 *
 * 因此采用"先迁移、后读取"：
 * 1. 合并内置默认模板**之前**，记录每条 route 的新键是否缺失；
 * 2. 合并之后，对这些新键用**旧键的实际取值**（而非默认值）填充；
 * 3. 于是新键必然存在，`getBoolean` 的语义才可靠，且升级后行为与升级前一致。
 *
 * 无独立门控的 route（`legacyGate == null`，如通用 `text`、`hytaleAvatar`）
 * 不参与迁移 —— 它们由调用方决定是否出图。
 */
internal object RenderGateMigration {

    private const val ROOT = "feature"
    private const val IMG = "img"
    private const val VIEWS = "views"
    private const val PIC = "pic"

    /** 需要迁移的 route（有旧门控的那些）。 */
    private fun gatedRoutes(): List<RenderRoute> = RenderRoute.entries.filter { it.legacyGate != null }

    /**
     * 合并默认模板**之前**调用：返回"新键当前缺失"的 route 列表。
     */
    fun pendingRoutes(root: CommentedConfigurationNode): List<RenderRoute> =
        gatedRoutes().filter { route ->
            root.node(ROOT, IMG, VIEWS, route.route, PIC).virtual()
        }

    /**
     * 合并默认模板**之后**调用：用旧键的实际取值填充 [pending] 中的新键。
     *
     * @return true 表示有键被改写（调用方据此决定是否落盘）
     */
    fun applyLegacyValues(root: CommentedConfigurationNode, pending: List<RenderRoute>): Boolean {
        var modified = false
        pending.forEach { route ->
            val gate = route.legacyGate ?: return@forEach
            val legacy = root.node(ROOT, gate, PIC)
            // 旧键不存在（或本身就是新装的默认值）时保留默认模板写入的值
            if (legacy.virtual()) return@forEach
            root.node(ROOT, IMG, VIEWS, route.route, PIC).set(legacy.boolean)
            modified = true
        }
        return modified
    }
}
