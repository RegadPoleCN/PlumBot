// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 关闭钩子登记处：必须"跑全、跑完即清、互不牵连"。 */
class ShutdownRegistryTest {

    @AfterTest
    fun cleanup() {
        ShutdownRegistry.clear()
    }

    @Test
    fun `runs every hook in reverse registration order`() {
        val order = mutableListOf<String>()
        ShutdownRegistry.register("first") { order += "first" }
        ShutdownRegistry.register("second") { order += "second" }

        assertEquals(0, ShutdownRegistry.runAll())
        assertEquals(listOf("second", "first"), order, "后创建的资源应先释放（LIFO）")
    }

    @Test
    fun `a throwing hook does not block the remaining hooks`() {
        var secondRan = false
        ShutdownRegistry.register("boom") { throw IllegalStateException("boom") }
        ShutdownRegistry.register("second") { secondRan = true }

        assertEquals(1, ShutdownRegistry.runAll(), "失败数量必须回报，便于平台侧告警")
        assertTrue(secondRan, "一个组件释放失败不能阻止其它组件被回收")
    }

    @Test
    fun `unregistered owner is not executed`() {
        var ran = false
        val owner = Any()
        ShutdownRegistry.register(owner) { ran = true }

        ShutdownRegistry.unregister(owner)

        assertEquals(0, ShutdownRegistry.runAll())
        assertTrue(!ran, "已注销的钩子不得再执行")
    }

    @Test
    fun `registering the same owner twice executes it once`() {
        var count = 0
        val owner = Any()
        ShutdownRegistry.register(owner) { count++ }
        ShutdownRegistry.register(owner) { count++ }

        ShutdownRegistry.runAll()
        assertEquals(1, count, "同一对象重复登记应被覆盖，而不是堆积")
    }

    @Test
    fun `registry is cleared after a run`() {
        ShutdownRegistry.register("once") { }
        ShutdownRegistry.runAll()

        assertEquals(0, ShutdownRegistry.size(), "执行后不得残留登记（否则会重复释放）")
    }
}
