# 附属插件的测试策略（mock / 单测 / 集成测）

> **目标读者**：写自己的 Bukkit 插件并依赖 PlumBotAPI 的开发者。本指南给出可复制的测试模式，覆盖 JUnit5 + MockK + Mockito + Bukkit Mock 库三种主流栈。

## 1. 测试金字塔

| 层 | 范围 | 工具 | 不依赖 |
|---|---|---|---|
| 纯单元 | 业务逻辑被 mock 化的 PlumBotAPI | JUnit5 + MockK | 不依赖 Bukkit、不依赖 PlumBot |
| 模块集成 | 你的插件 main 类 + listener 在 fake server 中跑 | Bukkit MockServer / MockBukkit | 不依赖真实 PlumBotBukkit jar |
| 真实环境 | 端到端；人工 smoke | Test server | 需要 PlumBot-Bukkit.jar 实际安装 |

本文专注**前两层**——第三层不在自动化范围内。

## 2. 公共 API 用 `interface` 设计带来的红利

`common/.../bot/BotProvider.kt`、`common/.../bot/BotEventDispatcher.kt`、`common/.../PlumBotAPI.kt` 不是 `final class`，但 `PlumBotAPI` 是 `object`。幸运的是，绝大多数"外部依赖"——`IBot`、`BotFactory`、`BotAdapterMetadata`、`BotCapability`、`PlatformContext`——都是 `interface` 或 `data class`，**可被 mock**。

`PlumBotAPI` 自己虽然不可 mock，但是你的**业务代码**不应直接依赖它；推荐用**小接口 + 适配器模式**把它包一层：

```kotlin
// 在你的插件代码里：
interface BotGateway {
    fun sendGroupMessage(groupId: Long, message: String): Boolean
    fun subscribeGroupMessages(handler: (GroupMessageEvent) -> Unit): ListenerHandle
}

class PlumBotGateway : BotGateway {
    override fun sendGroupMessage(groupId: Long, message: String): Boolean =
        PlumBotAPI.sendGroupMessage(groupId, message)
    override fun subscribeGroupMessages(handler: (GroupMessageEvent) -> Unit) =
        PlumBotAPI.subscribeGroupMessages(handler)
}
```

这样你可以在测试里 mock `BotGateway`，让业务测试**完全独立**于 PlumBot 真身。

## 3. JUnit5 + MockK 模式（推荐）

### 3.1 引入依赖

```kotlin
// build.gradle.kts (testImplementation)
testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
testImplementation("io.mockk:mockk:1.13.11")
```

### 3.2 单测示例：监听 + 发送

```kotlin
import io.mockk.*
import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.ListenerHandle
import kotlin.test.Test
import kotlin.test.assertTrue

class MyPingHandlerTest {

    private class StubHandle : ListenerHandle {
        var closed = false
        override fun close() { closed = true }
    }

    @Test
    fun `responds with pong on group ping`() {
        // 替换单例上的入口：MockK 的 mockkObject 在运行时会替换 PlumBotAPI 内的方法
        mockkObject(PlumBotAPI)
        val stubHandle = StubHandle()
        var captured: (GroupMessageEvent) -> Unit = {}
        every { PlumBotAPI.subscribeGroupMessages(any()) } answers {
            captured = firstArg()
            stubHandle
        }
        every { PlumBotAPI.sendGroupMessage(any(), any()) } returns true

        val handler = MyPingHandler()
        handler.attach()

        captured(GroupMessageEvent(
            botId = "onebot", groupId = 1L, userId = 2L,
            message = "!ping", timestamp = 0L,
        ))

        verify { PlumBotAPI.sendGroupMessage(1L, "pong") }
        assertTrue(handler.attached)
        handler.detach()
        assertTrue(stubHandle.closed)
        unmockkAll()
    }
}
```

### 3.3 mock `PlumBotAPI.getExtensionRegistry()`

```kotlin
val ext = mockk<me.regadpole.plumbot.bot.BotExtensionRegistry>(relaxed = true)
mockkObject(PlumBotAPI)
every { PlumBotAPI.getExtensionRegistry() } returns ext

val ok = myPlugin.onEnable()
verify { ext.registerExternalFactory(any(), any()) }
```

> `PlumBotAPI` 是 `object`；MockK 的 `mockkObject(PlumBotAPI)` 仅在测试 JVM 内可见。**注意**：每次 `mockkObject` 后必须 `unmockkAll()` 或 `@AfterAll` 复位，否则污染同 JVM 的后续测试。

## 4. JUnit5 + Mockito 模式（Java 习惯）

Mockito 对 Kotlin object 的支持比 MockK 弱一些，但仍可用：

```kotlin
// 用 mockito-inline + mockStatic 来 mock 静态成员（>=5.0）
mockStatic(PlumBotAPI::class.java, CALLS_REAL_METHODS).use {
    `when`<PlumBotAPI>(PlumBotAPI.getInstance()).thenReturn(...)
    `when`(PlumBotAPI.sendGroupMessage(anyLong(), anyString())).thenReturn(true)
    ...
}
```

