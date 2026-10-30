// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.config

import me.regadpole.plumbot.api.PublicApi

@PublicApi
object Messages {
    var prefix: String = "<missing:prefix>"

    var load: String = "<missing:load>"
    var unload: String = "<missing:unload>"

    var ob2server: String = "<missing:ob2server>"
    var server2ob: String = "<missing:server2ob>"

    var playerList: String = "<missing:playerList>"

    var avatarCard: String = "<missing:avatarCard>"
    var avatarUsage: String = "<missing:avatarUsage>"

    /** 代绑时 `require_in_group` 校验失败的拒绝文案（占位符 `%user_id%`）。 */
    var notInGroup: String = "<missing:notInGroup>"

    var joinProxy: String = "<missing:joinProxy>"
    var leaveProxy: String = "<missing:leaveProxy>"
    var changeServer: String = "<missing:changeServer>"
    var death: String = "<missing:death>"
    var advancement: String = "<missing:advancement>"

    var kickServer: String = "<missing:kickServer>"
    var kickPlatform: String = "<missing:kickPlatform>"

    /** 白名单校验暂时不可用（查询超时 / 数据库未就绪）时报给被拒玩家。 */
    var kickUnavailable: String = "<missing:kickUnavailable>"

    var playerAddBind: String = "<missing:playerAddBind>"
    var playerDeleteBind: String = "<missing:playerDeleteBind>"
    var playerQueryBind: String = "<missing:playerQueryBind>"

    var fullBind: String = "<missing:fullBind>"
    var bindTimeLine: String = "<missing:bindTimeLine>"
    var bindLimitLine: String = "<missing:bindLimitLine>"
    var bindPlayersLine: String = "<missing:bindPlayersLine>"
    var consoleMetaLine: String = "<missing:consoleMetaLine>"
    var qqEmptyBind: String = "<missing:qqEmptyBind>"
    var idEmptyBind: String = "<missing:idEmptyBind>"
    var existsBind: String = "<missing:existsBind>"
    var notExistsBind: String = "<missing:notExistsBind>"
    var notBelongToYou: String = "<missing:notBelongToYou>"
    var wrongUsage: String = "<missing:wrongUsage>"
    var internalError: String = "<missing:internalError>"

    var adminAddBind: String = "<missing:adminAddBind>"
    var adminDeleteBind: String = "<missing:adminDeleteBind>"
    var adminQueryIdBind: String = "<missing:adminQueryIdBind>"
    var adminQueryQQBind: String = "<missing:adminQueryQQBind>"

    var help: List<String> = emptyList()

    var serverStatus: String = "<missing:serverStatus>"
    var statusUnavailable: String = "<missing:statusUnavailable>"
    var remoteCommandDenied: String = "<missing:remoteCommandDenied>"
    var remoteCommandDisabled: String = "<missing:remoteCommandDisabled>"
    var remoteCommandExecuted: String = "<missing:remoteCommandExecuted>"
    var remoteCommandNoOutput: String = "<missing:remoteCommandNoOutput>"
    var remoteCommandUnknown: String = "<missing:remoteCommandUnknown>"
    var remoteCommandFailed: String = "<missing:remoteCommandFailed>"
}
