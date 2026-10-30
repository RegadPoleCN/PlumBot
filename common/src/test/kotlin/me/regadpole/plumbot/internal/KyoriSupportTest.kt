// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 宿主优先的 kyori 装载策略：宿主有就共享，没有才回退装载。插件自带一份（无论是否重定位）
 * `Component` 就无法交给宿主 API —— Velocity 的 `/plumbot status` 曾因此 `NoClassDefFoundError`。
 */
class KyoriSupportTest {

    /** 模拟「宿主完全没有 Adventure」的类加载器（父加载器为 bootstrap）。 */
    private class EmptyLoader : ClassLoader(null) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> =
            throw ClassNotFoundException(name)
    }

    @Test
    fun `host that provides adventure gets the plain manifest`() {
        val loader = KyoriSupportTest::class.java.classLoader
        assertTrue(KyoriSupport.isProvidedByHost(loader), "测试类路径必须能解析 adventure（testImplementation adventure-api）")
        assertEquals(RuntimeLibraries.ALL, KyoriSupport.manifestFor(loader))
    }

    @Test
    fun `host without adventure gets the fallback first`() {
        val loader = EmptyLoader()
        assertFalse(KyoriSupport.isProvidedByHost(loader))
        assertEquals(RuntimeLibraries.KYORI_FALLBACK + RuntimeLibraries.ALL, KyoriSupport.manifestFor(loader))
    }

    @Test
    fun `detection does not initialize the class`() {
        // 探测只回答「可见性」，不能触发 Component 的静态初始化（无副作用是这条策略的前提）
        val loader = KyoriSupportTest::class.java.classLoader
        assertTrue(KyoriSupport.isProvidedByHost(loader))
        assertTrue(KyoriSupport.isProvidedByHost(loader), "重复探测必须幂等")
    }
}
