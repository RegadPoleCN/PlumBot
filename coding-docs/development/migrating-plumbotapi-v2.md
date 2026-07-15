# PlumBotAPI v2 迁移指南（class → object）

> **适用版本**：从 `public-api-extension` 之后；变更前请确认你使用的 PlumBot-Bukkit jar 版本号（参见 `plugin.yml` 的 `version:` 字段）。
>
> **影响面**：第三方插件或附属功能模块中**直接构造 `PlumBotAPI`** 的代码。这一份迁移指南只针对 `PlumBotAPI` 自身的破坏性变更；其它公共 API（如 `IBot.sendMsg`、`BotProvider.registerFactory`）保持完全兼容。
>
> 相关变更：
>
> - `.trae/specs/public-api-extension/spec.md`：扩展公开 API 主 spec
> - [extending-from-other-plugins.md](./extending-from-other-plugins.md)：第三方插件入门
> - [public-api-changelog.md](./public-api-changelog.md)：公开 API 版本日志

## 1. 变更摘要

| 维度 | v1.x（旧） | v2.x（现） |
|---|---|---|
| `PlumBotAPI` 形态 | `class PlumBotAPI(private val plugin: PlumBot)`（须由调用方构造） | `object PlumBotAPI`（Kotlin 单例） |
| 关联方式 | `PlumBotAPI(plugin)` 构造时绑定 | `PlumBotAPI.attach(plugin)` / `detach()` 显式注入 |
| 公共访问 | 必须用 Bukkit `ServicesManager` 取 provider | 提供三入口：ServicesManager / `getInstance()` / `getAttachedPlugin()` |
| 多入口失败 | 仅一入口（`Bukkit.getPluginManager().isPluginEnabled` 必须先返回 true） | `runCatching { PlumBotAPI.getInstance() }.getOrNull()` 即时容错 |
| 发送消息 | 通过 `IBot.sendGroupMsg(...)`（取 `BotProvider.getBot()`） | `PlumBotAPI.sendGroupMessage(...)` 等便捷方法 |
| 事件订阅 | 无（仅有内部 `BotEventDispatcher`） | `PlumBotAPI.subscribeGroupMessages { ... }` |
| Adapter 注册 | 不可（必须源码改动 + 重编） | `PlumBotAPI.getExtensionRegistry().registerExternalFactory(...)` |

## 2. 破坏性变更：构造变 `attach()`

### 2.1 受影响的代码

唯一直接受影响的位置是**构造 `PlumBotAPI` 实例**：

```kotlin
// v1.x（旧）—— 现在编译失败
val api = PlumBotAPI(myPlugin)
```

特别提醒：**没有第三方代码应当直接构造 `PlumBotAPI`**。如果你之前这样写过，请把它当作 bug 修掉：

* 旧版本 `PlumBotAPI` 唯一允许的实例化入口是 `PlumBotBukkit.onEnable()`。其他位置拿到"第二个 PlumBotAPI 实例"既不能调用 `getConfig()`（返回的是构造时传 `plugin` 的 `config`，看起来对，但与真实 attach 状态完全脱钩），也**不会**对外广播到 `ServicesManager`。

### 2.2 推荐的新写法

#### A. 委托给公共入口（默认推荐）

```kotlin
val api = server.servicesManager
    .getRegistration(PlumBotAPI::class.java)
    ?.provider as? PlumBotAPI
```

第三方插件在 `onEnable` 中调用时，PlumBot 已经 `attach`（取决于 `softdepend`/`depend` 顺序），provider 即单例本身。

#### B. 静态访问点

```kotlin
val api = PlumBotAPI.getInstance()                       // 未 attach 时抛 IllegalStateException
val apiOrNull = runCatching { PlumBotAPI.getInstance() }.getOrNull()
```

#### C. 内嵌已 attach 状态的宿主引用

```kotlin
val host = org.bukkit.Bukkit.getPluginManager()
    .getPlugin("PlumBot") as? org.bukkit.plugin.java.JavaPlugin
```

> 2.0 起不再提供独立的 `PluginProvider` 服务。宿主 `JavaPlugin` 直接通过 Bukkit `PluginManager` 按名字取即可，API 表面保持单一入口 `PlumBotAPI`。

#### D. attach 一次但仅用于测试

```kotlin
class MyTest {
    @Before fun setUp() {
        // 此 API 仅用于测试场景或非 Bukkit 环境下手动注入；
        // 生产代码应通过 ServicesManager 或 getInstance() 取实例。
        PlumBotAPI.attach(FakePlumBotForTest())
    }
    @After fun tearDown() { PlumBotAPI.detach() }
}
```

## 3. 行为差异与陷阱

### 3.1 `getConfig()` 的变化

* 旧版本：`PlumBotAPI(plumBot)` 直接返回构造时绑定 `plugin` 的 `config`。
* 新版本：返回的是 `PlumBotAPI.attach(...)` 时绑定 `plugin` 的 `config`；**未 attach 时抛 `IllegalStateException` 并附 KDoc 提示**。

> 任何对 `getConfig()` 的访问都建议用 `runCatching { ... }.getOrNull()`，或先通过 ServicesManager 校验存在性。

### 3.2 `PlumBotBukkit.onEnable` 同步顺序

* 旧：`PlumBotAPI(this)` 构造后立刻注册到 ServicesManager。
* 新：**必须先 `PlumBotAPI.attach(this)`**，再 `server.servicesManager.register(PlumBotAPI::class.java, PlumBotAPI, ...)`。

如果你基于 PlumBot fork 出自己的 platform 模块，按这个顺序写就一定不会出错。

### 3.3 重载（`/plumbot reload`）

