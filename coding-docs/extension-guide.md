# PlumBot 附属插件与外部扩展开发手册

本文面向三种需求：在自己的插件里调用 PlumBot 的群互通能力、订阅 Bot 事件、实现第三方 Bot 适配器。

文中示例都有**可编译版本**：[`common/src/test/kotlin/me/regadpole/plumbot/docs/ExtensionGuideSnippets.kt`](../common/src/test/kotlin/me/regadpole/plumbot/docs/ExtensionGuideSnippets.kt)。该文件随 `./gradlew :common:compileTestKotlin` 编译，API 改名或改签名会先让它编译失败。以它为准，本文只解释用法。

## 一、依赖接入

本仓库**不发布到 Maven 中央**，没有 `maven-publish` 配置，不存在 `me.regadpole.plumbot:api:<version>` 这类坐标。可用方式只有两种。

### 1. 编译期：把发布产物当文件依赖

`./gradlew dist` 在根目录 `dist/` 生成 `PlumBot-Bukkit.jar`、`PlumBot-Velocity.jar`、`PlumBot-Hytale.jar`。产物里已包含 `me.regadpole.plumbot.api` 包（`:common` 以 `api(project(":api"))` 声明，Shadow 一并打入），直接引用 jar 即可：

```kotlin
dependencies {
    compileOnly(files("libs/PlumBot-Bukkit.jar")) // 只用于编译，不进入你的产物
}
```

必须 `compileOnly` 而不是 `implementation`：把 api 类打进自己的 jar，同一份接口会被两个 ClassLoader 各加载一次，拿到的实例与你声明的接口不是同一个类型，表现为 `ClassCastException`。编译用的 jar 要与服务器上运行的 PlumBot 是同一构建产物：当前版本 `3.0.0-beta1`（`gradle.properties`），`:api` 接口仍可能变动，升级 PlumBot 后要用新的 `dist/` 产物重新编译。

### 2. 运行期：普通插件依赖

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
depend: [PlumBot]
```

Bukkit 侧插件名是 `PlumBot`（见 [`bukkit/src/main/resources/plugin.yml`](../bukkit/src/main/resources/plugin.yml)）。只是可选集成时用 `softdepend`，并且必须判空取 API。

## 二、取 API 实例

两个入口都在 `me.regadpole.plumbot.api`：

- `PlumBotAPI.get()`：未初始化时抛 `IllegalStateException`，消息为 `PlumBotAPI 尚未初始化！请确保 PlumBot 已正确加载且处于已启用状态。`
- `PlumBotApiProvider.getInstanceOrNull()`：未初始化返回 `null`。

没有 `PlumBotAPI.getOrNull()`。`depend` 声明依赖时直接 `get()`；`softdepend` 或在自己 `onEnable` 早期调用，先 `getInstanceOrNull()` 判空。

Bukkit 还可以走服务管理器（`PlumBotBukkit` 在 `onEnable` 里注册了 `PlumBotAPI`）：`Bukkit.getServicesManager().getRegistration(PlumBotAPI::class.java)?.provider`。Velocity 侧在 `ProxyInitializeEvent` 之后同样 `PlumBotAPI.get()`（`PlumBotVelocity` 已注册）。

## 三、发送与查询

`PlumBotAPI` 的发送、绑定、成员查询方法全部是 **`suspend` 函数**，返回 `Unit` 或具体值，不返回 `CompletableFuture`：

```kotlin
scope.launch {
    val api = PlumBotAPI.get()
    api.sendGroupMessage(groupId = 123456789L, message = "服务器 TPS 当前为 20.0")
    api.sendGroupImage(groupId = 123456789L, imageFile = File(dataFolder, "ranking.png"))
    api.broadcastToAllGroups("服务器将在 10 分钟后例行维护。") // 方法名是 broadcastToAllGroups
}
```

完整清单照抄 [`api/src/main/kotlin/me/regadpole/plumbot/api/PlumBotAPI.kt`](../api/src/main/kotlin/me/regadpole/plumbot/api/PlumBotAPI.kt)，常用的几组：

- **发送**：`sendGroupMessage(groupId, message, asImage = false)`、`sendGroupMessageAt(groupId, userId, message)`、`sendGroupMessageAtAll(groupId, message)`、`sendUserMessage(userId, message, asImage = false)`、`sendGroupImage(groupId, imageFile)` / `sendGroupImage(groupId, imageBytes)`、`broadcastToAllGroups(message, asImage = false)`
- **绑定**：`getBindingUser(playerName): Long?`、`getBindingUser(uuid: UUID): Long?`、`getBindingAccounts(userId): Map<String, UUID?>`、`addBinding(userId, playerName, uuid = null): Boolean`、`removeBinding(playerName): Boolean`、`isPlayerWhitelisted(playerName): Boolean`
- **鉴权与属性**：`isBotAdmin(userId)`、`getGroupMemberInfo(groupId, userId): MemberInfo?`、`isGroupAdmin(groupId, userId)`、`boundGroupIds: Set<Long>`、`currentBotId: String`
- **Java 调用方**：用 `*Async` 重载，返回 `CompletableFuture`，如 `sendGroupMessageAsync(groupId, message)`、`broadcastToAllGroupsAsync(message)`、`getBindingUserAsync(playerName)`

## 四、订阅事件

```kotlin
import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.event.GroupMessageEvent

