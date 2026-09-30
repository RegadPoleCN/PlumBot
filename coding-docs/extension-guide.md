# PlumBot 附属插件与外部扩展开发手册

本文档面向希望在自己的 Minecraft 插件中集成 PlumBot、调用群互通能力、监听 Bot 事件，或编写自定义第三方 Bot 适配器的开发者。

---

## 📦 1. 依赖接入

PlumBot 将对外公开契约完全隔离在独立的 `:api` 模块中，外部插件**仅需且只能依赖 `:api` 模块**，无需依赖重型的 `:common` 或 `:bukkit`。

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    // 若使用本地版本或私有仓库，请配置对应 Maven URL
}

dependencies {
    // 仅依赖公开 API 契约，版本号请填入当前发布的具体版本（如 3.0.0 或 3.0.1-beta1）
    compileOnly("me.regadpole.plumbot:api:<version>")
}
```

### `plugin.yml` (Bukkit 宿主声明)
```yaml
name: MyAwesomePlugin
version: 1.0.0
main: com.example.plugin.MyPlugin
# 声明软依赖或硬依赖
depend: [PlumBot]
```

---

## 🔑 2. 获取 `PlumBotAPI` 实例

`PlumBotAPI` 采用面向服务架构（Service-Oriented Architecture），本身为一个 `@StableApi interface`。提供了多种标准获取途径：

### 途径 A：跨平台静态入口（推荐最简）
```kotlin
import me.regadpole.plumbot.api.PlumBotAPI

// 获取当前全局绑定的 API 实现实例（若 PlumBot 尚未就绪将抛出 IllegalStateException）
val api: PlumBotAPI = PlumBotAPI.get()

// 安全获取（未就绪时返回 null）
val safeApi: PlumBotAPI? = PlumBotAPI.getOrNull()
```

### 途径 B：Bukkit 服务管理器（Bukkit 标准做法）
```kotlin
import me.regadpole.plumbot.api.PlumBotAPI
import org.bukkit.Bukkit

val registration = Bukkit.getServicesManager().getRegistration(PlumBotAPI::class.java)
val api: PlumBotAPI? = registration?.provider
```

### 途径 C：Velocity 代理端插件获取
在 Velocity 代理端插件中，同样在 `ProxyInitializeEvent` 触发后直接调用跨平台门面：
```kotlin
import me.regadpole.plumbot.api.PlumBotAPI

// Velocity 插件可在代理端全局初始化后直接安全调用
val api: PlumBotAPI = PlumBotAPI.get()
```

---

## 🚀 3. 核心功能调用示例

### 1) 发送群消息与广播
所有的发送方法均是非阻塞异步执行，并返回标准的 `CompletableFuture`：

```kotlin
val api = PlumBotAPI.get()

// 1. 发送纯文本群消息
api.sendGroupMessage(groupId = 123456789L, message = "服务器 TPS 当前为 20.0")
    .thenAccept { println("消息发送成功！") }
    .exceptionally { err -> 
        println("发送失败: ${err.message}")
        null 
    }

// 2. 向所有配置文件中配置的已挂载群广播消息
api.broadcastGroupMessage("服务器将在 10 分钟后例行维护。")

// 3. 发送群图片
val imageFile = File(dataFolder, "ranking.png")
api.sendGroupImage(groupId = 123456789L, image = imageFile)
```

### 2) 玩家绑定关系查询 (Whitelist & Binding)
```kotlin
import java.util.UUID

// 通过游戏内玩家 UUID 查询绑定的 QQ 号
api.getBindingByPlayer(player.uniqueId).thenAccept { binding ->
    if (binding != null) {
        player.sendMessage("您绑定的 QQ 账号为: ${binding.userId}")
    } else {
        player.sendMessage("您尚未绑定任何 QQ 账号！")
    }
}

// 通过 QQ 号查询绑定的游戏账号
api.getBindingsByUser(123456789L).thenAccept { bindings ->
    println("该 QQ 绑定了 ${bindings.size} 个游戏角色: ${bindings.map { it.playerName }}")
}
```

---

## 🎧 4. 监听与订阅 Bot 事件

PlumBot 提供了类型安全的事件监听机制。为了**杜绝插件重载 (`/reload`) 导致的内存泄漏**，强烈建议将监听器与附属插件的生命周期绑定。

### 1) 订阅群消息事件
```kotlin
import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.event.GroupMessageEvent

