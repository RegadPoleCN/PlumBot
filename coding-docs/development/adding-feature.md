# 新增功能指南

本文档说明如何在 PlumBot 中新增一个玩家可见功能。这里的"功能"指由配置开关控制、需要同时影响游戏端和 Bot 端的业务，例如：在线玩家查询、入退服广播、游戏聊天转发等。

## 新增功能的一般流程

### 1. 判断功能归属

| 场景 | 归属 |
|---|---|
| 功能与平台无关，可复用于所有 platform | `:common` |
| 功能依赖具体平台 API（如 Bukkit 事件、Velocity 命令） | 对应 platform 模块 |
| 功能依赖具体 Bot 协议 | 对应 adapter 模块 |

大多数玩家可见功能应放在 `:common`，通过 platform 抽象和 adapter 抽象与两端交互。

### 2. 设计配置开关

在 `common/src/main/resources/config.yml` 中新增配置节。建议结构：

```yaml
feature:
  <featureName>:
    enable: true
    # 其他参数...
```

示例（参考现有功能）：

```yaml
feature:
  join:
    enable: true
    pic: false
  message:
    enable: true
    mode: 0
    prefix: ""
```

配置读取使用 `context.config.getBoolean("feature", "<featureName>", "enable")` 等方式。

### 3. 实现 common 业务服务

在 `common/src/main/kotlin/me/regadpole/plumbot` 下选择合适包：

- 与玩家登录/加入/离开相关：`server/`
- 与 Bot 命令相关：`bot/command/`
- 与消息转发相关：`bot/command/` 或 `server/`

服务类一般接收 `PlatformContext` 和 `IBot` 或 `BotCommandService`，通过 platform 抽象读取数据、通过 bot 抽象发送消息。

示例结构：

```kotlin
class MyFeatureService(private val service: BotCommandService) {
    fun onEvent(...) {
        if (!service.config.getBoolean("feature", "myFeature", "enable")) return
        // 业务逻辑...
        service.sendBindTemplate(groupId, Messages.myFeatureMessage, ...)
    }
}
```

### 4. 在 platform listener 中接入

platform listener 只做事件适配，不实现业务。例如 Bukkit 中：

```kotlin
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    myFeatureService.onEvent(event.player.name, ...)
}
```

### 5. 添加消息模板

如果功能需要向 QQ 群发送消息：

1. 在 `Messages.kt` 中新增字段并设置默认值。
2. 在 `common/src/main/resources/messages.yml` 中新增对应条目。
3. 在业务服务中使用 `sendBindTemplate` 替换占位符。

详见 [消息模板](./messages.md)。

### 6. 检查能力依赖

如果功能需要 Bot 提供特定能力（如发送图片、查询群成员），在业务入口使用 `requireCapability` 校验：

```kotlin
service.bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
```

### 7. 更新文档

- 在本文档所属目录下补充功能说明（如功能涉及新配置，更新 [配置系统](./configuration.md)）。
- 如果功能新增或变更了 capability 使用，更新 `architecture/bot-capabilities.md` 的使用场景。

## 注意事项

- **不要在 listener 中写业务逻辑**：listener 只负责提取事件数据和调用 service。
- **不要在 common 中 import 平台类**：所有平台相关操作通过 `PlatformContext` 抽象完成。
- **保持配置默认值不破坏现有行为**：新增配置默认关闭或保持与旧行为一致。
- **消息占位符保持兼容**：新增占位符可以，删除或改名需要评估影响。

## 验证清单

- [ ] 功能归属判断正确（common / platform / adapter）。
- [ ] 已在 `config.yml` 中添加配置节。
- [ ] 已在 `messages.yml` 和 `Messages.kt` 中添加消息模板。
- [ ] 业务逻辑集中在 common service，platform listener 只做薄适配。
- [ ] 已检查并声明所需的 Bot capability。
- [ ] 已通过 `common:compileKotlin` 和相关 platform 模块编译验证。
