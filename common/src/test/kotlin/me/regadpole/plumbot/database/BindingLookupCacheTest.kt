// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 登录校验缓存的边界：键归一化、负缓存、按名字/按 QQ 失效、容量淘汰。
 *
 * 这些细节直接决定「改了绑定表之后登录还认不认」—— 缓存不失效就是线上事故，
 * 没有失效语义的话连测试都说不清它是不是对的。
 */
class BindingLookupCacheTest {

    @AfterTest
    fun cleanup() {
        BindingLookupCache.clear()
    }

    @Test
    fun `put then get returns the stored value and timestamp`() {
        BindingLookupCache.put("Steve", "10001", 1_234L)

        val entry = assertNotNull(BindingLookupCache.get("Steve"))
        assertEquals("10001", entry.userId)
        assertEquals(1_234L, entry.storedAt)
    }

    @Test
    fun `the lookup key ignores case and surrounding spaces`() {
        BindingLookupCache.put(" Steve ", "10001", 1L)

        val entry = assertNotNull(BindingLookupCache.get("steve"))
        assertEquals("10001", entry.userId)
    }

    @Test
    fun `a negative entry is stored with a null value`() {
        BindingLookupCache.put("Alex", null, 9L)

        val entry = assertNotNull(BindingLookupCache.get("Alex"))
        assertNull(entry.userId, "userId 为 null 才表示确认未绑定")
        assertEquals(9L, entry.storedAt)
    }

    @Test
    fun `invalidate removes only the named entry`() {
        BindingLookupCache.put("Steve", "10001", 1L)
        BindingLookupCache.put("Alex", "10002", 1L)

        BindingLookupCache.invalidate("steve")

        assertNull(BindingLookupCache.get("Steve"))
        assertNotNull(BindingLookupCache.get("Alex"))
    }

    @Test
    fun `invalidateUser drops every name bound to that account`() {
        BindingLookupCache.put("Steve", "10001", 1L)
        BindingLookupCache.put("Notch", "10001", 1L)
        BindingLookupCache.put("Alex", "10002", 1L)

        BindingLookupCache.invalidateUser("10001")

        assertNull(BindingLookupCache.get("Steve"))
        assertNull(BindingLookupCache.get("Notch"))
        assertNotNull(BindingLookupCache.get("Alex"))
        assertEquals(1, BindingLookupCache.size())
    }

    @Test
    fun `capacity evicts the least recently used entry`() {
        repeat(BindingLookupCache.CAPACITY) { i -> BindingLookupCache.put("player$i", "u$i", 0L) }
        assertEquals(BindingLookupCache.CAPACITY, BindingLookupCache.size())

        BindingLookupCache.put("overflow", "u-overflow", 0L)

        assertEquals(BindingLookupCache.CAPACITY, BindingLookupCache.size(), "容量不得越过上限")
        assertNull(BindingLookupCache.get("player0"), "最久未用的条目应被淘汰")
        assertNotNull(BindingLookupCache.get("overflow"))
    }

    @Test
    fun `clear empties the cache`() {
        BindingLookupCache.put("Steve", "10001", 1L)
        BindingLookupCache.put("Alex", null, 1L)

        BindingLookupCache.clear()

        assertEquals(0, BindingLookupCache.size())
        assertNull(BindingLookupCache.get("Steve"))
    }
}
