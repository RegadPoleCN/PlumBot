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

package me.regadpole.plumbot.api.database

import me.regadpole.plumbot.api.PublicApi
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

@PublicApi
interface IDatabase {
    @Throws(ClassNotFoundException::class)
    fun initialize()

    @Throws(SQLException::class)
    fun close()

    @Throws(SQLException::class)
    fun getConnection(): DataSource

    fun getByUser(user: String): List<Binding>
    fun getByName(name: String): Binding?
    fun getById(id: Int): Binding?

    fun addBind(user: String, name: String)
    fun removeBindByNum(user: String, id: Int): String?
    fun removeBindByUser(user: String)
    fun removeBind(name: String)

    fun setUUID(name: String, uuid: UUID)

    fun getByUser(user: Long): List<Binding> = getByUser(user.toString())
    fun addBind(user: Long, name: String) = addBind(user.toString(), name)
    fun removeBindByNum(user: Long, id: Int): String? = removeBindByNum(user.toString(), id)
    fun removeBindByUser(user: Long) = removeBindByUser(user.toString())
    fun removeBind(user: Long) = removeBindByUser(user.toString())
}
