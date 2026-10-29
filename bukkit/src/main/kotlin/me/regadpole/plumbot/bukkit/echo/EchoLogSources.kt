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

/**
 * 按配置创建并启动控制台日志源。
 *
 * log4j2 / JUL / latest.log 是三条互补旁路：Paper 的 FeedbackForwardingSender 只能拿组件反馈，
 * 插件或其他代码直接打日志时只有日志旁路能看见。任意一条失败都不应阻断执行，所以只记录 debug。
 */
internal object EchoLogSources {

    fun create(
        session: EchoSession,
        settings: EchoSettings,
        debug: (String) -> Unit
    ): List<EchoSource> {
        val sources = ArrayList<EchoSource>(3)
        if (settings.log4j2) {
            val source = Log4j2EchoSource.tryCreate(session, settings.includeLogPrefix, debug)
            if (source != null && source.start()) sources += source else debug("log4j2 回显源未启用")
        }
        if (settings.jul) {
            val source = JulEchoSource(session)
            if (source.start()) sources += source else debug("JUL 回显源未启用")
        }
        if (settings.latestLog) {
            val source = LatestLogTail()
            if (source.start()) sources += source else debug("latest.log 回显源未启用（文件不存在或不可读）")
        }
        return sources
    }
}