* `PlumBotAPI.detach()` 与 `attach(plugin)` 都会清空 / 重置 `extensionRegistry`、`attached` 引用。
* listener 自动随 `BotEventDispatcher.clearAllListeners()` 清空，但**你拿到的 `ListenerHandle.close()` 仍可调用**（幂等）。
* `BotEventDispatcher.clearAllListeners()` 由 `BotProvider.unloadBot()` 调用，因此 reload 会**先**清 listener、再调 `Plugin.disable()`。

### 3.4 多 attach 行为

| 调用序列 | 行为 |
|---|---|
| `attach(A)` → `attach(B)` | 后者覆盖前者并写 WARN 日志 |
| `attach(A)` → `detach()` → `getInstance()` | 抛 `IllegalStateException` |
| 未 `attach` → `getInstance()` | **不抛**，返回 `object PlumBotAPI` 本身（`getInstance()` 与 Kotlin `object` 同义） |

> 关键点：`getInstance()` 始终返回 `PlumBotAPI` 单例（即 `object` 自身），它从来不会因 `attach` 状态返回 `null`。"未 attach" 由 `getConfig()` / `getExtensionRegistry()` 等使用附属状态的入口来抛异常；这样第三方代码可以"先取实例，后查细节"。

## 4. 4 步迁移清单

1. **全文搜索**：`grep -RE "PlumBotAPI\(" --include="*.kt"`。
   * 找到的每一处都应改成 `PlumBotAPI.attach(this)`（宿主实现）或 `PlumBotAPI.getInstance()` / ServicesManager 取值（附属插件）。
2. **替换 send 入口**：所有 `BotProvider.getBot()?.sendGroupMsg(...)` 改为 `PlumBotAPI.sendGroupMessage(...)`（更简洁，且无 bot 时返回 `false` 不抛）。
3. **替换 listener 直调**：所有 `BotEventDispatcher.dispatchXxx` 改用 `PlumBotAPI.subscribeXxx`（`BotEventDispatcher` 标注为 internal）。
4. **加 `ServicesManager` key 校验**：如果你的旧代码用了 `getRegistration(BotProvider.javaClass)` 这种用 `object.javaClass` 作 key 的写法，请改成 `BotProvider::class.java`（与新注册 key 一致）。

## 5. 4 类典型代码片段

### 5.1 第三方插件：取实例（替换前 → 替换后）

```diff
- val api = server.servicesManager
-     .getRegistration(PlumBotAPI::class.java)?.provider as? PlumBotAPI
-     ?: return
+ val api = server.servicesManager
+     .getRegistration(PlumBotAPI::class.java)?.provider as? PlumBotAPI
+     ?: run {
+         logger.warning("PlumBot 未启用")
+         return
+     }
```

> 替换含义：补全"未启用"分支的提示，避免静默返回。

### 5.2 发送消息

```diff
- val bot = me.regadpole.plumbot.bot.BotProvider.getBot()
- if (bot != null) {
-     try { bot.sendGroupMsg(groupId, msg) } catch (e: Exception) { logger.warning(e.message) }
- }
+ val ok = PlumBotAPI.sendGroupMessage(groupId, msg)
+ if (!ok) logger.warning("PlumBotAPI.sendGroupMessage 不可用")
```

### 5.3 订阅事件

```diff
- me.regadpole.plumbot.bot.BotEventDispatcher.dispatchGroupMessage(...)    // 旧：无法订阅，只能触发
+ val handle = PlumBotAPI.subscribeGroupMessages { evt -> ... }
+ handle.close()    // 不再使用时主动 close
```

### 5.4 注册 adapter

```diff
- // 旧：必须改 PlumBot 源码、PR、重编。
- // 没有外部 API。
+ val ext = PlumBotAPI.getExtensionRegistry()
+ val ok = ext.registerExternalFactory(MyFactory, BukkitPlugin(this))
+ if (!ok) logger.warning("类型冲突或被内置占用，注册失败")
```

## 6. 验证清单

- [ ] 全仓无 `PlumBotAPI(...)` 构造调用残留。
- [ ] 所有使用 `PlumBotAPI.sendXxxMessage` / `subscribeXxx` 的位置用 `runCatching` 或 `Boolean` 返回值兜底。
- [ ] 不再直接 new `BotEventDispatcher` / 反射访问 `common/internal/*`。
- [ ] 第三方插件 `plugin.yml` 中 `depend: [PlumBot]` / `softdepend: [PlumBot]` 与你获取 API 的时机匹配——`depend` 时 PlumBot 必然已 attach。
- [ ] 如需做旧 → 新同步发布，给你的插件版本号加 `2.0.0`，并在 `plugin.yml` 描述里加 "Requires PlumBot 2.0+"。

## 7. 一键回滚（如果迁移不通）

理论上 v2.x 与 v1.x 不可能在同一 JVM 内共存（`PlumBotAPI` 单例占据全进程命名空间）。**真正的回滚**只能：

1. 卸载当前版本的 `PlumBot-Bukkit.jar`；
2. 安装你 fork 的 `PlumBot` v1.x 旧版本；
3. 在第三方插件中把 `PlumBotAPI.getInstance()` 改回 `servicesManager.getRegistration(...).provider as? PlumBotAPI`；
4. 恢复直接调 `BotEventDispatcher` 的代码（如果有）。

所以**建议在生产灰度前先在 staging 环境跑通**。

## 8. 相关文档

- [extending-from-other-plugins.md](./extending-from-other-plugins.md) — API 入门
- [extending-bot-adapter.md](./extending-bot-adapter.md) — 完整 Adapter 实现
- [testing-external-plugins.md](./testing-external-plugins.md) — 单测与集成测
- [public-api-changelog.md](./public-api-changelog.md) — 版本变更日志
