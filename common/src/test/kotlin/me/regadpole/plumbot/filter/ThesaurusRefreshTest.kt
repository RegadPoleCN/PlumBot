/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.filter

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 词库快照的落盘与"空结果保护"策略。
 *
 * 背景：快照是**云端不可达时的降级数据源**，被写坏等于把离线退路一起毁掉；
 * 而"这次一个词都没取到"如果直接把生效词库置空，就是**过滤静默关闭**且无提示。
 */
class ThesaurusRefreshTest {

    @Test
    fun `原子落盘只留下完整内容且不残留临时文件`() {
        val dir = Files.createTempDirectory("plumbot-thesaurus").toFile()
        val target = File(dir, "source_abc.json")
        target.writeText("old")

        ThesaurusCache.writeAtomically(target, """{"words":["a","b"]}""")

        assertEquals("""{"words":["a","b"]}""", target.readText())
        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "不应残留临时文件：${leftovers.map { it.name }}")
    }

    @Test
    fun `原子落盘在目录不存在时会创建目录`() {
        val dir = Files.createTempDirectory("plumbot-thesaurus").toFile()
        val target = File(File(dir, "nested"), "source_x.json")

        ThesaurusCache.writeAtomically(target, "{}")

        assertTrue(target.isFile, "目标文件应被创建（含父目录）")
    }

    @Test
    fun `配置了云端源却取不到词时保留旧词库`() {
        assertTrue(
            shouldKeepPreviousMatcher(previousExists = true, newWordCount = 0, configuredSourceCount = 2),
            "有源可取却取不到（网络全挂/响应损坏）时必须保留旧词库，否则过滤被静默关闭"
        )
    }

    @Test
    fun `用户主动清空词库时照常置空`() {
        assertFalse(
            shouldKeepPreviousMatcher(previousExists = true, newWordCount = 0, configuredSourceCount = 0),
            "没配置任何源说明是主动清空，不应继续挂旧词库"
        )
    }

    @Test
    fun `正常取到词时替换词库`() {
        assertFalse(
            shouldKeepPreviousMatcher(previousExists = true, newWordCount = 12, configuredSourceCount = 1)
        )
        assertFalse(
            shouldKeepPreviousMatcher(previousExists = false, newWordCount = 0, configuredSourceCount = 1),
            "之前没有词库时无可保留"
        )
    }
}
