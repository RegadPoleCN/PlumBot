// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.config.YamlConfigurator
import taboolib.module.database.Database
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 真实 SQLite 上的绑定表测试。
 *
 * 存在意义：此前 `AbstractBindingDatabase` 的表名取自**子类覆写的属性**，
 * 而 `table` 在基类构造期初始化 —— 读出 null 会让 `Table(name, host)` 抛 NPE，
 * 插件在任何后端都起不来；而当时没有任何测试会真正构造数据库实例。
 */
class SqliteBindingDatabaseTest {

    private val tempDir: Path = Files.createTempDirectory("plumbot-db-test")
    private val databases = mutableListOf<IDatabase>()

    /**
     * taboolib 的连接池从**全局** [Database.settingsFile] 读取 datasource.yml；
     * 生产路径在 `PlumBot.loadDatabase()` 里设置它，测试必须复现同一步，
     * 否则 `createDataSource()` 会抛 `lateinit property settingsFile has not been initialized`。
     */
    private fun ensureSettingsFile() {
        if (!settingsInitialized) {
            val config = YamlConfigurator.createConfig(tempDir, "datasource.yml")
                ?: error("测试无法加载 datasource.yml")
            Database.settingsFile = DatabaseSource(config.getNode())
            settingsInitialized = true
        }
    }

    private fun newDatabase(): AbstractBindingDatabase<*, *> {
        ensureSettingsFile()
        val db = SQLite(tempDir.resolve("binding.db").absolutePathString())
        databases += db
        return db
    }

    private companion object {
        var settingsInitialized = false
    }

    @AfterTest
    fun cleanup() {
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        runCatching {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `construction does not throw and creates expected table`() {
        val db = newDatabase()
        db.initialize()

        val columns = columnsOf(db)

        assertEquals(
            listOf("id", "user_id", "player_name", "player_uuid", "binding_time", "source_group"),
            columns,
            "绑定表列必须与 IDatabase 契约一致（缺列会让后续 select 直接失败）"
        )
    }

    @Test
    fun `initialize is repeatable`() {
        val db = newDatabase()
        db.initialize()
        db.initialize()
    }

    @Test
    fun `close is idempotent`() {
        val db = newDatabase()
        db.initialize()
        db.close()
        db.close()
    }

    @Test
    fun `close without initialize does not throw`() {
        val db = newDatabase()
        db.close()
    }

    @Test
    fun `binding round trip works on a real database`() {
        val db = newDatabase()
        db.initialize()

        db.addBind("10001", "Steve")

        val byName = assertNotNull(db.getByName("Steve"), "刚写入的绑定必须可按玩家名查到")
        assertEquals("10001", byName.userId, "查询到的绑定必须属于写入的 QQ")

        val byId = assertNotNull(db.getById(byName.id), "必须能按记录 id 查到同一条绑定")
        assertEquals("steve", byId.playerName, "玩家名按契约归一化为小写存储")

        assertEquals(1, db.getByUser("10001").size, "按 QQ 查也必须命中")
    }

    @Test
    fun `removing a binding by number returns the removed player name`() {
        val db = newDatabase()
        db.initialize()

        db.addBind("10001", "Steve")
        val id = assertNotNull(db.getByName("Steve")).id

        assertEquals("steve", db.removeBindByNum("10001", id), "删除时应回传被删角色的玩家名（小写存储）")
        assertNull(db.getByName("Steve"), "删除后不应再查到")
        assertTrue(db.getByUser("10001").isEmpty(), "删除后按 QQ 查应为空")
    }

    @Test
    fun `unknown player name returns null instead of throwing`() {
        val db = newDatabase()
        db.initialize()
        assertNull(db.getByName("Nobody"))
    }

    @Test
    fun `lookup by player uuid finds the bound account`() {
        val db = newDatabase()
        db.initialize()

        val uuid = UUID.randomUUID()
        db.addBind("10001", "Steve")
        db.setUUID("Steve", uuid)

        val found = assertNotNull(db.getByPlayerUuid(uuid.toString()), "已记录 UUID 的角色必须能反查到绑定")
        assertEquals("10001", found.userId)
        assertEquals("steve", found.playerName, "反查结果同样按小写存储")
    }

    @Test
    fun `lookup by player uuid returns null when unknown or blank`() {
        val db = newDatabase()
        db.initialize()

        db.addBind("10001", "Steve")
        assertNull(db.getByPlayerUuid(UUID.randomUUID().toString()), "未记录的 UUID 不应命中")
        assertNull(db.getByPlayerUuid("   "), "空白 UUID 直接返回 null，不该查库")
    }

    @Test
    fun `lookup by player uuid returns null for a binding that never joined`() {
        val db = newDatabase()
        db.initialize()

        // 只有进过服的角色才会补写 player_uuid；从未进服的记录必须查不到，而不是抛异常
        db.addBind("10001", "Steve")
        assertNull(db.getByPlayerUuid(UUID.randomUUID().toString()))
    }

    @Test
    fun `addBindIfAbsent is idempotent per player name`() {
        val db = newDatabase()
        db.initialize()

        assertTrue(db.addBindIfAbsent("10001", "Steve"), "首次写入应当成功")
        assertFalse(db.addBindIfAbsent("10002", "Steve"), "同一角色名不得再占一条绑定")
        assertEquals(1, db.getByUser("10001").size)
        assertTrue(db.getByUser("10002").isEmpty(), "重复写入不应产生第二条记录")
    }

    @Test
    fun `unique index blocks direct duplicate inserts`() {
        val db = newDatabase()
        db.initialize()
        db.addBind("10001", "Steve")

        val failed = runCatching {
            db.getConnection().connection.use { conn ->
                conn.createStatement().use { st ->
                    st.executeUpdate(
                        "INSERT INTO $BINDING_TABLE_NAME (user_id, player_name, binding_time) VALUES ('10002', 'steve', '0')"
                    )
                }
            }
        }.isFailure

        assertTrue(failed, "唯一索引必须挡住绕过业务层的重复写入")
    }

    @Test
    fun `legacy duplicates skip index creation without deleting data`() {
        val db = newDatabase()
        db.initialize()

        // 模拟历史脏数据：先摘掉索引，再写入重复角色名
        db.getConnection().connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate("DROP INDEX IF EXISTS idx_binding_player_name")
                st.executeUpdate("INSERT INTO $BINDING_TABLE_NAME (user_id, player_name, binding_time) VALUES ('10001', 'steve', '0')")
                st.executeUpdate("INSERT INTO $BINDING_TABLE_NAME (user_id, player_name, binding_time) VALUES ('10002', 'steve', '0')")
            }
        }

        // 迁移不得抛异常、也不得删除任何一行
        db.initialize()
        assertNull(indexExists(db), "存在重复数据时必须跳过建索引")
        assertEquals(2, db.getByUser("10001").size + db.getByUser("10002").size, "历史数据必须原样保留")

        // 人工清理后，下一次启动自动补建
        db.removeBindByUser("10002")
        db.initialize()
        assertNotNull(indexExists(db), "清理干净后必须自动补建唯一索引")
    }

