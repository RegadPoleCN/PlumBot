// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.utils

object HttpConstants {
    /** 模拟主流桌面浏览器 UA，避免云端源被 CDN/WAF 阻断或触发防爬。 */
    const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val ACCEPT_JSON_OR_TEXT = "application/json, text/plain, */*"
}
