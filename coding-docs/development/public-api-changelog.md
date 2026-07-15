# 公开 API 版本变更日志

> **本文件**：记录 `common/.../api/*` 与 `common/.../bot/*` / `common/.../platform/*` / `common/.../task/*` 中**公开** API 的对外可见变化。内部调整（重构、性能、迁移、`internal/*`）不在此列。
>
> 版本号对齐 `bukkit/src/main/resources/plugin.yml` 的 `version:` 字段，遵循 [Semantic Versioning](https://semver.org/)：
>
> - **MAJOR** —— 破坏性变更（本文档重点跟踪）
> - **MINOR** —— 向后兼容的新增（首次在 `@ExperimentalApi` 加注）
> - **PATCH** —— 内部修复

---

## 2.x（当前）

### 2.0.0 — 公开 API 扩展（与 `.trae/specs/public-api-extension` 对齐）

#### ✨ 新增

* 公开类型（`common`）：
  * `me.regadpole.plumbot.api.PublicApi`
  * `me.regadpole.plumbot.api.StableApi`
  * `me.regadpole.plumbot.api.ExperimentalApi`
  * `me.regadpole.plumbot.api.Plugin`
  * `me.regadpole.plumbot.api.ListenerHandle`
  * `me.regadpole.plumbot.api.event.GroupMessageEvent`
  * `me.regadpole.plumbot.api.event.UserDecreaseEvent`
  * `me.regadpole.plumbot.bot.BotExtensionRegistry`
* `IBot` / `BotImpl` / `AbstractBotAdapter` / `BotFactory` / `BotRegistry` / `BotProvider` / `BotCapability` / `BotAdapterMetadata` / `PlumBot` / `TaskProvider` / `TaskProviderImpl` / `PlumBotAPI` / `PlatformContext` / `PlatformLogger` / `PlatformScheduler` / `PlatformMessenger` / `PlatformPlayerService` / `PlatformTaskHandle` / `PlatformType` / `Messages` 上加了 `@PublicApi` / `@StableApi` 标记。
* `PlumBotAPI` 新增方法（11 个，对 `class` → `object` 形态生效）：
  * `attach(plugin: PlumBot)` / `detach()`
  * `getInstance(): PlumBotAPI`
  * `getAttachedPlugin(): PlumBot?`
  * `getBot(): IBot` / `getBotOrNull(): IBot?`
  * `sendGroupMessage(groupId, message): Boolean`
  * `sendUserMessage(userId, message): Boolean`
  * `sendGroupMessageWithImage(groupId, message): Boolean`
  * `sendUserMessageWithImage(userId, message): Boolean`
  * `subscribeGroupMessages(handler): ListenerHandle`
  * `subscribeUserDecrease(handler): ListenerHandle`
  * `shutdown()`
  * `getExtensionRegistry(): BotExtensionRegistry`
* `BotProvider` 新增 `unregisterFactory(type): Boolean`（用于按 type 精确清理）。
* `BotEventDispatcher` 新增内部 listener 注册入口 `registerGroupMessageHandler` / `registerUserDecreaseHandler` / `clearAllListeners`。
* Bukkit 模块：
  * 新增 `me.regadpole.plumbot.bukkit.platform.BukkitPlugin`（实现 `Plugin`，用于第三方 Adapter 注册时的插件句柄）。
  * 新增 `me.regadpole.plumbot.bukkit.listener.PluginListener`（自动清理 external factory）。
  * `plugin.yml` 不再使用 `provides:`。第三方插件通过 `server.servicesManager.getRegistration(PlumBotAPI::class.java)` 取 API，或在自己 plugin.yml 写 `depend: [PlumBot]` 硬绑插件本体。
* Gradle：`common:checkApiContract` 任务（自动校验契约注释）。
* 新增 `common/src/main/kotlin/me/regadpole/plumbot/api/event/BotEvents.kt`。

#### ⚠️ BREAKING（影响 1 个调用点）

* **`PlumBotAPI` 由 `class` 改为 `object`**。
  * 唯一直接构造点：`bukkit/.../PlumBotBukkit.onEnable` 的 `PlumBotAPI(this)` → `PlumBotAPI.attach(this)`。
  * 第三方插件：从未建议构造 `PlumBotAPI` 实例；如果违反，请用 `servicesManager.getRegistration(PlumBotAPI::class.java)?.provider` 或 `PlumBotAPI.getInstance()`。
  * 迁移文档：[migrating-plumbotapi-v2.md](./migrating-plumbotapi-v2.md)。

#### 🔧 行为变更（兼容）

* `BotProvider.unloadBot()` 调用结束会增加 `BotEventDispatcher.clearAllListeners()`，已注册 listener 自动失效。
* Bukkit `ServicesManager` 注册 key 改为 `::class.java`（之前用 `object.javaClass`）。第三方取 `BotProvider` / `DatabaseProvider` 服务仍兼容。

#### ⛔ 移除

* 无。

#### 📦 文档

* 新增：
  * [extending-from-other-plugins.md](./extending-from-other-plugins.md)
  * [extending-bot-adapter.md](./extending-bot-adapter.md)
  * [migrating-plumbotapi-v2.md](./migrating-plumbotapi-v2.md)
  * [testing-external-plugins.md](./testing-external-plugins.md)
  * [public-api-changelog.md](./public-api-changelog.md)（本文档）
* 更新：
  * [adding-bot-adapter.md](./adding-bot-adapter.md) — 新增 §13
  * [platform-adapter-architecture.md](../architecture/platform-adapter-architecture.md) — "第三方插件嵌入视图"
  * [README.md](../README.md) — 索引 + 公开 API 表面表

#### 🧪 测试

* 14 个 `common` 单测（`PlumBotAPITest` 5 + `BotExtensionRegistryTest` 4 + `BotEventDispatcherTest` 5）。
* `gradle :common:test` 必须通过。
* `gradle :common:checkApiContract` 必须通过（25 公开类型契约一致）。

---

## 1.x（历史）

### 1.0.x — `:bukkit` 模块未公开 `PlumBotAPI`

* 仅暴露 4 个 getter 的 `class PlumBotAPI(private val plugin: PlumBot)`，通过 Bukkit `ServicesManager` 间接暴露。
* 公开范围有限：第三方插件需要反射访问 `common/internal/*` 来订阅事件或注册 Adapter。
* **不在本文档范围**（未达到本文档定义的"公开 API"治理粒度）。如果你的下游代码基于 1.0.x，请先按 [migrating-plumbotapi-v2.md](./migrating-plumbotapi-v2.md) 升级。

---

## 附：版本检测建议

你可以在第三方插件的 `onEnable` 顶部，校验 PlumBot 的"能力看板"，避免硬依赖：

```kotlin
val cap = runCatching {
    val api = me.regadpole.plumbot.PlumBotAPI.getInstance()
    api.getExtensionRegistry()        // 2.0+
    api.subscribeGroupMessages { it } // 2.0+
}.isSuccess

if (!cap) {
    logger.warning("[YourPlugin] Requires PlumBot 2.0+ for group-message subscription.")
    return
}
```

或者基于 `plugin.yml` 的 `name`/version：

```kotlin
val plumBot = server.pluginManager.getPlugin("PlumBot") as? org.bukkit.plugin.java.JavaPlugin
val isPlumBotV2 = plumBot?.description?.version?.startsWith("2.") == true
```

## 附：PR 提交到 PlumBot 时如何更新本日志

1. 修改了 `common/.../api/*` 或核心公开类型（参见 README 的契约等级表）→ 必更新本文件。
2. 新增 `@ExperimentalApi` 类型 → 必须在 `MINOR` 段说明。
3. 删除 / 重命名公开方法 → 必须在 `BREAKING` 段说明，并给出迁移指引。
4. 文档同步规则参见 [README.md §文档维护规则](../README.md)。
