// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api

/**
 * 事件订阅返回的句柄。
 * 实现**必须**保证 [close] 幂等：重复调用不得抛异常，也不得产生重复副作用。
 */
@PublicApi
fun interface ListenerHandle {
    fun close()
}
