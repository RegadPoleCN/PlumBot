# 新增命令指南

本文档说明如何在 PlumBot 中新增一个 Bot 群命令。当前命令处理由 `DefaultBotHandler` 通过 `CommandRegistration` 注册表统一分发，白名单相关命令已实现为独立的 `WhitelistCommand` 子类。

## 命令处理流程

```text
群消息 -> adapter listener -> DefaultBotHandler.onGroupMessage()
  -> CommandRegistration 注册表匹配
  -> 对应命令类 execute()
  -> 业务服务 / 数据库 / platform 抽象
```

## 新增命令步骤

### 1. 在 `config.yml` 中新增命令关键字配置

在 `common/src/main/resources/config.yml` 的 `keys` 节下新增命令关键字列表：

```yaml
keys:
  myCommand:
    - "mycommand"
    - "mycmd"
```

### 2. 在 `BotHandler` 接口中新增回调（可选）

如果命令需要被外部直接调用，可以在 `common/.../listener/BotHandler.kt` 中新增方法：

```kotlin
fun onMyCommand(message: String, groupId: Long, userId: Long)
```

对于纯内部命令，也可以跳过这一步，直接由 `DefaultBotHandler` 调用命令类。

### 3. 创建命令类

如果命令与白名单绑定相关，放在 `common/.../bot/command/whitelist/` 包下；否则放在 `common/.../bot/command/` 下。

命令类结构：

```kotlin
package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.config.Messages

class MyCommand(private val service: BotCommandService) {
    fun execute(message: String, groupId: Long, userId: Long) {
        try {
            // 参数解析
            // 权限检查
            // 业务逻辑
            service.sendBindTemplate(groupId, Messages.myCommandReply, ...)
        } catch (e: IllegalStateException) {
            service.sendBindMessage(groupId, Messages.internalError)
            service.context.logger.log(LogLevel.ERROR, e.stackTraceToString())
        } catch (e: SQLException) {
            service.sendBindMessage(groupId, Messages.internalError)
            service.context.logger.log(LogLevel.ERROR, e.stackTraceToString())
        } catch (e: Exception) {
            service.sendBindMessage(groupId, Messages.internalError)
            service.context.logger.log(LogLevel.ERROR, e.stackTraceToString())
        }
    }
}
```

如果命令涉及数据库操作，建议继承 `AbstractWhitelistCommand`（或新建公共基类）复用 `requireDatabase()` 和错误处理。

### 4. 在 `DefaultBotHandler` 中注册命令

在 `DefaultBotHandler` 的 `commands` 注册表中新增一项。注册顺序决定匹配优先级：

```kotlin
private val commands: List<CommandRegistration> by lazy {
    val keys = commandService.config.getConfigMaker("keys")
    listOf(
        // 现有命令...
        CommandRegistration(
            keys.getStringList("myCommand"),
            "myFeature",  // feature 名称，用于 isFeatureEnabled 校验
            { cmdPrefix, key -> """$cmdPrefix$key(.*)""" },
            { cmdPrefix, key, msg -> msg.replace("$cmdPrefix$key", "") }
        ) { msg, groupId, userId ->
            myCommand.execute(msg, groupId, userId)
        }
    )
}
```

说明：

- `keys`：命令触发关键字列表，从 `config.yml` 读取。
- `feature`：功能开关名称，用于 `commandService.isFeatureEnabled(feature)` 校验。
- `regex`：生成正则表达式，判断消息是否匹配该命令。
- `payload`：从消息中提取参数部分。
- `handler`：匹配成功后执行，接收提取的参数、群号和用户号。

### 5. 添加消息模板

1. 在 `Messages.kt` 中新增字段：
   ```kotlin
   var myCommandReply: String = "<missing:myCommandReply>"
   ```
2. 在 `common/src/main/resources/messages.yml` 中新增：
   ```yaml
   myCommandReply: "命令执行结果：%result%"
   ```
3. 在命令类中使用 `sendBindTemplate` 替换占位符。

### 6. 检查能力依赖

如果命令需要 Bot 提供特定能力，在 `execute` 开头校验：

```kotlin
service.bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
```

## 命令正则示例

| 命令类型 | regex lambda | payload lambda |
|---|---|---|
| 无参数 | `{ cmdPrefix, key -> """$cmdPrefix$key""" }` | `{ cmdPrefix, key, msg -> msg.replace("$cmdPrefix$key", "") }` |
| 一个参数 | `{ cmdPrefix, key -> """$cmdPrefix$key (.+)""" }` | `{ cmdPrefix, key, msg -> msg.replace("$cmdPrefix$key ", "") }` |
| 可选参数 | `{ cmdPrefix, key -> """$cmdPrefix$key(.*)""" }` | `{ cmdPrefix, key, msg -> msg.replace("$cmdPrefix$key", "") }` |

## 注意事项

- **命令匹配顺序影响行为**：例如 `addBind` 和 `deleteBind` 都带参数，顺序靠前者优先匹配。
- **前缀为空时的处理**：`DefaultBotHandler` 中 `prefix` 默认空字符串，regex 和 payload 应正确处理空前缀。
- **不要在命令类中直接 import 平台类**：所有平台操作通过 `BotCommandService` / `PlatformContext` 完成。
- **保持错误处理一致**：统一返回 `Messages.internalError` 并记录完整堆栈。

## 验证清单

- [ ] 已在 `config.yml` 的 `keys` 节添加命令关键字。
- [ ] 已创建命令类并处理异常。
- [ ] 已在 `DefaultBotHandler` 注册表中注册命令。
- [ ] 已添加 `Messages.kt` 字段和 `messages.yml` 条目。
- [ ] 已检查并声明所需的 Bot capability。
- [ ] 命令匹配顺序符合预期。
- [ ] 已通过 `common:compileKotlin` 编译验证。
