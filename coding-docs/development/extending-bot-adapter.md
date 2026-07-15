# 自定义 BotAdapter（外部插件实现完整教程）

> **目标读者**：想写一个**完整可工作**的 Bot Adapter（自定义协议或自定义网关），但不想 fork / 重编 PlumBot 的第三方开发者。
>
> 本文是 [extending-from-other-plugins.md §4](./extending-from-other-plugins.md) 的**深度版**：那一节只给 `BotFactory` + `BotAdapterMetadata` 的最小骨架；本文给出完整的协议实现、事件广播、缓存预热、热重载兼容、跨平台声明等**端到端**模板。

## 0. 范围与限制

* 你的 adapter 只能运行在 **已经 attach 的 `PlumBot` 服务端**上（Bukkit 默认已支持；新增 Sponge/Velocity 时需先补 `Plugin` / `PlatformContext` 适配）。
* PlumBot **不要求** 你 fork `common` 模块；只要 Gradle 把 `PlumBot-Bukkit.jar` 以 `provided` / `compileOnly` 提供给第三方插件即可。
* 不要直接 `new` 或反射访问 `common/internal/*`；这些类型 KDoc 明确标记"框架内部使用"。
* 注册阶段**绝不抛异常**。`BotExtensionRegistry.registerExternalFactory` 通过 `Boolean` 返回失败；正文会给出失败原因的处理模式。

## 1. 端到端最小骨架

下面给出一个**生产可用**的骨架："我的协议"适配器，能发群消息、能列出群成员、能在收到群消息时向上分发事件。生产中替换 `MyProtocolClient` 的实现即可（HTTP、WebSocket、gRPC 均可）。

```kotlin
package com.example.mycool.adapter

import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bot.*
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.platform.PlatformType
import java.util.concurrent.CompletableFuture

/* ---------- Factory ---------- */

@PublicApi
object MyCoolFactory : BotFactory {
    override val metadata: BotAdapterMetadata = BotAdapterMetadata(
        type = "mycool",
        displayName = "MyCool",
        supportedPlatforms = setOf(PlatformType.BUKKIT),     // 明确支持范围
        requiredPlugins = emptySet(),                        // 运行时无外部依赖
        capabilities = setOf(
            BotCapability.GROUP_MESSAGE_SEND,
            BotCapability.USER_MESSAGE_SEND,
            BotCapability.GROUP_MEMBER_QUERY,
            BotCapability.GROUP_MEMBER_CHECK,
            BotCapability.GROUP_MESSAGE_RECEIVE,
            BotCapability.GROUP_MEMBER_DECREASE_RECEIVE,
            // 暂不支持：BotCapability.IMAGE_SEND、GROUP_MEMBER_QUERY_RELATIONS...
        )
    )

    override fun create(context: PlatformContext): IBot =
        MyCoolAdapter(context, metadata)
}

/* ---------- Adapter ---------- */

@StableApi
class MyCoolAdapter(
    override val context: PlatformContext,
    override val metadata: BotAdapterMetadata,
) : AbstractBotAdapter() {

    private val client: MyProtocolClient = MyProtocolClient(
        endpoint = context.config.getString("bot", "mycool", "endpoint") ?: "ws://localhost:9100",
        token = context.config.getString("bot", "mycool", "token") ?: "",
    )

    /* 必填：连接 / 关闭。throw 会导致 PlumBot 在 loadBot 阶段爆 IllegalStateException。 */

    override fun doStart() {
        client.connect { event ->
            // 收到外部协议事件 -> 转成 common 分发入口
            when (event) {
                is MyCoolGroupMsg -> BotEventDispatcher.dispatchGroupMessage(
                    messageRaw = event.text,
                    groupId = event.groupId,
                    senderId = event.senderId,
                )
                is MyCoolMemberLeave -> BotEventDispatcher.dispatchUserDecrease(
                    groupId = event.groupId,
                    userId = event.userId,
                )
            }
        }
    }

    override fun doShutdown() {
        client.close()
    }

    /* 缓存预热：AbstractBotAdapter 提供的 primeGroupMember helper 可直接复用。
       群名缓存 (groupNameCache) 内部触发：调用 loadGroupName(groupId) 的 CompletableFuture
       完成即可写入缓存；如需"预热"，让 sync getGroupName 走一次即可。 */

    override fun preloadGroupCaches() {
        val groups = context.config.getLongList("groups")
        for (groupId in groups) {
            runCatching {
                val members = client.listMembers(groupId)
                members.forEach { primeGroupMember(groupId, it.toMemberInfo()) }
                // 群名预热：触发同步 getGroupName 一遍，写入缓存
                getGroupName(groupId)
            }.onFailure { e ->
                context.logger.log(me.regadpole.plumbot.internal.LogLevel.WARN,
                    "preloadGroupCaches failed for group=$groupId: ${e.message}")
            }
        }
    }

    /* 回源 loader：被 groupMemberCache / groupNameCache 在缓存未命中时回调。 */

    override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
        return client.fetchMemberAsync(groupId, userId)
            .thenApply { it?.toMemberInfo() }
    }

    override fun loadGroupName(groupId: Long): CompletableFuture<String> {
        return client.fetchGroupNameAsync(groupId)
    }

    /* 必填：发送消息能力。 */

    override fun sendGroupMsg(targetId: Long, message: String) {
        client.sendGroupMessage(targetId, message)
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        client.sendUserMessage(targetId, message)
    }

    override fun sendGroupPicWithText(targetId: Long, message: String) {
        client.sendGroupImageWithText(targetId, message) // 自定义实现，参考 me.regadpole.plumbot.utils.TextToImg
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        client.sendUserImageWithText(targetId, message)
    }
}

/* ---------- Wire ↔ Domain 映射 ---------- */

private fun MyCoolMemberInfo.toMemberInfo(): MemberInfo = MemberInfo(
    userId = userId,
    name = nickname,
    card = groupCard,
    role = role.name.lowercase(),
)
```

