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

    var joinProxy: String = "<missing:joinProxy>"
    var leaveProxy: String = "<missing:leaveProxy>"
    var changeServer: String = "<missing:changeServer>"
    var death: String = "<missing:death>"
    var advancement: String = "<missing:advancement>"

    var kickServer: String = "<missing:kickServer>"
    var kickPlatform: String = "<missing:kickPlatform>"

    var playerAddBind: String = "<missing:playerAddBind>"
    var playerDeleteBind: String = "<missing:playerDeleteBind>"
    var playerQueryBind: String = "<missing:playerQueryBind>"

    var fullBind: String = "<missing:fullBind>"
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

    var noCommandFound: String = "<missing:noCommandFound>"
    var commandAddBind: String = "<missing:commandAddBind>"
    var commandDeleteBindById: String = "<missing:commandDeleteBindById>"
    var commandDeleteBindByQQ: String = "<missing:commandDeleteBindByQQ>"
    var commandQueryBindById: String = "<missing:commandQueryBindById>"
    var commandQueryBindByQQ: String = "<missing:commandQueryBindByQQ>"
}