但因为 `PlumBotAPI.getInstance()` 实际上**就是 `this`**，Mockito 对它做不了真正的替换——**MockK 仍是首选**。

## 5. 用 MockBukkit 做模块集成测

[MockBukkit](https://github.com/MockBukkit/MockBukkit) 提供 `MockServer`、`JavaPluginMockFactory` 等；你可以塞一个真实 `JavaPlugin` 实例进 mock server。

### 5.1 初始化最小 stub server

```kotlin
import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class BukkitIntegrationTest {
    protected lateinit var server: ServerMock
    @BeforeEach fun setUp() { server = MockBukkit.mock() }
    @AfterEach  fun tearDown() { MockBukkit.unmock() }
}
```

### 5.2 模拟 PlumBotAPI 注册

```kotlin
@Test
fun `MyCoolPlugin attaches when PlumBotAPI is registered`() {
    // 1. 启动一个 fake "PlumBot" 宿主 plugin（不必是真 jar，只要 ServicesManager 注册了 API）
    val fakePlumBot = MockBukkit.createMockPlugin("fake.plumbot")
    val api = mockk<me.regadpole.plumbot.PlumBotAPI>(relaxed = true)
    every { api.getExtensionRegistry() } returns mockk(relaxed = true)

    server.servicesManager.register(
        me.regadpole.plumbot.PlumBotAPI::class.java, api, fakePlumBot,
        org.bukkit.plugin.ServicePriority.Normal,
    )

    // 2. 启动你的插件
    val myPlugin = MockBukkit.load(MyCoolPlugin::class.java)

    // 3. 验证 attach 已触发注册
    verify { api.getExtensionRegistry() }
    // 你的插件应在 mock 中注册 listener；视具体业务断言。
}
```

> 注：MockBukkit 不解析 `plugin.yml` 中不存在的 `name`/`depend`/`softdepend`（无 I/O 加载），但 `ServicesManager.getRegistration(...)` 的行为是真实的，可通过手动 `register` 验证下游插件。

## 6. 测试 `BotExtensionRegistry` 边界

`BotExtensionRegistry` 是非 `object` 的普通 `class`，所以直接 `new` 即可：

```kotlin
val ext = BotExtensionRegistry(BotProvider)
val plugin = FakePlugin("myplugin")
assertTrue(ext.registerExternalFactory(MyFactory, plugin))
assertFalse(ext.registerExternalFactory(MyFactory, plugin))  // 重复注册 → false
val removed = ext.unregisterAllFor(plugin)
assertEquals(1, removed)
```

完整示例见仓库内 `common/src/test/kotlin/.../BotExtensionRegistryTest.kt`。

## 7. 测试 dispatcher listener 链路

`BotEventDispatcher` 是 `object` 但注册 / 注销 / dispatch 都是**普通方法**。最简单的方式是调用 `dispatchGroupMessage` 让内部分发路径跑通：

```kotlin
val fired = AtomicInteger(0)
val handle = BotEventDispatcher.registerGroupMessageHandler { fired.incrementAndGet() }
BotEventDispatcher.dispatchGroupMessage("msg", 1L, 2L)
assertEquals(1, fired.get())
handle.close()
BotEventDispatcher.dispatchGroupMessage("msg", 1L, 2L)
assertEquals(1, fired.get())   // 不再触发
```

完整示例见 `common/src/test/kotlin/.../BotEventDispatcherTest.kt`。

## 8. 不要做的事

* **不要在测试中调用真实的 WebSocket / HTTP**：可能与 CI 网络策略相冲突。`PlumBot` 不强制 adapter 必须联网，所以你应在自己 adapter 内抽出"transport interface"再注入。
* **不要启动 4 个 Gradle 子模块**：单测只针对 `common` 与你自己的插件代码，不必拉起 `bukkit:shadowJar`。
* **不要 mock `internal` 类型**：被 `internal` 修饰的方法或类应被视作私有；如不得不通过 mock 验证行为，说明业务耦合到 framework 内部，应在 spec 阶段就申请升级到 `@PublicApi`/`@StableApi`。

## 9. CI 建议

| 任务 | 命令 |
|---|---|
| `common` 单测 | `./gradlew :common:test` |
| `bukkit` 编译（确保你引用的 API 仍存在） | `./gradlew :bukkit:compileKotlin` |
| 契约校验 | `./gradlew :common:checkApiContract` |
| shadowJar（仅发版用） | `./gradlew :bukkit:shadowJar` |

把上述几条加到 GitHub Actions / GitLab CI 上即可。若你 fork 出 PlumBot 的 platform 模块，请同时跑 `:yourplatform:compileKotlin` / `:yourplatform:test`。

## 10. 相关文档

- [extending-from-other-plugins.md](./extending-from-other-plugins.md)
- [migrating-plumbotapi-v2.md](./migrating-plumbotapi-v2.md)
- [extending-bot-adapter.md](./extending-bot-adapter.md)
- [debugging.md](./debugging.md)
