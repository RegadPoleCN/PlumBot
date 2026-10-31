// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

/**
 * Chromium 沙箱开关。
 *
 * 渲染的是**模板页面**（可被第三方替换），一旦模板被投毒，无沙箱即等于进程级攻击面，
 * 故**默认开启沙箱**：不传 `--no-sandbox`，交给 Chromium 自身的隔离机制。
 *
 * 容器里 Chromium 常因缺少 `CAP_SYS_ADMIN` / user namespace 而启动失败，此时**不会静默降级**
 * （那等于悄悄关掉防线），而是给出告警与指引，由管理员显式设置 `disable_sandbox`。
 * 键取反向命名是为了**键缺失时等于沙箱开启** —— 正向键一旦被误删就会静默变成无沙箱。
 *
 * 逻辑抽成纯函数以便单测（本机无法真的启动 Chromium）。
 */
internal object PlaywrightSandbox {

    const val NO_SANDBOX = "--no-sandbox"
    const val NO_SETUID_SANDBOX = "--disable-setuid-sandbox"

    /** 沙箱关闭时需要追加的参数；沙箱开启时**不追加任何东西**。 */
    fun args(sandboxDisabled: Boolean): List<String> =
        if (sandboxDisabled) listOf(NO_SANDBOX, NO_SETUID_SANDBOX) else emptyList()

    /**
     * 判定启动失败是否"看起来是沙箱问题"：只做**提示性**匹配（Chromium 在受限环境下的
     * 报错形态很杂：SUID sandbox helper、clone/namespace 权限、`No usable sandbox`），
     * 命中时给出针对性指引，不改变"失败即降级为纯文本"的行为。
     */
    fun looksLikeSandboxFailure(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase()
        return listOf(
            "sandbox",
            "suid",
            "namespace",
            "clone",
            "operation not permitted",
            "no usable sandbox",
        ).any { lower.contains(it) }
    }

    /** 沙箱不可用时给管理员的操作指引（明确"显式"二字，避免被当成自动降级）。 */
    fun sandboxFailureHint(): String =
        "Chromium 沙箱启动失败（常见于容器：缺少 CAP_SYS_ADMIN / user namespace）。" +
            "如确认进程隔离由容器提供，可**显式**设置 render.playwright.disable_sandbox: true 关闭沙箱；" +
            "否则本次渲染会降级为纯文本（不会静默出图）。"

    /** 显式关闭沙箱时的告警文案。 */
    fun disabledWarning(): String =
        "[PlumBot-Render] Chromium 沙箱已被显式关闭（render.playwright.disable_sandbox=true）：" +
            "请确认模板来源可信，且进程隔离由容器/编排层提供。"
}