## 2. 在第三方插件中注册

```kotlin
class MyCoolPlugin : JavaPlugin() {
    override fun onEnable() {
        val ext = runCatching { PlumBotAPI.getExtensionRegistry() }.getOrNull()
        if (ext == null) {
            logger.warning("PlumBot not present; MyCoolAdapter unavailable")
            return
        }
        val ok = ext.registerExternalFactory(MyCoolFactory, BukkitPlugin(this))
        if (!ok) {
            logger.warning("MyCoolAdapter registration refused (maybe duplicate or built-in taken)")
        }
    }

    override fun onDisable() {
        // 由 PluginDisableEvent 自动清理；下方为显式清理示范：
        runCatching {
            PlumBotAPI.getExtensionRegistry()
                .unregisterAllFor(BukkitPlugin(this))
        }
    }
}
```

## 3. 关键约束清单

| 项 | 要求 |
|---|---|
| `metadata.supportedPlatforms` | 必须为非空集；空集意味着"通吃"——慎用 |
| `metadata.requiredPlugins` | 写你运行时 `Bukkit.getPluginManager().isPluginEnabled(...)` 真正检查的名字 |
| `metadata.capabilities` | 仅声明你**已实现的**能力（详见 [bot-capabilities.md](../architecture/bot-capabilities.md)） |
| 异常策略 | adapter 抛异常 → `BotProvider.loadBot` 抛 `IllegalStateException`；`BotExtensionRegistry` 任何注册失败仅写 WARN 并返回 `false` |
| 异步 | 所有阻塞型 `IBot.getXxx` 同步查询都基于 `CompletableFuture` + `awaitWithTimeout`；详见 [adding-bot-adapter.md](./adding-bot-adapter.md) |
| 线程 | listener 回调可能来自**外部网络线程**；用 `submitAsync` 把耗时的下游操作切走 |

## 4. `BotAdapterMetadata` 字段语义详解

| 字段 | 用途 | 第三方实现建议 |
|---|---|---|
| `type` | 唯一 id（小写、不带空格），作为 `config.yml` 的 `bot.type` 取值 | 命名空间化，如 `mynamespace.myadapter`，避免和 `onebot` / `mirai` 冲突 |
| `displayName` | UI / 日志显示 | 默认与 `type` 相同时可省略 |
| `supportedPlatforms` | 决定 `BotProvider.availableAdapters()` 在哪个 platform 中返回此项 | 适配 Bukkit 就写 `setOf(PlatformType.BUKKIT)` |
| `requiredPlugins` | 由 `BotProvider.loadBot` 校验 `PlatformContext.isPluginAvailable(name)` | 只写**实际**会查的名字 |
| `capabilities` | 供下游判断"我能不能用 image send" | 严格按 [bot-capabilities.md](../architecture/bot-capabilities.md) 的真实支持声明 |

## 5. 缓存与超时

