# 附属插件调用 PlumBot 公开 API

> **目标读者**：想在自己的 Bukkit 插件中**集成** PlumBot 机器人能力的开发者。

## 1. 如何取到 `PlumBotAPI`

PlumBot 通过 Bukkit `ServicesManager` 同时提供**静态访问点**和**服务发现**两种方式。

### 1.1 通过 ServicesManager（推荐）

```kotlin
import me.regadpole.plumbot.PlumBotAPI

val api = server.servicesManager
    .getRegistration(PlumBotAPI::class.java)
    ?.provider as? PlumBotAPI
```

第三方插件在 `onEnable` 中调用此代码时，PlumBot 已经 `attach`（依赖 `softdepend` / `depend` 顺序）。`provider` 即 `object PlumBotAPI` 单例。

### 1.2 通过静态访问点

```kotlin
import me.regadpole.plumbot.PlumBotAPI

val api = PlumBotAPI.getInstance() // 未 attach 时抛 IllegalStateException
val apiOrNull = runCatching { PlumBotAPI.getInstance() }.getOrNull()
```

### 1.3 宿主插件引用（如何取到 `PlumBotBukkit` 自身）

直接通过 `PlumBotAPI.getAttachedPlugin()`（返回 `me.regadpole.plumbot.PlumBot` 接口），它由 `PlumBotBukkit` 实现；如要拿到 `JavaPlugin` 引用用于 `server` / `logger` / `dataFolder` 等，进一步用 `JavaPlugin.getPlugin(PlumBotAPI::class.java)` 即可。

```kotlin
val host = org.bukkit.Bukkit.getPluginManager()
    .getPlugin("PlumBot") as? org.bukkit.plugin.java.JavaPlugin
host?.server  // 服务端实例
host?.logger  // 日志
```

> PlumBot 不再单独提供 `PluginProvider` 类——所有宿主信息都通过 `PlumBotAPI` 这一**单条 API 路径**拿到，避免 API 表面膨胀。

## 2. 如何发送群消息

```kotlin
import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.api.bot.IBot

// 方式 A：直接 API（推荐）
val sent: Boolean = PlumBotAPI.sendGroupMessage(
    groupId = 12345L,
    message = "Hello from MyCoolPlugin"
)

// 方式 B：拿到 IBot 自己调用（更细粒度可用 capability 判断）
val bot: IBot? = PlumBotAPI.getBotOrNull()
bot?.sendGroupMsg(12345L, "Plain text")
```

发送接口一览（均不抛异常打断主流程；无 bot 时返回 `false`）：

| 方法 | 说明 |
|---|---|
| `sendGroupMessage(groupId, message)` | 群文字消息 |
| `sendUserMessage(userId, message)` | 私聊文字消息 |
| `sendGroupMessageWithImage(groupId, message)` | 群图文消息 |
| `sendUserMessageWithImage(userId, message)` | 私聊图文消息 |

> ⚠️ `PlumBotAPI.getMessages()` 返回的是**可变**全局对象。**请勿**修改其字段；修改会破坏所有 adapter 的消息模板。

## 3. 如何监听群消息与用户退群

```kotlin
import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.event.UserDecreaseEvent

val gmHandle = PlumBotAPI.subscribeGroupMessages { evt: GroupMessageEvent ->
    // evt.botId / evt.groupId / evt.userId / evt.message / evt.timestamp
    if (evt.message == "!ping") {
        PlumBotAPI.sendGroupMessage(evt.groupId, "pong")
    }
}
val udHandle = PlumBotAPI.subscribeUserDecrease { evt: UserDecreaseEvent ->
    logger.info("${evt.userId} left ${evt.groupId}")
}

// 在插件 onDisable 中注销：
gmHandle.close()
udHandle.close()
// 多次 close() 幂等
```

监听器抛出的异常会被 PlumBot 捕获并写入 `PlatformLogger`，不会影响其他监听器；不要在监听器内部做阻塞调用。

## 4. 如何注册自定义 BotAdapter

无需修改 PlumBot 源码。在你自己的插件 `onEnable` 中：

