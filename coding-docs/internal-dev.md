# PlumBot 内部核心开发手册

本文档面向 PlumBot 仓库维护者与核心贡献者，阐述如何在 `:common` 模块新增端到端功能闭环、扩展配置与数据库，以及接入新服务端平台。

---

## 🛠️ 1. 新增功能端到端全流程 (Feature Workflow)

在 PlumBot 中新增一个玩家或群成员可见的交互功能（如群指令响应、游戏事件联动），标准流程分为 4 步：

### 第一步：声明配置与消息模板
1. **`common/src/main/resources/config.yml`**：在对应节点下增加功能开关与配置参数。
2. **`common/src/main/resources/messages.yml`**：定义反馈消息模板，支持 MiniMessage 标签格式（如 `<gray>[<aqua>PlumBot</aqua>]</gray> <yellow>%msg%</yellow>`）。
3. **配置迁移感知 (`ConfigMigrator`)**：
   在 `common/.../internal/config/ConfigMigrator.kt` 中登记新字段，确保旧服热升级时自动补全默认配置项而不破坏用户原有文件。

### 第二步：文本安全与渲染 (防越权注入)
当外部群消息需要转发或渲染到 Minecraft 游戏内时，**必须调用标签转义工具**：
```kotlin
import me.regadpole.plumbot.utils.ComponentUtils.escapeMiniMessageTags
import me.regadpole.plumbot.utils.ComponentUtils.getComponentFromMiniMsg

// ❌ 错误示范：未经转义直接拼接用户输入，会导致玩家利用 <click:run_command:/op> 实施提权
val rawMini = "<gray>$user: $untrustedUserInput</gray>"

//  正确示范：外部不可信文本强制转义
val safeMini = "<gray>${escapeMiniMessageTags(user)}: ${escapeMiniMessageTags(untrustedUserInput)}</gray>"
val component = getComponentFromMiniMsg(safeMini)
context.sendMessage(component)
```

### 第三步：注册群响应指令 (`DefaultBotHandler`)
在 `common/.../listener/DefaultBotHandler.kt` 的 `init` 块中通过轻量 DSL 注册指令：
```kotlin
registerCommand(
    CommandRegistration(
        keys = context.config.getStringList("command", "myfeature", "alias"),
        feature = "myfeature",
        regex = { trigger, _ -> """^$trigger\s+(?<arg>\S+)$""" },
        payload = { trigger, _, msg -> msg.removePrefix(trigger).trim() },
        handler = { payload, groupId, userId ->
            // 校验 Bot 适配器是否声明了所需能力
            bot.requireCapability(BotCapability.SEND_GROUP_MSG)
            
            // 执行具体业务逻辑并回复群消息
            bot.sendMsg(true, groupId, "执行完成: $payload")
        }
    )
)
```

---

## 💾 2. 数据库与持久层扩展

数据库组件统一抽象在 `me.regadpole.plumbot.api.database` 与 `me.regadpole.plumbot.database`。

### 1) 架构设计
- **`IDatabase`**：数据库操作接口，定义在 `:api` 模块。
- **`AbstractBindingDatabase`**：底层基于 TabooLib Database + HikariCP/SQLite 的异步抽象基类。
- **`DatabaseProvider`**：全局数据持久层门面，单例管理连接生命周期与线程调度。

### 2) 新增查询/操作方法规范
1. 在 `api/.../database/IDatabase.kt` 中声明方法契约：
   ```kotlin
   fun getRecordByCustomKey(key: String): CompletableFuture<Binding?>
   ```
2. 在 `common/.../database/AbstractBindingDatabase.kt` 中编写 SQL 实现：
   ```kotlin
   override fun getRecordByCustomKey(key: String): CompletableFuture<Binding?> {
       return table.select(dataSource) {
           where { "custom_key" eq key }
       }.firstOrNull { Binding.of(this) }
   }
   ```
3. 领域模型映射采用强类型构造器 `Binding.of(resultSet)`，严禁直接在业务层直接解包原始 JDBC 游标。

---

## ⚡ 3. 异步调度与缓存规范

### 1) 协程缓存体系 (`DefaultBotCache` & `DefaultGroupMemberCache`)
底层基于 `Caffeine` + `Aedile` 协程挂起加载扩展：
- 禁止在 Minecraft 主线程或事件监听器中调用阻塞方法（如 `.get().get()` 或 `.join()`）。
- 优先使用挂起函数 `getAsync(key)`。在非协程环境中使用 `get(key)` 返回的 `CompletableFuture` 处理异步链。

### 2) 任务调度器 (`PlatformScheduler`)
跨平台调度必须通过 `PlatformContext.scheduler` 完成：
```kotlin
// 异步执行耗时 I/O 任务
context.scheduler.runTaskAsynchronously {
    // 耗时网络请求或数据库操作
}

// 延迟调度任务 (使用 Kotlin Duration)
context.scheduler.runTaskLater(10.seconds) {
    // 延迟逻辑
}
```

---

## 🔌 4. 接入新游戏服务端平台 (如 Velocity / Fabric)

得益于 PlumBot 高度内聚的 `PlatformContext` 设计，接入一个全新平台只需在子模块中实现以下组件：

### 1) 实现 `PlatformContext`
实现位于 `me.regadpole.plumbot.api.platform.PlatformContext` 的全部接口：
```kotlin
class VelocityPlatformContext(
    private val server: ProxyServer,
    override val config: PlatformConfig,
    override val datasource: PlatformConfig,
    override val dataDirectory: Path,
    override val scheduler: PlatformScheduler,
    override val playerService: PlatformPlayerService
) : PlatformContext {

    override val platformType: PlatformType = PlatformType.VELOCITY

    override val supportedCapabilities: Set<PlatformCapability> = setOf(
        PlatformCapability.CHAT_RECEIVE,
        PlatformCapability.CHAT_BROADCAST,
        PlatformCapability.COMMAND_DISPATCH
    )

    override fun log(level: LogLevel, message: String) {
        // 调用平台 Logger 输出
    }

    override fun sendMessage(message: Component) {
        // 使用 Adventure 广播全代理服务器
        server.sendMessage(message)
    }

    override fun isPluginAvailable(name: String): Boolean {
        return server.pluginManager.isLoaded(name)
    }
}
```

### 2) 平台生命周期装配
在平台插件入口处（如 `@Plugin` 类中）：
1. 加载配置文件，实例化 `PlatformContext`。
2. 调用 `DatabaseProvider.start(...)` 初始化数据库。
3. 注册内置 Bot 工厂：`BotProvider.registerFactory(OneBotFactory)`。
4. 注册外部 API 实例：`PlumBotApiProvider.register(PlumBotApiImpl(context))`。
5. 启动 Bot 客户端：`PlumBot.loadBot()`。
6. 在卸载回调中调用 `disable()`，优雅关闭网络与数据库连接。
