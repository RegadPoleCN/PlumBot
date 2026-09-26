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