// 将自己的 Bukkit 插件包装为 PlumBot 能够识别的 Plugin 凭据
val pluginHandle = object : Plugin {
    override val name: String = this@MyPlugin.name
    override val version: String = this@MyPlugin.description.version
    override val isEnabled: Boolean get() = this@MyPlugin.isEnabled
}

// 订阅事件：传入 pluginHandle 可以在你的插件 disable 时自动注销监听，零泄漏风险！
val handle = api.subscribeGroupMessage(pluginHandle) { event: GroupMessageEvent ->
    println("收到群 [${event.groupId}] 成员 [${event.userId}] 的消息: ${event.message}")
    
    if (event.message == "ping") {
        api.sendGroupMessage(event.groupId, "pong!")
    }
}

// 如果需要手动注销（可选，close 操作具备幂等性）：
handle.close()
```

---

## 🔌 5. 编写与注册自定义 BotAdapter

如果你需要接入不支持的聊天协议平台（如 KOOK、Telegram、Discord 或自定义协议），可以实现自己的 BotAdapter 并注册进 PlumBot。

### 第一步：实现 `IBot`
```kotlin
import me.regadpole.plumbot.api.bot.AbstractBotAdapter
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.platform.PlatformContext
import java.util.concurrent.CompletableFuture

class MyCustomBotAdapter(
    override val context: PlatformContext,
    override val metadata: BotAdapterMetadata
) : AbstractBotAdapter() {

    override fun doStart() {
        // 初始化并启动你的底层客户端连接
    }

    override fun doShutdown() {
        // 断开连接，释放资源
    }

    override fun sendGroupMsg(groupId: Long, msg: String): CompletableFuture<Boolean> {
        // 调用三方 SDK 发送文本消息
        return CompletableFuture.completedFuture(true)
    }

    override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
        // 拉取成员信息
        return CompletableFuture.completedFuture(null)
    }
}
```

### 第二步：实现 `BotFactory`
```kotlin
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.platform.PlatformContext

object MyCustomBotFactory : BotFactory {
    override val metadata: BotAdapterMetadata = BotAdapterMetadata(
        type = "my-bot",
        displayName = "My Custom Bot",
        capabilities = setOf(
            BotCapability.SEND_GROUP_MSG,
            BotCapability.SEND_IMAGE
        )
    )

    override fun create(context: PlatformContext): IBot {
        return MyCustomBotAdapter(context, metadata)
    }
}
```

### 第三步：注册到运行期注册表
```kotlin
val registry = api.getExtensionRegistry()
val success = registry.registerExternalFactory(MyCustomBotFactory, pluginHandle)
if (success) {
    println("自定义 Bot 适配器注册成功！可在 config.yml 中配置 bot.type: my-bot 使用")
}
```

---

## 🧪 6. 单元测试与 Mock 指南

得益于当前版本的 `PlumBotAPI` 是纯接口契约设计，外部附属插件在编写单元测试时**完全无需启动真实的 Minecraft 服务端或 PlumBot**，直接使用 MockK 即可轻松模拟：

```kotlin
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.api.PlumBotApiProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class MyPluginLogicTest {

    private lateinit var mockApi: PlumBotAPI

    @BeforeEach
    fun setup() {
        mockApi = mockk(relaxed = true)
        every { mockApi.sendGroupMessage(any(), any()) } returns CompletableFuture.completedFuture(true)
        
        // 绑定 Mock 实例
        PlumBotApiProvider.register(mockApi)
    }

    @AfterEach
    fun tearDown() {
        PlumBotApiProvider.unregister()
    }

    @Test
    fun testSendMessageOnPlayerJoin() {
        // 执行你的插件业务逻辑
        PlumBotAPI.get().sendGroupMessage(12345L, "玩家加入了游戏")

        // 验证是否正确调用了 PlumBot 的发送能力
        verify(exactly = 1) { mockApi.sendGroupMessage(12345L, "玩家加入了游戏") }
    }
}
```