```kotlin
import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.bot.BotFactory
import me.regadpole.plumbot.bot.BotAdapterMetadata
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bukkit.platform.BukkitPlugin
import org.bukkit.plugin.java.JavaPlugin

class MyCoolFactory : BotFactory {
    override val metadata = BotAdapterMetadata(
        type = "mycool",
        displayName = "MyCool",
        // 必填：声明你支持哪些平台
        supportedPlatforms = setOf(me.regadpole.plumbot.platform.PlatformType.BUKKIT),
        requiredPlugins = emptySet(),
        capabilities = setOf(
            BotCapability.GROUP_MESSAGE_SEND,
            BotCapability.USER_MESSAGE_SEND,
        )
    )

    override fun create(context: PlatformContext): IBot = TODO("your adapter")
}

class MyCoolPlugin : JavaPlugin() {
    override fun onEnable() {
        val ext = PlumBotAPI.getExtensionRegistry()
        ext.registerExternalFactory(MyCoolFactory(), BukkitPlugin(this))
        // 卸载时反注册
    }

    override fun onDisable() {
        // 反注册交给 BotExtensionRegistry：
        // - 若你的插件 disable 被 Bukkit 拦截，Listener 会自动调用 unregisterAllFor(this)。
        // - 你也可以主动调用：PlumBotAPI.getExtensionRegistry().unregisterAllFor(BukkitPlugin(this))
        //   或 unregisterExternalFactory("mycool", BukkitPlugin(this))。
    }
}
```

约束：

* 注册同 `type` 重复时返回 `false`（内置 adapter 永久占位）。
* 平台不兼容由 `BotProvider.loadBot` 阶段校验，注册阶段不抛异常。
* 不要调用 `BotEventDispatcher`（属内部 API），请用 `PlumBotAPI.subscribeXxx`。
* 不要直接 `new` 或反射访问 `common/internal/*`。

## 5. 合约注解（为下游框架准备的"信号灯"）

| 注解 | 含义 |
|---|---|
| `@PublicApi` | 公开 API；可使用，无稳定性承诺 |
| `@StableApi` | 公开 API；minor 版本内签名/行为兼容 |
| `@ExperimentalApi` | 公开 API；可能在 minor 版本变更 |

源代码里形如 `@StableApi interface IBot` 的标注，IDE 插件如 `detekt` / `ktlint` 的 API 检查规则可以选择性集成。

## 6. FAQ

* **Q：我能在还没启用的插件上下文里调 `getInstance()` 吗？**
  会抛 `IllegalStateException("PlumBotAPI 未初始化...")`。请用 `runCatching { ... }.getOrNull()` 包装。

* **Q：能拿到当前 bot 类型吗？**
  `PlumBotAPI.getBotOrNull()?.metadata?.type` 或 `PlumBotAPI.getBotProvider().availableAdapters()`。

* **Q：能在 platform 不是 Bukkit 时调用这套 API 吗？**
  `common` 不依赖 Bukkit；理论上可以。但 listener/ExtensionRegistry 入口需要平台提供 `Plugin` 适配类。当前项目已实现 `BukkitPlugin`；新增 Sponge/Velocity 时需补 `SpongePlugin` 等。

* **Q：`Messages` 是否会不可变？**
  暂不引入不可变视图，详见 `.trae/specs/third-pass-refactor/spec.md:152-157` 与公共 `Messages` KDoc。请勿在第三方代码中修改 `Messages` 字段。

* **Q：listener 异常会影响 `BotHandler` 吗？**
  不会。`BotEventDispatcher.dispatchGroupMessage` 会先调用 `BotHandler.onGroupMessage(...)`，再 fan-out 到公共 API listeners，二者异常被隔离。

## 7. 进阶模式

> 本节给出**实际生产**中遇到的多 Bot / 跨语言 / Capability 守卫等更细的样例与边界条件。

### 7.1 当前 bot 与多 Bot 场景

`PlumBotAPI` 同时**只持有 1 个** active bot（`BotProvider.getBot()`）。这是 design-by-choice：
* server 上用户通常运行 1 个 bot 对应 1 个群/协议；
* 若有"主备"需求，可在 `onEnable` 时让多个 `BotFactory` 注册进 `BotExtensionRegistry`，由管理员通过 `bot.type` 在 `config.yml` 中切换。

如果你需要"运行中切 bot"，调用 `PlumBotAPI.shutdown()` 再调你自己的重载逻辑（前提是你 fork 了 `PlumBotBukkit`）。否则就在 reload 时让运维承担。

#### 7.1.1 选某一个 bot 协议

```kotlin
val desiredType = config.getString("my.plugin.target-bot", "onebot")
val candidates = PlumBotAPI.getBotProvider()
    .availableAdapters()
    .filter { it.type.equals(desiredType, ignoreCase = true) }

if (candidates.isEmpty()) {
    logger.warning("[YourPlugin] 目标 bot type=$desiredType 不可用；当前可用: " +
        PlumBotAPI.getBotProvider().availableAdapters().joinToString { it.type })
}
```

### 7.2 Java 插件视角

Java 插件调用 Kotlin `object` 的方法时，等价于 Java 静态方法：

