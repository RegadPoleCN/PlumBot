// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import com.microsoft.playwright.Page
import me.regadpole.plumbot.api.platform.PlatformContext

/** manifest 校验结果。 */
internal sealed interface ManifestCheck {
    data object Ok : ManifestCheck
    data class Invalid(val detail: String) : ManifestCheck
}

/**
 * 模板 manifest 校验器（每次握手刷新一次）。
 *
 * 缺少 manifest、缺少 protocol、协议不一致、route 未实现都算 `PW_TEMPLATE_INVALID`，
 * 不以"旧模板兼容"为名继续注入数据并等到超时。
 *
 * 校验成功后把 `routes` 声明记进 [templateRoutes]，供 [PlaywrightImageRenderer.supportsRoute]
 * 在**下次渲染之前**选对路由（头像卡依赖这一点直接走兼容路由）。
 */
internal class ManifestVerifier(private val context: PlatformContext) {

    /**
     * 模板声明的可渲染路由。
     *
     * - `null`：还没读到 manifest（尚未成功握手）—— 见 [PlaywrightImageRenderer.supportsRoute]；
     * - 空集：模板没写 `routes` 字段，视为**不限制**任何路由；
     * - 非空：只认这些路由。
     */
    @Volatile
    var templateRoutes: Set<String>? = null
        private set

    /** 主题可能换了另一份模板构建，旧的 routes 声明不再可信；置空后退回"未知"。 */
    fun reset() {
        templateRoutes = null
    }

    /** 读取并校验模板 manifest。 */
    fun verify(page: Page, viewRoute: String): ManifestCheck {
        val raw = try {
            page.evaluate("() => window.__PLUMBOT_MANIFEST__ || null")
        } catch (e: Throwable) {
            return ManifestCheck.Invalid("读取 window.__PLUMBOT_MANIFEST__ 失败：" + (e.message ?: e::class.java.simpleName))
        }
        val manifest = raw as? Map<*, *>
            ?: return ManifestCheck.Invalid("模板未声明 window.__PLUMBOT_MANIFEST__（前端 SDK 的 installManifest 未执行）")

        val declaredProtocol = (manifest["protocol"] as? Number)?.toInt()
            ?: return ManifestCheck.Invalid("manifest 缺少 protocol 字段（模板 SDK 版本过旧或构建被裁剪）")

        val expectedProtocol = context.config
            .getInteger("render", "playwright", "template_protocol")
            .let { if (it <= 0) EXPECTED_PROTOCOL else it }

        if (declaredProtocol != expectedProtocol) {
            return ManifestCheck.Invalid(
                "模板协议版本 " + declaredProtocol + " 与插件期望的 " + expectedProtocol + " 不匹配"
            )
        }

        val declaredRoutes = (manifest["routes"] as? List<*>)?.filterIsInstance<String>()
        // 记录给 supportsRoute()：调用方（如头像卡）要靠它在**下次渲染之前**选对路由。
        // 缺 routes 字段按"不限制"记为空集，与下面的放行逻辑保持一致。
        templateRoutes = declaredRoutes?.toSet() ?: emptySet()
        if (!declaredRoutes.isNullOrEmpty() && viewRoute !in declaredRoutes) {
            return ManifestCheck.Invalid(
                "模板未实现 route '" + viewRoute + "'（已实现：" + declaredRoutes.joinToString(",") + "）"
            )
        }
        return ManifestCheck.Ok
    }

    private companion object {
        /** 与 render-protocol.json 的 protocol 字段保持一致。 */
        const val EXPECTED_PROTOCOL = 1
    }
}