`AbstractBotAdapter` 内部已封装：

* `groupMemberCache: DefaultGroupMemberCache` 由 `fetchMember` 回源。
* `groupNameCache: DefaultBotCache<Long, String>` 由 `loadGroupName` 回源。
* 同步访问方（`getGroupName` / `checkUserInGroup` / `getGroupUserName` / `getGroupUserCard`）通过 `awaitWithTimeout` 等候；默认 10 秒（参见 `AbstractBotAdapter.kt` 的 `DEFAULT_ADAPTER_TIMEOUT_MS`）。

如果你需要**更短/更长**的超时，可在子类 override：

```kotlin
override fun <T> awaitWithTimeout(future: CompletableFuture<T>, default: T): T =
    runCatching { future.get(5_000L, TimeUnit.MILLISECONDS) }
        .getOrDefault(default)
```

## 6. 热重载 (`/plumbot reload`) 与 detach 流程

* 当服务器管理员执行 `/plumbot reload` 或 PlumBot 自己 `disable()` 时：
  * `PlumBotAPI.detach()` 把当前 attach 状态清空。
  * `BotProvider.unloadBot()` 调用 `bot.shutdown()`，回到 `AbstractBotAdapter.doShutdown`。
  * `BotEventDispatcher.clearAllListeners()` 清空所有公共 listener，**你返回的 `ListenerHandle.close()` 也会被视作已失效**（无需你自己重复 close）。
* 如果你的 adapter 在 unload 时需要做**异步**清理（关闭 WS 连接、断开池等），请把逻辑放在 `doShutdown()` 末尾，并对未完成的 `CompletableFuture` 设置超时；否则 `AbstractBotAdapter.shutdown` 的 `getGroupMemberCache.invalidateAll()` 会先于你的清理完成。

## 7. 跨平台声明与未来兼容

* 写 `supportedPlatforms = setOf(PlatformType.BUKKIT)`：当前唯一支持平台。
* 当 PlumBot 新增 Sponge/Velocity 时，**不会**自动允许你的 adapter 跨平台——你需要：
  1. 自己新增对目标 platform 的 wire 适配（接该平台的 scheduler 启动 `client.connect`）。
  2. 在你的 metadata 中追加 `PlatformType.SPONGE` 等。
  3. 注册新的 `BotFactory` 实例（`BotExtensionRegistry.registerExternalFactory` 允许**多次注册**，只要你换 `type`；同 type 仍以**先到为准**）。

## 8. 调试与日志

* **日志**统一走 `context.logger.log(LogLevel, ...)`，不要直接 `Bukkit.getLogger()`，否则会被 debug 体系绕过。
* **错误隔离**：listener 内部的异常会被 `BotEventDispatcher` 吞掉并 warn；调试时把 LogLevel 调到 DEBUG（参见 [debugging.md](./debugging.md)）即可看到完整堆栈。
* **失败回退**：`BotEventDispatcher.dispatchGroupMessage` 先调用 `BotHandler.onGroupMessage` 再 fan-out；如果你也要命令解析，注册在你自己的 listener 里即可。

## 9. 验证清单

- [ ] `BotAdapterMetadata.capabilities` 与实际实现一一对应（不能多宣称）。
- [ ] `doStart()` 抛异常时日志带上下文（endpoint、token 是否 mask 过）。
- [ ] `preloadGroupCaches` 在 `feature.load.enable=false` 时**仍然**安全——不要假设它在配置禁用时跳过。
- [ ] `Metadata.type` 已填入 `config.yml > bot.type`，且小写。
- [ ] `requiredPlugins` 在 `PlatformContext.isPluginAvailable` 返回 true 后才真正使用这些插件。
- [ ] adapter 注册失败时**不抛**（依赖 `Boolean` 返回）。
- [ ] 卸载时 `doShutdown` 内的 `CompletableFuture` 全部结束或被取消。

## 10. 相关文档

- [extending-from-other-plugins.md](./extending-from-other-plugins.md) — 公共 API 入门
- [adding-bot-adapter.md](./adding-bot-adapter.md) — 维护者视角的"内置适配器"流程（含 §13 第三方注册）
- [bot-capabilities.md](../architecture/bot-capabilities.md) — Capability 模型与判别
- [adapter-platform-compatibility.md](../architecture/adapter-platform-compatibility.md) — 兼容矩阵维护
- [platform-adapter-architecture.md](../architecture/platform-adapter-architecture.md) — 架构总览
