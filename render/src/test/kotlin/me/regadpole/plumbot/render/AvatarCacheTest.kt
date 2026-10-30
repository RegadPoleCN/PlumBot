// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 头像磁盘缓存测试：缓存是"服务端预取"能够成立的前提 —— 没有它，列表类 route 会在渲染窗口内逐张去取头像。
 */
class AvatarCacheTest {

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "plumbot-avatar-cache-${System.nanoTime()}")
            .apply { mkdirs() }

    @Test
    fun `写入后可读回`() {
        val cache = AvatarCache(freshDir(), ttlMillis = 60_000)
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        assertNull(cache.read("qq:10001"), "未写入前应为未命中")
        cache.write("qq:10001", bytes)
        assertEquals(bytes.toList(), cache.read("qq:10001")?.toList())
    }

    @Test
    fun `不同键互不干扰且文件名不含原始键`() {
        val dir = freshDir()
        val cache = AvatarCache(dir, ttlMillis = 60_000)
        cache.write("qq:111", byteArrayOf(1))
        cache.write("qq:222", byteArrayOf(2))

        assertEquals(listOf<Byte>(1), cache.read("qq:111")?.toList())
        assertEquals(listOf<Byte>(2), cache.read("qq:222")?.toList())

        val names = dir.listFiles()?.map { it.name }.orEmpty()
        assertEquals(2, names.size)
        assertTrue(
            names.none { it.contains(":") || it.contains("qq") },
            "缓存文件名应为哈希，避免非法字符与信息泄漏：$names"
        )
    }

    @Test
    fun `过期条目视为未命中`() {
        val dir = freshDir()
        // TTL 不能小到"写入那一刻即过期"：写入时的清理会直接删掉文件，本用例就无法回拨时间了
        val cache = AvatarCache(dir, ttlMillis = 60)
        cache.write("qq:333", byteArrayOf(9))

        val cached = dir.listFiles()?.firstOrNull()
        assertNotNull(cached)
        // 把最后修改时间回拨到 2 秒前，模拟过期
        cached.setLastModified(System.currentTimeMillis() - 2000)

        assertNull(cache.read("qq:333"), "超过 TTL 应视为未命中")
    }

    @Test
    fun `ttl 为零表示永不过期`() {
        val dir = freshDir()
        val cache = AvatarCache(dir, ttlMillis = 0)
        cache.write("qq:444", byteArrayOf(7))

        val cached = dir.listFiles()?.firstOrNull()
        assertNotNull(cached)
        cached.setLastModified(System.currentTimeMillis() - 10L * 365 * 24 * 3600 * 1000)

        assertEquals(listOf<Byte>(7), cache.read("qq:444")?.toList())
    }

    @Test
    fun `不残留临时文件`() {
        val dir = freshDir()
        val cache = AvatarCache(dir, ttlMillis = 60_000)
        repeat(3) { cache.write("qq:$it", byteArrayOf(it.toByte())) }

        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "不应残留 .tmp 文件：${leftovers.map { it.name }}")
    }

    @Test
    fun `空文件视为未命中`() {
        val dir = freshDir()
        val cache = AvatarCache(dir, ttlMillis = 60_000)
        cache.write("qq:555", ByteArray(0))

        assertNull(cache.read("qq:555"), "空内容不应被当作有效缓存")
    }

    @Test
    fun `过期文件会被真正删除`() {
        val dir = freshDir()
        // TTL 不能小到"写入那一刻就已过期"，否则写入时的清理会立刻把新文件删掉
        val cache = AvatarCache(dir, ttlMillis = 60)
        cache.write("qq:666", byteArrayOf(9))
        Thread.sleep(150)

        assertNull(cache.read("qq:666"))

        val removed = cache.prune()
        assertTrue(removed >= 1, "prune 必须删除过期文件：旧实现只让读取落空，磁盘会无限增长")
        assertEquals(0, dir.listFiles()?.count { it.name.endsWith(".png") } ?: 0)
    }

    @Test
    fun `超出容量上限时按最旧优先清理`() {
        val dir = freshDir()
        val cache = AvatarCache(dir, ttlMillis = 0, maxFiles = 3)
        repeat(6) { cache.write("qq:$it", byteArrayOf(it.toByte())) }

        cache.prune()

        assertEquals(3, dir.listFiles()?.count { it.name.endsWith(".png") } ?: 0, "必须裁剪到容量上限")
    }

    @Test
    fun `同一键并发写入不会写出交错内容`() {
        val dir = freshDir()
        val cache = AvatarCache(dir, ttlMillis = 60_000)
        val a = ByteArray(4096) { 0x11 }
        val b = ByteArray(4096) { 0x22 }
        val start = java.util.concurrent.CountDownLatch(1)

        val threads = listOf(a, b).map { payload ->
            Thread {
                start.await()
                repeat(20) { cache.write("qq:shared", payload) }
            }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join() }

        val result = cache.read("qq:shared")
        assertNotNull(result, "并发写后应仍能读出内容")
        assertTrue(
            result.contentEquals(a) || result.contentEquals(b),
            "临时文件必须每写一次唯一命名：旧实现共用 <name>.tmp 会把两份内容交错写坏"
        )
    }
}
