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
