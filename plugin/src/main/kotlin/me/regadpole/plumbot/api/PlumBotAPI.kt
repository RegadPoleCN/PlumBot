package me.regadpole.plumbot.api

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.ConfigMaker
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.database.IDatabase

class PlumBotAPI(private val plugin: PlumBot) {
    companion object{
        private val INSTANCE by lazy { PlumBotAPI(PlumBot.INSTANCE) }
        fun getInstance(): PlumBotAPI {
            return INSTANCE
        }
    }

    /**
     * Get the database instance
     * @see IDatabase
     * @return the object of IDatabase
     */
    fun getDatabase(): IDatabase {
        return plugin.database!!
    }

    /**
     * Get the bot instance
     * @see IBot
     * @return the object of IBot
     */
    fun getBot(): IBot {
        return plugin.bot!!
    }

    /**
     * Get the messages
     * @see Messages
     * @return the object of Messages
     */
    fun getMessages(): Messages {
        return plugin.messages
    }

    /**
     * Get the config
     * @see ConfigMaker
     * @return the object of ConfigMaker
     */
    fun getConfig(): ConfigMaker {
        return plugin.config!!
    }
}