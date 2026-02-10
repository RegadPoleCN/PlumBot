package me.regadpole.plumbot.internal.dispatcher

import com.hypixel.hytale.server.core.command.system.CommandManager
import me.regadpole.plumbot.PlumBot
import java.util.concurrent.CompletableFuture

class CommandDispatcher {
    companion object {
        private val tasks: HashMap<Int, CompletableFuture<Void>> = hashMapOf()
        internal val result: HashMap<Int, String> = hashMapOf()
        private val availableNum: MutableList<Int> = mutableListOf()
        private var lastNum = 0

        fun clear() {
            tasks.forEach { (_, t) -> t.cancel(true) }
            tasks.clear()
            result.clear()
            availableNum.clear()
            lastNum = 0
        }
    }
    private val num: Int = if (availableNum.isNotEmpty()) {
            val i = availableNum[0]
            availableNum.remove(i)
            i
        } else {
        lastNum += 1
        lastNum
    }

    fun dispatch(command: String): String {
        val task = CommandManager.get().handleCommand(PlumBotCommandSender(num), command)
        tasks[num] = task
        task.join()
        tasks.remove(num)
        val r = result[num]
        result.remove(num)
        availableNum.add(num)
        return r ?: PlumBot.INSTANCE.messages.remoteCommandEmptyResult
    }
}