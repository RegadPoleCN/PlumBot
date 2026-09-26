package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.StableApi
import net.kyori.adventure.text.Component
import java.nio.file.Path

@StableApi
interface PlatformContext {
    val config: PlatformConfig
    val datasource: PlatformConfig
    val dataDirectory: Path
    val scheduler: PlatformScheduler
    val playerService: PlatformPlayerService
    val platformType: PlatformType

    /** 当前运行平台所声明支持的全部特性能力集合 */
    val supportedCapabilities: Set<PlatformCapability>
        get() = emptySet()

    /**
     * 日志输出能力（内聚于平台上下文）
     */
    fun log(level: LogLevel, message: String)

    /**
     * 向全服控制台与在线玩家广播 Adventure 富文本消息
     */
    fun sendMessage(message: Component)

    fun isPluginAvailable(name: String): Boolean

    /** 校验当前平台是否具备某个能力 */
    fun hasCapability(capability: PlatformCapability): Boolean =
        capability in supportedCapabilities
}
