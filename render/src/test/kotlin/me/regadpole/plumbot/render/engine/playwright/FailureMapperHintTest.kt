// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import me.regadpole.plumbot.render.theme.ThemeManager

/**
 * 排障提示必须指向真实存在的配置键：守卫白名单实际读 render.avatar.domains
 * （RenderSecurity.allowedHostsFrom），按提示改配置必须真的生效。
 */
class FailureMapperHintTest {

    private val mapper = FailureMapper({ error("本测试不触发主题管理器") as ThemeManager })

    @Test
    fun `导航失败提示指向真实的白名单配置键`() {
        val e = mapper.mapStageFailure("navigate", "status", "default", RuntimeException("boom"))
        val hint = requireNotNull(e.hint)
        assertContains(hint, "render.avatar.domains")
        assertFalse(hint.contains("render.allowed_hosts"), "不得再指向不存在的配置键")
    }

    @Test
    fun `请求守卫注册失败提示指向真实的白名单配置键`() {
        // requestGuardFailed 只构造异常，由调用方（EngineLifecycle 的 catch 块）抛出。
        val e = mapper.requestGuardFailed("注册请求拦截规则失败：mock", RuntimeException("boom"))
        val hint = requireNotNull(e.hint)
        assertContains(hint, "render.avatar.domains")
        assertFalse(hint.contains("render.allowed_hosts"), "不得再指向不存在的配置键")
    }
}