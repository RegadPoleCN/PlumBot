# PlumBot 新平台接入与适配技术规范 (Platform Implementation Guide)

本文档是为 PlumBot 实现新宿主平台（如 Velocity、BungeeCord、Fabric、Sponge、Folia、以及未来的 Hytale 等）的**权威架构蓝图与标准化开发手册**。

本文以已高度打磨成熟的 `:bukkit` 模块为黄金参考基准（Golden Reference），详细拆解平台模块的每一个必需组件、生命周期装配时序、边界守则与质量校验清单，**确保人工或 AI 在扩充新平台时产出的是工业级、100% 完整闭环的生产模块，绝不遗漏核心机制。**

---

## 🏗️ 一、 平台模块的定位与边界守则

在 PlumBot 的分层体系中，平台模块（如 `:bukkit`, `:velocity`）属于**最外层的宿主胶水装配器（Host Assembly Layer）**。

```
       ┌────────────────────────────────────────────────────────┐
       │             :api (公开跨平台业务契约与事件)             │
       └───────────────────────────▲────────────────────────────┘
                                   │
       ┌───────────────────────────┴────────────────────────────┐
       │             :common (核心业务逻辑与通用服务)             │
       └───────────────────────────▲────────────────────────────┘
                                   │ 依赖并装配
       ┌───────────────────────────┴────────────────────────────┐
       │           :[platform] (平台宿主实现模块，本规范核心)     │
       │  1. 构建规范 (Platform Distribution Convention)        │
       │  2. 动态依赖注入 (Libby Dependency Loader)             │
       │  3. 平台上下文契约实现 (PlatformContext & Scheduler)   │
       │  4. 服务端事件与群聊双向网桥 (ServerListener)          │
       │  5. 第三方扩展生态守护 (PluginListener 防内存泄漏)      │
       │  6. 原生控制台命令系统 (/plumbot [reload|status|bind]) │
       │  7. 远程控制台输出捕获器 (Capturing Command Sender)     │
       └────────────────────────────────────────────────────────┘
```

### 平台模块的三大不可逾越铁律：
1. **零通用逻辑驻留**：敏感词 AC 自动机、SQLite/MySQL 数据持久化、白名单指令逻辑、OneBot 协议解析全部留在 `:common`，平台模块**严禁重复实现任何业务逻辑**。
2. **严防类型污染反向渗透**：平台特有的类（如 Spigot 的 `JavaPlugin`/`Player`，Velocity 的 `ProxyServer`/`CommandSource`）严格局限在平台模块内部，**绝对不能渗透到 `:api` 或 `:common`**。
3. **闭环完整性**：一个合格的平台实现必须提供**完整的 7 大核心要素**（详见下文），缺一不可。

---

## 🧩 二、 平台模块的 7 大核心构件（以 Bukkit 为蓝图）

对照 `bukkit/src/main/kotlin/me/regadpole/plumbot/bukkit/`，任何新平台模块必须完整实现以下 7 个构件：

### 1. 构建配置与产物归约 (`build.gradle.kts`)
所有平台模块必须应用 `buildSrc` 中抽离的 `platform-distribution` 约定插件，享受全自动 Shadow 打包、统一依赖隔离重定向（Relocation）与根目录 `:dist` 产物自动归集。

```kotlin
plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
    // 若平台有编译期注解处理（如 Velocity 的 @Plugin），在此引入 kapt / annotationProcessor
}

dependencies {
    implementation(project(":common"))
    implementation(project(":adapter:onebot")) // 引入通用 Bot 适配器

    // 平台自身 API (compileOnly)
    compileOnly(libs.platformApi)
    
    // Libby 平台运行时依赖加载器
    implementation(libs.libbyPlatform)
}

tasks {
    shadowJar {
        archiveBaseName.set("PlumBot-[PlatformName]") // 产物统一为 PlumBot-Velocity.jar 等
        archiveVersion.set("")
    }
}
```

---

### 2. 动态类库加载器 (`[Platform]DependencyLoader`)
由于 SQLite JDBC 与 MySQL 驱动体积较大且版本各异，PlumBot 在开服时通过 Libby 动态下载。各平台必须提供对应的 DependencyLoader，并严格遵循**多源优先级回退链**：

