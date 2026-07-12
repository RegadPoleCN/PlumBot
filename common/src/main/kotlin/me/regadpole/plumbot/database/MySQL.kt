package me.regadpole.plumbot.database

import me.regadpole.config.DatabaseSource
import org.spongepowered.configurate.ConfigurationNode
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.HostSQL
import taboolib.module.database.SQL

class MySQL(node: ConfigurationNode) : AbstractBindingDatabase<HostSQL, SQL>(HostSQL(DatabaseSource(node))) {

    override val tableName = "binding"
    override fun SQL.configureIdColumn() = id()
    override fun SQL.configureUserIdColumn() = type(ColumnTypeSQL.BIGINT)
    override fun SQL.configurePlayerNameColumn() = type(ColumnTypeSQL.VARCHAR, 64)
    override fun SQL.configureTextColumn() = type(ColumnTypeSQL.TEXT)
}
