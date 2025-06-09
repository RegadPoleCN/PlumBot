package me.regadpole.plumbot

import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider

class PlumBotAPI(private val plugin: PlumBot) {

    /**
     * Get the database instance
     * @see me.regadpole.plumbot.database.DatabaseProvider
     * @return the object of IDatabase
     */
    fun getDatabase(): DatabaseProvider {
        return DatabaseProvider
    }

    /**
     * Get the bot instance
     * @see me.regadpole.plumbot.bot.BotProvider
     * @return the object of IBot
     */
    fun getBotProvider(): BotProvider {
        return BotProvider
    }

    /**
     * Get the messages
     * @see me.regadpole.plumbot.api.config.Messages
     * @return the object of Messages
     */
    fun getMessages(): Messages {
        return Messages
    }

    /**
     * Get the config
     * @see me.regadpole.plumbot.api.config.YamlConfigurator
     * @return the object of ConfigMaker
     */
    fun getConfig(): YamlConfigurator {
        return plugin.config
    }
}