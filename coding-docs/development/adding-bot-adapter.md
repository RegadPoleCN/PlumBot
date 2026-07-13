# 新增 Bot Adapter 指南

本文档用于指导维护者为 PlumBot 新增 Bot 接入模块，例如 Discord、Telegram、更多 OneBot 实现或平台插件型 Bot。

## 适用范围

Bot adapter 模块负责接入外部 Bot 协议或插件 API，并将外部事件转换为 common 可处理的事件。adapter 不应承载白名单、玩家列表、消息模板等通用业务。

## 命名建议

通用 adapter：

```text
:adapter:<name>
```

平台绑定 adapter：

```text
:adapter:<name>-<platform>
```

示例：

```text
:adapter:onebot
:adapter:discord
:adapter:miraimc-bukkit
```

当前仓库 Step 1 建立的模块为：

- `:adapter:onebot`
- `:adapter:miraimc`

## 新增步骤

1. 在 `settings.gradle.kts` 中 include 新模块：

   ```kotlin
   include(":adapter:<name>")
   ```

2. 新建 `adapter/<name>/build.gradle.kts`。
3. 应用现有 Kotlin JVM convention plugin 和 Kotlin serialization plugin。
4. 依赖 `project(":common")`。
5. 添加协议 SDK、插件 API 或网络库依赖。
6. 实现 Bot factory。
7. 声明 Bot adapter metadata。
8. 只声明真实支持的 capabilities。
9. 将外部 Bot 事件转换为 common Bot 事件或 dispatcher 调用。
10. 在支持的 platform 模块中注册 factory。例如在 `:bukkit` 的 `build.gradle.kts` 中添加：

    ```kotlin
    implementation(project(":adapter:<name>"))
    ```
11. 更新兼容矩阵和能力文档。
12. 编译验证 adapter 模块和受影响 platform 模块。

## Metadata 要求

每个 adapter 应声明：

- 稳定 id。
- 显示名称。
- 支持的平台集合。
- 支持的能力集合。
- 必需插件集合。
- 配置路径或配置节名称。

metadata 是启动时兼容性校验的依据，必须与文档保持一致。

## Capability 要求

adapter 只声明实际支持的能力。例如：

- 能发送群消息时声明 `GROUP_MESSAGE_SEND`。
- 能接收群消息事件时声明 `GROUP_MESSAGE_RECEIVE`。
- 不能发送图片时不要声明 `IMAGE_SEND`。
- 能发送私聊消息时声明 `USER_MESSAGE_SEND`。
- 能查询群成员信息时声明 `GROUP_MEMBER_QUERY`。
- 能检查群成员是否在群时声明 `GROUP_MEMBER_CHECK`。
- 能接收群成员减少事件时声明 `GROUP_MEMBER_DECREASE_RECEIVE`。

如果某能力依赖额外配置或外部插件版本，应在 adapter 注释、文档或 metadata 备注中说明。

## 平台依赖规则

- 通用 adapter 不应 import Bukkit、Velocity、Bungee 等平台 API。
- 平台插件型 adapter 必须显式声明支持平台和 required plugin。
- 不支持的平台不能提前加载平台专属类。
- 如果 adapter 只能在 Bukkit 运行，优先考虑命名为 `:adapter:<name>-bukkit`；如短期未改名，必须在兼容矩阵中明确 Bukkit-only。

## 事件处理原则

adapter 负责协议转换，不负责业务决策：

```text
外部 Bot 事件 -> adapter listener -> common bot event/handler -> common service
```

常见转换内容：

- 群号、用户号、消息内容。
- 群成员信息。
- 退群、撤回、私聊等事件类型。
- 图片、文本或富文本消息格式。

## 代码模板

common 模块提供了 `BotAdapterTemplate`（`common/src/main/kotlin/me/regadpole/plumbot/bot/BotAdapterTemplate.kt`），内含新增 adapter 时常用的字符串模板：

- `FACTORY_TEMPLATE`：`BotFactory` 与 `BotAdapterMetadata` 声明示例，包括 `supportedPlatforms`、`requiredPlugins` 与 `capabilities`。
- `ADAPTER_TEMPLATE`：继承 `AbstractBotAdapter` 的实现骨架，覆盖连接建立、资源清理、成员查询、消息发送等方法。
- `LISTENER_TEMPLATE`：将外部事件转换为 common `BotHandler` 调用的 listener 示例。
- `NOTES`：新增 adapter 的注意事项，例如必须正确声明平台支持、依赖插件与能力；阻塞查询应使用 `AbstractBotAdapter.awaitWithTimeout` 保持统一超时；图片发送可复用 common 模块的 `TextToImg` 等工具。

`BotAdapterTemplate` 仅作为文档/模板存在，内部字符串不需要编译通过，也不应引入任何具体协议 SDK。建议复制模板到 `:adapter:<name>` 模块中，根据实际协议填写实现。

## 验证清单

- [ ] 模块已加入 `settings.gradle.kts`。
- [ ] 模块依赖 `project(":common")`。
- [ ] 已声明 metadata。
- [ ] 支持平台是显式且保守的。
- [ ] required plugin 是显式的。
- [ ] capabilities 只包含真实支持项。
- [ ] 不包含不必要的平台 API import。
- [ ] 外部事件已转换为 common 事件入口。
- [ ] 已更新兼容矩阵。
- [ ] 已更新能力文档。
- [ ] 已完成 Gradle 编译验证。