```
[自定义仓库优先] ➔ [阿里云公共镜像(国内加速)] ➔ [MavenCentral核心兜底] ➔ [JitPack] ➔ [PaperMC生态源]
```
必须动态加载的基础库：
- `org.xerial:sqlite-jdbc` (`RuntimeLibraryVersions.SQLITE_JDBC`)
- `com.mysql:mysql-connector-j` (`RuntimeLibraryVersions.MYSQL_CONNECTOR`)

---

### 3. 平台上下文契约实现 (`PlatformContext` 系列)
平台模块的核心桥梁，集中实现 `me.regadpole.plumbot.api.platform.*` 定义的接口：

#### A. `[Platform]PlatformContext`
- **能力声明 (`supportedCapabilities`)**：严格按平台实际支持情况声明。
  - 单服实体服（Bukkit/Fabric/Sponge）：通常声明包括 `SERVER_TPS_METRICS`、`PLAYER_DEATH_BROADCAST`、`PLAYER_ADVANCEMENT_BROADCAST` 等。
  - 代理端网络服（Velocity/Bungee）：声明 `SERVER_SWITCH_BROADCAST`、`PRE_LOGIN_INTERCEPT`，**绝不可声明**代理端不具备的死亡与 TPS 指标。
- **日志输出 (`log`)**：将 `LogLevel` 映射到平台原生 Logger（SLF4J、Log4j 或 `java.util.logging.Logger`）。
- **消息广播 (`sendMessage(Component)`)**：
  - 原生支持 Adventure（Paper / Velocity）：直接调用 `server.sendMessage(message)`。
  - 非 Adventure 平台：通过 `message.toPlainText()` 或转为 Legacy 文本进行降级广播。

#### B. `[Platform]PlatformScheduler` (时间与线程驱动)
必须完整实现 `PlatformScheduler` 的 6 个接口（`runSync`, `runAsync`, `runLater`, `runLaterAsync`, `runRepeating`, `runRepeatingAsync`）：
- 强制使用 `kotlin.time.Duration` 作为入参；
- 所有返回值必须封装为幂等的 `PlatformTaskHandle { task.cancel(); true }`。

#### C. `[Platform]PlayerService`
- 提供 `listPlayers(): List<String>` 与 `listPlayerString(): String`；
- 提供 `kickPlayer(name: String)`（执行封禁或未绑定白名单踢出）。

---

### 4. 远程控制台双通道捕获器 (`Capturing[Platform]Sender`)
为了响应群内 `/cmd <命令>` 管理员运维指令，平台模块必须能捕获控制台输出。
- **底层复用**：直接包装 `:common` 的 `OutputCapturingBuffer(forwardTo)`；
- **双通道拦截**：同时拦截平台的字符串输出与 Adventure `Component` 输出；
- **真实透传**：拦截的同时必须将输出透传给平台的真实控制台，保证物理机运维屏幕同步可见。

---

### 5. 服务端事件与群聊双向网桥 (`[Platform]ServerListener`)
监听平台原生事件，并转化为 `:common` 的通用服务调用：

| 平台原生触发事件 | 调用的通用服务组件 | 关键行为与守卫规则 |
|---|---|---|
| **玩家聊天事件** | `GameEventBridge.onChat(...)` | 自动剥除 Minecraft 原版格式码，调用敏感词过滤器脱敏后群发。 |
| **玩家预登录事件** | `PlayerLoginService.check(name)` | 白名单前置拦截！未绑定者直接在握手期断开，并设置退回提示。 |
| **玩家进入服务器** | `GameEventBridge.onJoin(name)` | 异步线程广播进服消息。 |
| **玩家离开服务器** | `GameEventBridge.onLeave(name, server)` | 异步线程广播离服消息。 |
| **跨服换服 (代理端特有)**| `GameEventBridge.onChangeServer(...)` | 守卫 `SERVER_SWITCH_BROADCAST`，广播从 A 子服跳转到 B 子服。 |
| **玩家遇难 (实体服特有)**| `GameEventBridge.onDeath(...)` | 守卫 `PLAYER_DEATH_BROADCAST`，转发原版死亡文本。 |
| **玩家成就 (实体服特有)**| `GameEventBridge.onAdvancement(...)` | 守卫 `PLAYER_ADVANCEMENT_BROADCAST`，自动过滤掉合成配方解锁刷屏。 |