    @Test
    fun `source group round trips and defaults to null`() {
        val db = newDatabase()
        db.initialize()

        // 群内路径：写入来源群
        assertTrue(db.addBindIfAbsent("10001", "Steve", "12345"), "群内绑定应写入成功")
        assertEquals(
            "12345",
            assertNotNull(db.getByName("Steve"), "刚写入的绑定必须可按名字查到").sourceGroup,
            "来源群必须落库（退群解绑据此判断该不该删这一条）"
        )
        assertEquals("12345", db.getByUser("10001").single().sourceGroup, "列表查询同样要带回来源群")

        // 游戏内指令 / 外部 API 路径：不带来源群
        db.addBind("10002", "Alex")
        assertNull(assertNotNull(db.getByName("Alex")).sourceGroup, "不带来源群的写入必须落成 NULL，而不是空串")
    }

    @Test
    fun `within limit overload records the source group`() {
        val db = newDatabase()
        db.initialize()

        assertTrue(db.addBindIfAbsentWithinLimit("10003", "Bob", 2, "777"), "名额充足时应写入成功")
        assertEquals("777", assertNotNull(db.getByName("Bob")).sourceGroup)
        assertFalse(db.addBindIfAbsentWithinLimit("10003", "Carol", 1, "777"), "名额用尽时必须拒绝")
        assertNull(db.getByName("Carol"), "被拒绝的写入不得留下记录")
    }

    @Test
    fun `a legacy table without source group column is migrated in place`() {
        val db = newDatabase()

        // 模拟升级前的表：没有 source_group 列，且已有历史数据（createTable 不会给既有表加列）
        db.getConnection().connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE $BINDING_TABLE_NAME (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT, player_name TEXT, " +
                        "player_uuid TEXT, binding_time TEXT)"
                )
                st.executeUpdate(
                    "INSERT INTO $BINDING_TABLE_NAME (user_id, player_name, binding_time) VALUES ('10001', 'steve', '0')"
                )
            }
        }

        db.initialize()

        assertTrue("source_group" in columnsOf(db), "initialize 必须给既有表补上 source_group 列")
        val legacy = assertNotNull(db.getByName("Steve"), "迁移不得丢失历史数据")
        assertNull(legacy.sourceGroup, "历史记录的来源群是未知的，必须读成 null 而不是抛异常")
        assertTrue(db.addBindIfAbsent("10002", "Alex", "12345"), "迁移后新写入应带回来源群")
        assertEquals("12345", assertNotNull(db.getByName("Alex")).sourceGroup)
    }

    private fun columnsOf(db: AbstractBindingDatabase<*, *>): List<String> =
        db.getConnection().connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info($BINDING_TABLE_NAME)").use { rs ->
                    buildList { while (rs.next()) add(rs.getString("name")) }
                }
            }
        }

    private fun indexExists(db: AbstractBindingDatabase<*, *>): String? =
        db.getConnection().connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_binding_player_name'"
                ).use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
}
