# 消息模板说明

PlumBot 使用 `messages.yml` 集中管理所有对外发送的文本消息。消息通过反射加载到 `Messages` 对象，业务代码中使用占位符替换生成最终文本。

## 核心类

| 类 | 位置 | 说明 |
|---|---|---|
| `Messages` | `common/.../api/config/Messages.kt` | 消息模板对象，所有字段有默认值 |
| `BotCommandService` | `common/.../bot/command/BotCommandService.kt` | 提供 `sendBindTemplate` 等发送工具 |

## `Messages.kt` 结构

```kotlin
object Messages : Cloneable {
    var prefix: String = "<missing:prefix>"
    var load: String = "<missing:load>"
    var unload: String = "<missing:unload>"
    var internalError: String = "<missing:internalError>"
    // ...
    var help: List<String> = emptyList()
}
```

所有字段都有默认值，避免 `messages.yml` 缺失字段时崩溃。

## 加载流程

`PlumBot.loadConfig()` 中：

1. 创建 `messages.yml` 配置对象。
2. 通过反射遍历 `Messages` 的所有可变属性。
3. 根据属性类型（`String` 或 `List`）调用 `getString` 或 `getStringList` 读取。
4. 若 `messages.yml` 加载失败，保留 `Messages` 中的默认值。

## 常用占位符

| 占位符 | 说明 |
|---|---|
| `%player_name%` | 玩家名 |
| `%user_id%` | QQ 用户 ID |
| `%user_name%` | QQ 用户昵称 |
| `%user_nick%` | QQ 群名片 |
| `%target_player%` | 目标玩家名 |
| `%num%` | 当前绑定数量 |
| `%current%` | 当前绑定列表 |
| `%whitelist_limit%` | 最大绑定数 |
| `%player%` | 通用玩家占位符 |

新增占位符时，务必在代码中用 `sendBindTemplate` 正确替换。

## 发送消息

### 纯文本消息

```kotlin
service.sendBindMessage(groupId, Messages.playerAddBind)
```

### 带占位符的消息

```kotlin
service.sendBindTemplate(
    groupId,
    Messages.playerAddBind,
    *service.userReplacements(groupId, userId),
    "%target_player%" to playerName,
    "%num%" to wl.size.toString(),
    "%current%" to wl.keys.toString()
)
```

`sendBindTemplate` 会将 `Messages` 中的字段按占位符逐个替换。

## 新增消息字段

1. 在 `Messages.kt` 中新增字段并设置默认值：
   ```kotlin
   var myNewMessage: String = "<missing:myNewMessage>"
   ```
2. 在 `common/src/main/resources/messages.yml` 中新增对应条目：
   ```yaml
   myNewMessage: "执行结果：%result%"
   ```
3. 在业务代码中使用 `sendBindTemplate` 替换占位符。

## 注意事项

- 消息字段名必须与 `messages.yml` 中的键名一致（反射加载依赖字段名）。
- 删除或重命名字段会破坏用户现有配置，应尽量避免。
- 图片模式（`pic: true`）下，消息会通过 `TextToImg.toFile()` 渲染为图片发送。
- `help` 字段是 `List<String>`，用于多行帮助消息。
