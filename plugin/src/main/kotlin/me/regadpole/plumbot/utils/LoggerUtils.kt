package me.regadpole.plumbot.utils

import me.regadpole.plumbot.PlumBot

fun debug(log: String) {
    PlumBot.INSTANCE.logger.debug(log)
}

fun info(log: String) {
    PlumBot.INSTANCE.logger.info(log)
}

fun warn(log: String) {
    PlumBot.INSTANCE.logger.warn(log)
}

fun error(log: String) {
    PlumBot.INSTANCE.logger.error(log)
}