package me.regadpole.plumbot.utils

import me.regadpole.plumbot.PlumBot

fun debug(log: String) {
    PlumBot.INSTANCE.logger.atFine().log(log)
}

fun info(log: String) {
    PlumBot.INSTANCE.logger.atInfo().log(log)
}

fun warn(log: String) {
    PlumBot.INSTANCE.logger.atWarning().log(log)
}

fun error(log: String) {
    PlumBot.INSTANCE.logger.atSevere().log(log)
}