---

### 6. 第三方扩展生态守护器 (`[Platform]PluginListener`)
这是保障服务器稳定运行、**绝对不能缺失的核心机制**：
- 监听宿主平台的插件卸载事件（如 Bukkit 的 `PluginDisableEvent` 或代理端相应注销事件）；
- 当任何第三方附属插件被热重载或禁用时，**强制执行双重安全注销**：
  1. `PlumBotAPI.get().unregisterAllFor(pluginHandle)`：彻底切断其所有的群消息监听器回调；
  2. `api.extensionRegistry.unregisterAllFor(pluginHandle)`：注销该插件注册的所有外部自定义 Bot 适配器；
- **收益**：杜绝插件重载后旧 ClassLoader 堆积导致的 Metaspace 内存溢出。

---

### 7. 平台主入口与原生管理指令 (`PlumBot[Platform]` & `PlumBotCommand`)

#### A. 主类生命周期装配规范 (`onEnable` / `onDisable`)
在平台主类中，启动顺序必须严格遵循以下标准时序：

```
[1. loadDependencies()] ➔ [2. loadConfig()] ➔ [3. BotProvider.registerFactory(...)]
          │
          ▼
[4. 注册并公开 PlumBotAPI] ➔ [5. enable() (初始化数据库与Bot客户端)]
          │
          ▼
[6. 装配 FilterThesaurusManager 并异步预热词库] ➔ [7. 注册所有平台监听器与 /plumbot 指令]
```

在插件卸载 (`onDisable`) 时，必须执行资源释放三部曲：
1. `disable()`：关闭 Bot 网络连接、释放数据库连接池（HikariCP/SQLite）、关闭任务线程池；
2. 清空外部动态 Bot 工厂注册表；
3. `PlumBotApiProvider.unregister()` 销毁门面引用。

#### B. 原生管理指令系统 (`/plumbot`)
在平台控制台及游戏中注册 `/plumbot` 指令（权限节点 `plumbot.admin`），提供标准子指令：
- `/plumbot reload`：重载 `config.yml`、`messages.yml`、字体资源并触发敏感词词库异步热重载；
- `/plumbot status`：在控制台/聊天框输出当前 Bot 适配器名称、数据库状态与在线人数；
- `/plumbot bind <玩家名> <QQ>` / `/plumbot unbind`：游戏内管理员强制操作白名单。

---

## 📋 三、 新平台开发自检核对表（Checklist）

完成新平台模块编码后，必须逐项对照本清单确认，全部勾选方可提交：

- [ ] **构建体系**：是否已应用 `platform-distribution` 约定插件，并在根工程 `settings.gradle.kts` 中包含该模块？
- [ ] **产物归档**：运行 `./gradlew dist` 是否能在根目录 `dist/` 下正常生成 `PlumBot-[Platform].jar`？
- [ ] **API 纯洁度**：平台模块是否未向 `:api` 和 `:common` 泄露任何平台专有类型？
- [ ] **能力声明精准度**：`supportedCapabilities` 是否严格吻合平台实际能力（代理端不虚标实体服特性，实体服不虚标跨服特性）？
- [ ] **线程安全保证**：远程控制台指令派发是否已确保调度在服务端的安全执行线程？
- [ ] **防越权安全转义**：转发到服务端的聊天与玩家名称是否全部经由 `escapeMiniMessageTags` 过滤？
- [ ] **命令注册落地**：平台主类是否显式挂载了 `/plumbot` 指令执行器与补全器？
- [ ] **插件卸载清理**：是否实现了 `PluginListener`，在外部插件卸载时自动注销其注册的事件句柄与 BotFactory？
- [ ] **敏感词管理器挂载**：是否已实例化 `FilterThesaurusManager` 并将其挂载到 `FilterManagerHolder.manager`？
- [ ] **关服优雅回收**：关服时是否完整调用了 `disable()` 关闭数据库连接池与 Bot 网络客户端？
