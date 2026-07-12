package me.regadpole.plumbot.database

import taboolib.module.database.ColumnTypeSQLite
import taboolib.module.database.HostSQLite
import taboolib.module.database.SQLite
import java.io.File

class SQLite(path: String) : AbstractBindingDatabase<HostSQLite, SQLite>(HostSQLite(File(path))) {

    override val tableName = "binding"
    override fun SQLite.configureIdColumn() = id()
    override fun SQLite.configureUserIdColumn() = type(ColumnTypeSQLite.INTEGER)
    override fun SQLite.configurePlayerNameColumn() = type(ColumnTypeSQLite.TEXT)
    override fun SQLite.configureTextColumn() = type(ColumnTypeSQLite.TEXT)
}
