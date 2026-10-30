// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

/** 控制台日志旁路源（log4j2 / JUL / latest.log）。 */
internal interface EchoSource {
    val name: String

    /** 在主线程 attach；返回 false 表示该源不可用。 */
    fun start(): Boolean

    /** 在 finalize 线程 detach，并把窗口内新增内容送入 session。 */
    fun finish(session: EchoSession)
}
