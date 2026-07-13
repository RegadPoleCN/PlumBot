# PlumBot 开发文档

本文档集合面向 PlumBot 维护者与贡献者，涵盖架构设计、开发指南和日常问题排查。如需快速了解项目全貌，可先阅读 `docs/README.md`。

## 架构设计

| 文档 | 说明 |
|---|---|
| [平台 / Bot Adapter 架构](./architecture/platform-adapter-architecture.md) | 模块边界、启动流程、事件流、各层职责 |
| [Adapter / Platform 兼容矩阵](./architecture/adapter-platform-compatibility.md) | Bot adapter 与运行平台的兼容关系维护表 |
| [Bot 能力模型](./architecture/bot-capabilities.md) | Capability 设计目标、能力列表与使用原则 |

## 开发指南

| 文档 | 说明 |
|---|---|
| [新增 Platform](./development/adding-platform.md) | 如何新增服务端平台（Velocity、Bungee、Standalone 等） |
| [新增 Bot Adapter](./development/adding-bot-adapter.md) | 如何新增 Bot 接入模块 |
| [新增功能](./development/adding-feature.md) | 如何在 common 中新增一个玩家可见功能 |
| [新增命令](./development/adding-command.md) | 如何新增一个 Bot 群命令 |
| [配置系统](./development/configuration.md) | Configurate 封装、`config.yml`/`datasource.yml` 结构、读取方式 |
| [消息模板](./development/messages.md) | `messages.yml` 结构、占位符规则、新增消息字段流程 |
| [数据库](./development/database.md) | 绑定表结构、`AbstractBindingDatabase`、新增表/字段流程 |
| [构建与发布](./development/build-and-release.md) | Gradle 构建命令、shadowJar、版本管理、产物说明 |
| [调试与问题排查](./development/debugging.md) | 日志、DebugProvider、常见问题与排查思路 |

## 文档维护规则

1. 新增架构变更时，优先更新 `architecture/` 下的文档。
2. 新增开发流程时，优先在 `development/` 下补充文档。
3. 修改公开 API（如 `PlatformContext`、`IBot`、`BotFactory`、`BotAdapterMetadata`、`BotCapability`）时，必须同步更新相关架构文档和所有平台的实现。
4. 修改兼容矩阵或能力列表时，同步检查代码中的 metadata 声明是否一致。
5. 保持中英文术语一致：adapter、platform、capability、handler、listener 等保留英文原词，避免混用。
