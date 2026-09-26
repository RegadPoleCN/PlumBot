package me.regadpole.plumbot.api

/**
 * 跨平台 API 单例持有者。
 * 供 PlumBot 核心在启动时注册实例，外部插件通过 [PlumBotAPI.get] 访问。
 */
@PublicApi
object PlumBotApiProvider {

    @Volatile
    private var instance: PlumBotAPI? = null

    @JvmStatic
    fun get(): PlumBotAPI =
        instance ?: error("PlumBotAPI 尚未初始化！请确保 PlumBot 已正确加载且处于已启用状态。")

    fun getInstanceOrNull(): PlumBotAPI? = instance

    fun register(api: PlumBotAPI) {
        instance = api
    }

    fun unregister() {
        instance = null
    }
}
