// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api

/**
 * 宿主插件的平台中立视图。
 * 接入新平台只需新增一层适配器包装平台原生插件对象；第三方插件始终面向本契约。
 */
@PublicApi
interface Plugin {
    val name: String
    val version: String
    val isEnabled: Boolean
}
