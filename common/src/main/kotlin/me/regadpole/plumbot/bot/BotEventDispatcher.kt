package me.regadpole.plumbot.bot

/**
 * 统一事件分发入口，从不同 Bot 协议（OneBot / MiraiMC 等）的 Listener 转发到
 * [BotProvider] 当前活跃 Bot 的 [me.regadpole.plumbot.listener.BotHandler]。
 *
 * 注意：理想状态下该对象仅供 `common` 内部使用（应声明为 `internal object`）。
 * 然而当前 `bukkit` 模块下的 `MiraiMCListener` 直接引用本对象以解耦 listener
 * 与 Bot 实例生命周期，跨模块 `internal` 可见性限制会破坏现有调用方。
 *
 * 因此暂保持 `public` 可见性，并在此 KDoc 中显式标注"仅供框架内部使用"，
 * 以避免外部模块继续将新的 Listener 直接依赖在此处。后续若将所有 Listener
 * 迁移到 `common` 模块（或改用 `@PublishedApi internal`），再收紧到 `internal`。
 */
object BotEventDispatcher {
    fun dispatchGroupMessage(message: String, groupId: Long, senderId: Long) {
        BotProvider.getBot()?.handler?.onGroupMessage(message, groupId, senderId)
    }

    fun dispatchUserDecrease(groupId: Long, userId: Long) {
        BotProvider.getBot()?.handler?.onUserDecrease(groupId, userId)
    }
}
