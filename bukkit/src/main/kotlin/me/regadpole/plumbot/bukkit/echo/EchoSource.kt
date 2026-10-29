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

package me.regadpole.plumbot.bukkit.echo

/** 控制台日志旁路源（log4j2 / JUL / latest.log）。 */
internal interface EchoSource {
    val name: String

    /** 在主线程 attach；返回 false 表示该源不可用。 */
    fun start(): Boolean

    /** 在 finalize 线程 detach，并把窗口内新增内容送入 session。 */
    fun finish(session: EchoSession)
}