// 把自己的插件包成 PlumBot 认识的插件凭据，注销时用它定位
val pluginHandle = object : Plugin {
    override val name: String = this@MyPlugin.name
    override val version: String = this@MyPlugin.description.version
    override val isEnabled: Boolean get() = this@MyPlugin.isEnabled
}

val handle = api.subscribeGroupMessage(pluginHandle) { event: GroupMessageEvent ->
    println("群 [" + event.groupId + "] 成员 [" + event.userId + "]：" + event.message)
}
handle.close() // 可选，close() 幂等
```

要点：`subscribeGroupMemberDecrease(pluginHandle) { event -> ... }` 结构相同；回调在触发它的 Bot 线程上同步执行，不在 Bukkit 主线程，要改世界状态先切回宿主主线程；回调里要调 `suspend` 方法就自己开协程，不要阻塞回调线程。

注销有两条路径：自己的 `close()`，以及宿主插件 disable 时 Bukkit 的 `PluginDisableEvent`（[`bukkit/.../listener/PluginListener.kt`](../bukkit/src/main/kotlin/me/regadpole/plumbot/bukkit/listener/PluginListener.kt) 调 `BotEventDispatcher.unregisterAllFor(plugin)` 与 `api.extensionRegistry.unregisterAllFor(plugin)`）。重载频繁的场景两条都要在。

## 五、自定义 Bot 适配器

只有接入新协议（KOOK、Telegram、Discord 等）才需要，三步。

**1. 实现 `IBot`**（`me.regadpole.plumbot.api.bot.IBot`）：至少 `metadata`、`start(): IBot`、`shutdown()`、`sendGroupMsg(targetId, message)`、`sendGroupImage(targetId, imageFile): Boolean`、`getGroupName(groupId)`、`checkUserInGroup(userId, groupId)`。生命周期方法名是 `start()` / `shutdown()`，没有 `doStart()` / `doShutdown()`。想省掉群缓存、出图协程与渲染降级，可以继承 `AbstractBotAdapter` —— 它在 `:common` 的 `me.regadpole.plumbot.bot` 包，不在 `:api`。

**2. 实现 `BotFactory`**：

```kotlin
object MyBotFactory : BotFactory {
    override val metadata = BotAdapterMetadata(
        type = "my-bot",
        displayName = "My Bot",
        capabilities = setOf(BotCapability.GROUP_MESSAGE_SEND, BotCapability.IMAGE_SEND)
    )

    override fun create(context: PlatformContext): IBot = MyBot(context, metadata)
}
```

能力位枚举名是 `GROUP_MESSAGE_SEND`、`USER_MESSAGE_SEND`、`IMAGE_SEND`、`GROUP_MEMBER_QUERY`、`GROUP_MEMBER_CHECK`、`GROUP_MESSAGE_RECEIVE`、`GROUP_MEMBER_DECREASE_RECEIVE`，没有 `SEND_GROUP_MSG` / `SEND_IMAGE`。`BotAdapterMetadata` 另有 `supportedPlatforms` 与 `requiredPlugins`（都默认空集），`BotProvider.loadBot` 会按这两项校验。

**3. 注册**：`val ok = api.extensionRegistry.registerExternalFactory(MyBotFactory, pluginHandle)`。成功返回 `true`；type 与已注册者（含内置的 `onebot`、`miraimc`）冲突时**抛 `AdapterRegistrationException`**，不是返回 `false`，用 `runCatching` 包住。注册后把 `config.yml` 的 `bot.type` 改成 `my-bot`，重启或 `/plumbot reload` 生效。

## 六、单元测试

`PlumBotAPI` 是纯接口，不必起服务端，`mockk` 之后注入全局实例即可（`PlumBotApiProvider.register(mockk<PlumBotAPI>(relaxed = true))`，测试后 `PlumBotApiProvider.unregister()`）。`suspend` 方法用 `runTest` 驱动（`kotlinx-coroutines-test`），校验用 `coVerify`。

## 七、排障三点

1. **`NoSuchMethodError` / `NoClassDefFoundError`**：编译用的 api jar 与服务器上的 PlumBot 不是同一构建产物。换成当前的 `dist/` jar 重新编译，不要留着旧的缓存依赖。
2. **能力缺失、调用没反应**：先确认 Bot 在线（`currentBotId`）与账号在群里的权限（`isGroupAdmin`、`getGroupMemberInfo`）。`BotCapability` 只声明适配器支持什么，不代表运行期一定可用。`isBotConnected` 已废弃，不要用它判断。
3. **加载顺序**：`depend: [PlumBot]` 保证 PlumBot 先 enable，`get()` 可直接用；`softdepend` 时先用 `getInstanceOrNull()` 判空，PlumBot 未启用或已卸载都能安全跳过。

相关文档：[`adding-platform.md`](adding-platform.md)（接入新宿主平台）、[`internal-dev.md`](internal-dev.md)（内部实现流程）。
