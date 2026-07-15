# PlumBot 开发文档

本文档集合面向 PlumBot 维护者、贡献者，以及**附属（third-party）插件**作者。涵盖架构设计、开发指南、迁移参考、测试策略、调试与日常问题排查。
如需快速了解项目全貌，可先阅读 `docs/README.md`。

> 文档维护规则在文末。建议**先读「公开 API」一节**，避免无意中触碰 `internal/*` 或破坏契约等级为 `@StableApi` 的方法签名。

## 架构设计

| 文档 | 说明 |
|---|---|
| [平台 / Bot Adapter 架构](./architecture/platform-adapter-architecture.md) | 模块边界、启动流程、事件流、各层职责、第三方插件嵌入视图 |
| [Adapter / Platform 兼容矩阵](./architecture/adapter-platform-compatibility.md) | Bot adapter 与运行平台的兼容关系维护表 |
| [Bot 能力模型](./architecture/bot-capabilities.md) | Capability 设计目标、能力列表与使用原则 |

## 开发指南（维护者视角）

| 文档 | 说明 |
|---|---|
| [新增 Platform](./development/adding-platform.md) | 如何新增服务端平台（Velocity、Bungee、Standalone 等） |
| [新增 Bot Adapter](./development/adding-bot-adapter.md) | 如何新增 Bot 接入模块（含 §13 第三方注册） |
| [新增功能](./development/adding-feature.md) | 如何在 common 中新增一个玩家可见功能 |
| [新增命令](./development/adding-command.md) | 如何新增一个 Bot 群命令 |
| [配置系统](./development/configuration.md) | Configurate 封装、`config.yml`/`datasource.yml` 结构、读取方式 |
| [消息模板](./development/messages.md) | `messages.yml` 结构、占位符规则、新增消息字段流程 |
| [数据库](./development/database.md) | 绑定表结构、`AbstractBindingDatabase`、新增表/字段流程 |
| [构建与发布](./development/build-and-release.md) | Gradle 构建命令、shadowJar、版本管理、产物说明 |
| [调试与问题排查](./development/debugging.md) | 日志、DebugProvider、常见问题与排查思路 |

## 附属插件（Third-party Plugin）开发文档

> **建议从这里开始**——这一节是给"想基于 PlumBot 写插件"的开发者准备的，比 §1、§2 更短、更聚焦。

| 文档 | 说明 |
|---|---|
| [附属插件调用公开 API](./development/extending-from-other-plugins.md) | 取 API / 发消息 / 监听事件 / 注册 Adapter / 进阶模式（多 Bot、Java、Capability 守卫） |
| [自定义 BotAdapter 完整教程](./development/extending-bot-adapter.md) | end-to-end 模板：factory + adapter + cache + 热重载 + 调试 |
| [PlumBotAPI v2 迁移指南](./development/migrating-plumbotapi-v2.md) | `class` → `object` 破坏性变更；行为差异、陷阱、4 步迁移、验证清单 |
| [附属插件测试策略](./development/testing-external-plugins.md) | JUnit5 + MockK / Mockito / MockBukkit 三套栈；mock `PlumBotAPI`、mock `BotExtensionRegistry` |
| [公开 API 版本变更日志](./development/public-api-changelog.md) | 2.0.0 起的所有外部可见增减 / 破坏性变更 |

## 公开 API（Third-party Surface）

> 修改本表所列类型时，**必须**同步修改相关架构文档和所有平台的实现。本表与 `common/.../api/PublicApi.kt` 中的 `@PublicApi` / `@StableApi` / `@ExperimentalApi` 注解保持一致（由 Gradle `checkApiContract` 任务校验）。

| 契约等级 | 类型 |
|---|---|
| `@StableApi` | `PlumBotAPI` (singleton)，`PlumBot`，`IBot`，`BotImpl`，`AbstractBotAdapter`，`BotFactory`，`PlatformContext`，`PlatformLogger`，`PlatformScheduler`，`PlatformMessenger`，`PlatformPlayerService`，`PlatformTaskHandle`，`TaskProvider`，`BotProvider` |
| `@PublicApi` | `PublicApi`，`BotAdapterMetadata`，`BotCapability`，`BotRegistry`，`BotExtensionRegistry`，`TaskProviderImpl`，`Plugin`，`ListenerHandle`，`GroupMessageEvent`，`UserDecreaseEvent`，`Messages`（只读契约，KDoc 标注请勿修改），`PlatformType`，`BukkitPlugin` (Bukkit)，`OneBotFactory`，`MiraiMCFactory` |
| `@ExperimentalApi` | —（尚无；新增 `@ExperimentalApi` 类型时务必记录在此表） |
| 内部（KDoc 标注，无标记） | `BotEventDispatcher`（仍 `public object` 因跨模块引用，公开入口走 `PlumBotAPI.subscribeXxx`） |
| `@PublicApi`/`@StableApi` 禁止 | `common/.../internal/*` 全部类型 |

附属插件作者请直接阅读 [extending-from-other-plugins.md](./development/extending-from-other-plugins.md)。

### 推荐阅读顺序

1. **新接入插件**：[extending-from-other-plugins.md](./development/extending-from-other-plugins.md) — 一份 5 分钟的入门。
2. **写自定义 BotAdapter**：[extending-bot-adapter.md](./development/extending-bot-adapter.md) — 端到端模板。
3. **从 v1 升级**：[migrating-plumbotapi-v2.md](./development/migrating-plumbotapi-v2.md) — 避免破坏性变更踩坑。
4. **写单元测试**：[testing-external-plugins.md](./testing-external-plugins.md) — mock 与集成测。
5. **查契约与变更**：[public-api-changelog.md](./development/public-api-changelog.md) + 上面「公开 API」表。

## 文档维护规则

1. 新增架构变更时，优先更新 `architecture/` 下的文档。
2. 新增开发流程时，优先在 `development/` 下补充文档。
3. 修改公开 API（如 `PlumBotAPI`、`PlumBot`、`IBot`、`BotFactory`、`BotAdapterMetadata`、`BotCapability`、`PlatformContext`、`BotExtensionRegistry` 等）时，必须同步更新相关架构文档、公开 API 列表（本文档）和所有平台的实现；并在 [public-api-changelog.md](./development/public-api-changelog.md) 中按 SemVer 记录变更。
4. 修改兼容矩阵或能力列表时，同步检查代码中的 metadata 声明是否一致。
5. 保持中英文术语一致：adapter、platform、capability、handler、listener 等保留英文原词，避免混用。
6. 任何新增 `development/*.md` 文档需在本文档「开发指南」或「附属插件开发文档」对应小节登记索引。
