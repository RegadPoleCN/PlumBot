# PlumBot Platform / Bot Adapter 架构

本文档是 PlumBot 多平台、多 Bot 适配器架构的维护入口，用于说明模块边界、启动流程与事件流。代码迁移已完成，本文描述当前真实结构。

## 目标

- `common` 只保留平台无关的核心业务、抽象契约和通用工具。
- platform 模块负责把具体服务端 API 适配到 common 契约。
- bot adapter 模块负责接入外部 Bot 协议或插件 API。
- adapter 与 platform 的兼容性显式声明，避免在不支持的运行环境中被加载。
- 新增平台或 Bot 接入时，已有稳定的文档、矩阵和能力模型作为维护依据。

## 当前模块图

当前模块结构：

```text
:bukkit -----------------------------> :common
:bukkit -----------------------------> :adapter:onebot
:bukkit -----------------------------> :adapter:miraimc
:adapter:onebot ---------------------> :common
:adapter:miraimc --------------------> :common
```

说明：

- `:bukkit` 保留既有模块名，未重命名为 `:platform-bukkit`。
- `:adapter:onebot` 和 `:adapter:miraimc` 已完成迁移并接入 `:bukkit`；`:bukkit` 通过 Gradle `implementation(project(...))` 在构建时将二者打包进最终产物。
- `common` 不再包含 AOneBot、MiraiMC 或 Bukkit 等具体平台/协议实现；具体 adapter 依赖已下放到对应模块。

## 推荐模块命名

未来新增 adapter 建议放在 `adapter/<name>/` 目录下，对应的 Gradle 模块路径为 `:adapter:<name>`：

```text
:common
:platform-bukkit
:platform-velocity
:platform-bungee
:adapter:onebot
:adapter:miraimc-bukkit
:adapter:discord
:adapter:telegram
```

命名规则：

- 服务端平台模块使用 `platform-<name>`。
- Bot 接入模块使用 `:adapter:<name>`。
- 如果某个 adapter 强绑定某个平台，使用 `:adapter:<name>-<platform>`。

## common 职责

`common` 应包含：

- 配置读取与配置模型。
- 消息模板与国际化资源。
- 数据库接口及平台无关数据库实现。
- platform 抽象接口。
- bot 抽象接口、registry、factory、metadata 与 capability model。
- 白名单、消息转发、入退服通知、Bot 命令解析等平台无关业务服务。
- 与平台无关的通用工具。

`common` 不应长期包含：

- Bukkit、Velocity、Bungee 等具体服务端 API import。
- AOneBot、MiraiMC 等具体 Bot adapter import。
- 具体平台调度器实现。
- 具体 Bot 连接、事件监听和第三方协议处理。

## platform 模块职责

以 Bukkit 为例，platform 模块负责：

- 插件入口与生命周期。
- 平台事件监听与事件数据提取。
- 平台 logger、scheduler、player service、messenger 的实现。
- 平台依赖加载与可选插件检查。
- 注册当前平台可用的 Bot adapter factory。
- 将 common 返回的业务结果应用回平台事件。

平台 listener 应保持轻量，只做事件适配，不承载可复用业务逻辑。

## bot adapter 模块职责

每个 Bot adapter 模块负责：

- 连接第三方 Bot 协议或插件 API。
- 将第三方 Bot 事件转换为 common 可处理的 Bot 事件。
- 实现 Bot 消息发送、成员查询、图片发送等能力。
- 声明 metadata：adapter id、显示名、支持平台、能力、必需插件、配置路径。
- 提供 factory，由 platform 启动时按兼容性注册。

adapter 不应直接依赖不支持的平台 API；如果必须依赖平台 API，应在模块名和兼容矩阵中明确标注。

## 启动流程

启动流程：

1. platform 模块启动并创建 `PlatformContext`。
2. platform 检查自身能力和可选插件状态。
3. platform 注册与当前平台兼容的 Bot adapter factory。
4. common 根据配置读取 `bot.type`。
5. Bot registry 根据 adapter metadata 校验平台兼容性、必需插件和能力。
6. registry 创建 Bot 实例并进入连接或监听流程。
7. common 启动数据库、业务服务和事件分发。

## 事件流

### 平台事件到 Bot

```text
服务端事件 -> platform listener -> common service -> bot abstraction -> adapter -> 外部 Bot
```

示例：Bukkit 玩家聊天事件由 Bukkit listener 提取消息与玩家信息，交给 common 的聊天转发服务，最后通过当前 Bot adapter 发送到群。

### Bot 事件到平台

```text
外部 Bot -> adapter listener -> common bot event/handler -> common service -> platform abstraction -> 服务端
```

示例：群消息命令由 adapter 转成 common Bot 事件，common 命令服务处理后，通过 platform 抽象查询玩家列表或广播服务器消息。

## 兼容性维护

adapter 与 platform 的支持关系维护在 `coding-docs/architecture/adapter-platform-compatibility.md`。新增或迁移 adapter 时必须同步更新：

- 支持的平台。
- 必需插件。
- 关键限制。
- 是否可在 Standalone 环境运行。

## 能力维护

Bot 能力说明维护在 `coding-docs/architecture/bot-capabilities.md`。新增 adapter 时只声明实际支持的能力，不应为了满足旧接口而假装支持。

## 新增平台

新增平台时参考 `coding-docs/development/adding-platform.md`，重点是实现 platform 抽象，并把平台事件转换为 common service 调用。

## 新增 Bot Adapter

新增 Bot adapter 时参考 `coding-docs/development/adding-bot-adapter.md`，重点是实现 factory、metadata 和支持的能力，并同步兼容矩阵。
