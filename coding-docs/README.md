# PlumBot 开发与架构文档

本文档集合面向 PlumBot 核心维护者、代码贡献者以及**第三方附属插件（Third-party Plugin）**开发者。旨在以最简洁、严谨、可靠的形式阐述系统架构与开发指南。

---

## 📚 文档导航

整个开发文档体系由 4 篇正交文档构成，按需查阅：

| 文档 | 面向对象 | 核心内容 |
|---|---|---|
| **[本文 (README.md)](#)** | 全员 | 架构分层拓扑、模块职责、公开 API 契约分级、Bot 能力与兼容矩阵 |
| **[附属插件开发手册](./extension-guide.md)** | 外部开发者 | 接入 `:api`、获取 `PlumBotAPI`、收发消息、事件监听、扩展 BotAdapter、轻量单元测试 Mock |
| **[内部核心开发手册](./internal-dev.md)** | 内部维护者 | 新增端到端功能/指令闭环、消息与 MiniMessage 安全转义、数据库持久层扩展、接入新服务端平台 |
| **[构建与发布规范](./build-and-release.md)** | 全员 / DevOps | Gradle `buildSrc` 双约定插件架构、统一产物输出 (`./gradlew dist`)、契约守护校验 |

---

## 🏗️ 整体架构拓扑与分层职责

PlumBot 采用标准的**分层门面 + 适配器架构（Layered Facade & Adapter Architecture）**，各模块具备严格的物理边界与单向依赖规则：

```
           [ 第三方附属插件 / External Plugins ]
                           │
                           │ 仅依赖公开契约
                           ▼
┌────────────────────────────────────────────────────────┐
│  :api (公开契约层，Zero Legacy Debt)                    │
│  - 根包路径: me.regadpole.plumbot.api.*                │
│  - 纯接口业务门面: PlumBotAPI                           │
│  - 领域模型: BotModels, PlatformModels, Binding        │
│  - 事件体系: BotEvents (GroupMessageEvent 等)           │
└──────────────────────────▲─────────────────────────────┘
                           │ 接口实现与依赖注入
┌──────────────────────────┴─────────────────────────────┐
│  :common (核心领域实现，纯 JVM 平台无关)                │
│  - 包路径: me.regadpole.plumbot.*                      │
│  - 文本安全与过滤: AhoCorasickMatcher (AC自动机脱敏)   │
│  - 协程缓存: BotCache, GroupMemberCache (Caffeine)     │
│  - 持久层: DatabaseProvider, AbstractBindingDatabase  │
│  - 指令与消息分发: BotCommandService, DefaultBotHandler│
└──────────────▲──────────────────────────▲──────────────┘
               │ 宿主接入                 │ 协议挂载
┌──────────────┴──────────────┐ ┌─────────┴──────────────┐
│  :bukkit (服务端平台实现)    │ │  :adapter:* (Bot 协议适配)│
│  - BukkitPlatformContext    │ │  - :adapter:onebot     │
│  - Libby 类库动态回退链加载  │ │    (WebSocket 协程全异步)│
│  - 服务发现与宿主生命周期管理│ │  - :adapter:miraimc    │
└─────────────────────────────┘ └────────────────────────┘
```

### 模块边界守则
1. **`:api` 模块纯粹度**：严禁引入任何具体服务实现或包含重度依赖的第三方库（仅允许 `adventure-api` 与 `kotlinx-datetime` 等基础契约库）。任何对外公开类型必须冠以 `me.regadpole.plumbot.api.*`。
2. **`:common` 平台无关性**：严禁出现任何特定游戏服务端（如 Bukkit/Spigot/Paper）的类导入。所有平台能力均通过 `PlatformContext` 抽象注入。
3. **`:adapter:*` 隔离性**：每个适配器只负责将外部 IM 协议转化为 PlumBot 标准事件和消息模型，不可直接耦合具体游戏业务逻辑。

---

## 📜 公开 API 契约分级 (API Surface)

为保证第三方生态的稳定性，PlumBot 在 `:api` 模块通过自定义注解显式声明契约稳定性等级。Gradle 在构建时会自动执行 `:api:checkApiContract` 任务进行静态扫描。

| 契约注解 | 稳定性承诺 | 代表性接口 / 类型 |
|---|---|---|
| **`@StableApi`** | **长期稳定**。发版后不破坏二进制兼容，严禁无故修改签名或删除方法。 | `PlumBotAPI`<br>`IBot`<br>`BotFactory`<br>`PlatformContext`<br>`PlatformScheduler`<br>`PlatformPlayerService`<br>`AbstractBotAdapter` |
| **`@PublicApi`** | **公开可用**。主版本号内保证兼容，允许合理扩展新方法与新模型。 | `BotAdapterMetadata`<br>`BotCapability`<br>`MemberInfo`<br>`PlatformCapability`<br>`PlatformType`<br>`LogLevel`<br>`Binding`<br>`IDatabase`<br>`ListenerHandle`<br>`Plugin`<br>`GroupMessageEvent`<br>`GroupMemberDecreaseEvent` |
| **内部实现** | **严禁外部引用**。随时可能发生变动或重构，外部调用后果自负。 | 所有位于 `me.regadpole.plumbot.internal.*` 下的类，以及 `:common` 内部类。 |

---

## 🤖 Bot 能力模型与兼容矩阵

不同的 Bot 框架具备不同的协议特性（例如：某些协议无法拉取群成员名片，某些协议不支持发送图片）。为了防止业务层出现盲目调用导致的运行时崩溃，PlumBot 采用 **能力声明（BotCapability）守卫机制**。

### 1. 核心能力枚举 (`me.regadpole.plumbot.api.bot.BotCapability`)
- `SEND_GROUP_MSG`: 支持发送普通群文本消息。
- `SEND_IMAGE`: 支持在群内发送图片。
- `FETCH_GROUP_MEMBER_INFO`: 支持获取指定群成员的详细资料（昵称、名片、群权限）。
- `EXECUTE_COMMAND`: 支持执行群管理指令。

### 2. 官方适配器能力支持矩阵

| 适配器类型 (`type`) | 运行传输协议 | `SEND_GROUP_MSG` | `SEND_IMAGE` | `FETCH_GROUP_MEMBER_INFO` | 具备平台依赖 |
|---|---|:---:|:---:|:---:|---|
| **`onebot`** | 原生 WebSocket (反向/正向) | ✅ | ✅ | ✅ | 跨平台纯独立 |
| **`miraimc`** | MiraiMC 本地桥接服务 | ✅ | ✅ | ✅ | 仅限 Bukkit 宿主 |

> 业务层在调用特定功能（如根据群名片识别玩家）前，应先通过 `bot.metadata.capabilities` 校验是否声明了对应能力。
