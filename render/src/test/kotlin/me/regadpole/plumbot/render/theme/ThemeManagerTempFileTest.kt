// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.theme

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 模板下载临时文件契约：每次调用唯一命名。reload 期间旧实例仍在下载时，
 * 新实例会并发写同一目标路径——固定 "<name>.tmp" 会交错写坏模板。
 */
class ThemeManagerTempFileTest {

    @Test
    fun `临时文件每次唯一且落在目标目录`() {
        val dir = Files.createTempDirectory("plumbot-theme-tmp")
        val dest = dir.resolve("index.html").toFile()
        val seen = HashSet<String>()
        repeat(64) {
            val temp = newTemplateTempFile(dest, dir.toFile())
            assertTrue(seen.add(temp.absolutePath), "两次创建的临时文件必须不同名")
            assertEquals(dir.toFile().canonicalFile, temp.parentFile.canonicalFile)
            assertTrue(temp.name.endsWith(".tmp"), temp.name)
        }
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `并发创建互不冲突`() {
        val dir = Files.createTempDirectory("plumbot-theme-tmp-cc")
        val dest = dir.resolve("index.html").toFile()
        val start = CountDownLatch(1)
        val names = java.util.Collections.synchronizedCollection(ArrayList<String>())
        val threads = (1..16).map {
            Thread {
                start.await()
                repeat(8) { names.add(newTemplateTempFile(dest, dir.toFile()).absolutePath) }
            }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(128, names.size)
        assertEquals(128, names.toSet().size, "并发创建的临时文件必须全部不同名")
        dir.toFile().deleteRecursively()
    }
}