```java
import me.regadpole.plumbot.PlumBotAPI;
import me.regadpole.plumbot.api.event.GroupMessageEvent;
import me.regadpole.plumbot.api.ListenerHandle;

// 取实例（Bukkit 1.13+）
PlumBotAPI api = server.getServicesManager()
        .getRegistration(PlumBotAPI.class)
        .getProvider();

// 发送
boolean ok = api.sendGroupMessage(12345L, "hi");

// 订阅（Java 8 lambda）
ListenerHandle handle = api.subscribeGroupMessages((GroupMessageEvent e) -> {
    if ("!ping".equals(e.getMessage())) {
        api.sendGroupMessage(e.getGroupId(), "pong");
    }
});
// 卸载
handle.close();
```

> 注意：`Plugin<...>` / `ListenerHandle` / `GroupMessageEvent` 等 Kotlin 类型在 Java 端都用 getter（`getMessage()` / `isEnabled()` 等）；Java 8+ lambda 可直接用。

### 7.3 Capability 守卫

第三方代码应根据 adapter 的真实能力判断如何发送，避免在不支持 image 的 adapter 上调用 `sendGroupMessageWithImage`：

```kotlin
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.bot.requireCapability

fun safeSendImage(groupId: Long, msg: String) {
    val bot = PlumBotAPI.getBotOrNull() ?: return
    // 软判断：
    val supportsImage = BotCapability.IMAGE_SEND in bot.metadata.capabilities
    if (supportsImage) {
        PlumBotAPI.sendGroupMessageWithImage(groupId, msg)
    } else {
        // 退化到纯文本
        PlumBotAPI.sendGroupMessage(groupId, msg)
    }

    // 硬断言（缺失能力即抛 IllegalStateException）：
    // bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
}
```

`requireCapability` 是定义在 `IBot` 上的顶层扩展函数（见 `common/src/main/kotlin/me/regadpole/plumbot/bot/BotCapabilities.kt`），用于"必须存在"的硬断言；软判断请用 `IBot.metadata.capabilities.contains(BotCapability.X)`。

### 7.4 路由订阅（与主 BotHandler 并存）

如果你订阅了 `subscribeGroupMessages`，**不要**依赖它做命令解析；命令解析属于 `BotHandler` 责任范围。**可接受**的分工：
* 你做：跨群广播、统计、外部 API 触发、第三方服务桥接。
* 主 handler 做：白名单、Bot 命令、消息模板拼装。

### 7.5 配置注入与"运行时重读"

PlumBot 的 `YamlConfigurator` 内部使用 Configurate；config 节点可读但你不应**写**。若你的下游插件需要写，建议：

1. 把你的配置写到自己的 `plugins/YourPlugin/config.yml`；
2. 通过 `PluginProvider` 取到 `JavaPlugin.getDataFolder()` 自己实现；
3. 不要 extend `YamlConfigurator` 或尝试反射改其内部状态。

### 7.6 重连 / 热重载注意事项

如果你做的是"监听器"，Bukkit 在 `/reload` 时会重新跑 `onEnable()` + `onDisable()`；`BotEventDispatcher.clearAllListeners()` 会自动跑，你之前拿到的 `ListenerHandle.close()` 仍可安全调用（幂等）。但请：

* 把 `handle.close()` 放在 `onDisable` 中，而**不是**在 lambda 内部。
* 不要假设 listener 顺序；多个订阅者**并行**触发，无序保证。
* 不要在 listener 内做 `Thread.sleep` 或类似阻塞调用——listener 会跑在 adapter 的网络线程上。

### 7.7 性能建议

* `sendXxxMessage` 每次返回 `Boolean`：false 不一定是错，可能仅是没 bot；如果你的批处理很多，考虑批量改用 `IBot` 上的对应方法拿到精确返回值。
* `subscribeGroupMessages` 每条消息一个 listener 调用。如要批量（如数据库写、HTTP 上报），建议你自己攒批处理 + 限流，避免数据库/网络被消息洪流打爆。

### 7.8 IDE / 静态检查

* 在 IntelliJ 中，搜索符号时优先看 `me.regadpole.plumbot.api.*`、`me.regadpole.plumbot.bot.BotProvider` 等公开包，避免被引导到 `internal`。
* 在 IDE 中安装 [Kotlin @RequiresOptIn](https://kotlinlang.org/docs/opt-in-requirements.html) lint 规则，可视化 `@ExperimentalApi` 警告。
* 一旦哪个第三方 API 没标 `@StableApi`，视为"随时可改"。

## 8. 相关延伸阅读

- [extending-bot-adapter.md](./extending-bot-adapter.md) — 完整 Adapter 实现
- [migrating-plumbotapi-v2.md](./migrating-plumbotapi-v2.md) — `PlumBotAPI` v2 迁移
- [testing-external-plugins.md](./testing-external-plugins.md) — 单测与集成测
- [public-api-changelog.md](./public-api-changelog.md) — API 变更日志
- [bot-capabilities.md](../architecture/bot-capabilities.md) — Capability 模型
- [platform-adapter-architecture.md](../architecture/platform-adapter-architecture.md) — 整体架构
