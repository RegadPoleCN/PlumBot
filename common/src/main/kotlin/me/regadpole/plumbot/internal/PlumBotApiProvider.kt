package me.regadpole.plumbot.internal

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.PlumBotAPI

/**
 * 内部 API 生命周期管理器。
 * 对外部第三方插件隐藏生命周期控制（attach/detach），仅暴露 requireInstance() 供 [PlumBotAPI.get] 访问。
 */
object PlumBotApiProvider {

    @Volatile
    private var instance: PlumBotAPI? = null

    fun requireInstance(): PlumBotAPI =
        instance ?: error("PlumBotAPI 尚未初始化！请确保 PlumBot 已正确加载且处于已启用状态。")

    fun getInstanceOrNull(): PlumBotAPI? = instance

    fun attach(plugin: PlumBot) {
        instance = PlumBotApiImpl(plugin)
    }

    fun detach() {
        instance = null
    }
}
