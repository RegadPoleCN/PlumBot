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

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.PublicApi

@PublicApi
enum class PlatformType {
    BUKKIT,
    VELOCITY,
    BUNGEE,
    STANDALONE,
    HYTALE
}

@PublicApi
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

@PublicApi
enum class PlatformCapability {
    CHAT_RECEIVE,
    CHAT_BROADCAST,
    PRE_LOGIN_INTERCEPT,
    PLAYER_JOIN_BROADCAST,
    PLAYER_QUIT_BROADCAST,
    SERVER_SWITCH_BROADCAST,
    PLAYER_DEATH_BROADCAST,
    PLAYER_ADVANCEMENT_BROADCAST,
    SERVER_TPS_METRICS,
    COMMAND_DISPATCH
}
