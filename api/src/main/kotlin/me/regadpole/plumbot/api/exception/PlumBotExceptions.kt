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

package me.regadpole.plumbot.api.exception

import me.regadpole.plumbot.api.PublicApi

@PublicApi
sealed class PlumBotApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@PublicApi
class BotNotReadyException(message: String = "No bot instance is currently available") : PlumBotApiException(message)

@PublicApi
class BotMessageSendException(message: String, cause: Throwable? = null) : PlumBotApiException(message, cause)

@PublicApi
class AdapterRegistrationException(message: String) : PlumBotApiException(message)
