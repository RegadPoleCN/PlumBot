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
