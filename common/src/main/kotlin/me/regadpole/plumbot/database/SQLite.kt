// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

import taboolib.module.database.ColumnTypeSQLite
import taboolib.module.database.HostSQLite
import taboolib.module.database.SQLite
import java.io.File

class SQLite(path: String) :
    AbstractBindingDatabase<HostSQLite, SQLite>(HostSQLite(File(path)), BINDING_TABLE_NAME) {

    override fun SQLite.configureIdColumn() = id()
    override fun SQLite.configureUserIdColumn() = type(ColumnTypeSQLite.TEXT)
    override fun SQLite.configurePlayerNameColumn() = type(ColumnTypeSQLite.TEXT)
    override fun SQLite.configureTextColumn() = type(ColumnTypeSQLite.TEXT)

    /** SQLite 没有 information_schema，用 `PRAGMA table_info` 读列名。 */
    override fun sourceGroupColumnPresent(connection: java.sql.Connection): Boolean =
        connection.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($BINDING_TABLE_NAME)").use { rs ->
                var present = false
                while (rs.next()) {
                    if (rs.getString("name") == "source_group") present = true
                }
                present
            }
        }

    /** SQLite 支持 `IF NOT EXISTS`，天然幂等。 */
    override fun createUniquePlayerNameIndex() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_binding_player_name ON $BINDING_TABLE_NAME (player_name)"
                )
            }
        }
    }
}
