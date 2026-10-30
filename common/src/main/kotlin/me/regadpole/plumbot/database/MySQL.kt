// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

import me.regadpole.config.DatabaseSource
import org.spongepowered.configurate.ConfigurationNode
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.HostSQL
import taboolib.module.database.SQL

class MySQL(node: ConfigurationNode) :
    AbstractBindingDatabase<HostSQL, SQL>(HostSQL(DatabaseSource(node)), BINDING_TABLE_NAME) {

    override fun SQL.configureIdColumn() = id()
    override fun SQL.configureUserIdColumn() = type(ColumnTypeSQL.VARCHAR, 64)
    override fun SQL.configurePlayerNameColumn() = type(ColumnTypeSQL.VARCHAR, 64)
    override fun SQL.configureTextColumn() = type(ColumnTypeSQL.TEXT)

    /** MySQL 走 `information_schema.columns`（与建索引的探测方式保持一致）。 */
    override fun sourceGroupColumnPresent(connection: java.sql.Connection): Boolean =
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = '$BINDING_TABLE_NAME' " +
                    "AND column_name = 'source_group'"
            ).use { rs -> rs.next() && rs.getInt(1) > 0 }
        }

    /**
     * MySQL 8 的 `CREATE INDEX` 不支持 `IF NOT EXISTS`，因此先查 `information_schema`
     * 再建索引；整个方法可重复执行。
     */
    override fun createUniquePlayerNameIndex() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                val alreadyExists = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = '$BINDING_TABLE_NAME' " +
                        "AND index_name = 'idx_binding_player_name'"
                ).use { rs -> rs.next() && rs.getInt(1) > 0 }
                if (!alreadyExists) {
                    st.executeUpdate(
                        "CREATE UNIQUE INDEX idx_binding_player_name ON $BINDING_TABLE_NAME (player_name)"
                    )
                }
            }
        }
    }
}
