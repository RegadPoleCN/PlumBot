# PlumBot 新平台接入与适配规范

本文写给要给 PlumBot 加一个新宿主平台的人：新游戏服务端（Fabric、Sponge、Folia 等）或新的代理端实现。仓库里已有 `:bukkit`、`:velocity`、`:hytale` 三份完整实现，以 `:bukkit` 为基准读，它的文件最全。

平台模块是宿主胶水装配层：`:[platform]` 依赖 `:common`（`:common` 以 `api(project(":api"))` 对外暴露契约），平台代码不写业务逻辑，只把宿主的能力和事件接到 `:common` 的服务上。

## 一、接入清单

动手写代码前，先把模块挂进构建。以下每一项都以仓库现有文件为准。

1. **注册模块**：在 `settings.gradle.kts`（现有 `include(":api")`、`":render"`、`":common"`、`":bukkit"`、`":velocity"`、`":hytale"`、`":adapter:onebot"`、`":adapter:miraimc"`）里加一行 `include(":myplatform")`，目录 `myplatform/` 下放 `build.gradle.kts` 与源码。
2. **选约定插件**：`buildSrc` 只有两个约定插件。`buildsrc.convention.kotlin-jvm`（`jvmToolchain(25)`、可选生成 `BuildConstants`）给库模块用；平台模块用 `buildsrc.convention.platform-distribution`，它在 `kotlin-jvm` 之上加 Shadow 打包、统一重定位（`me.regadpole.plumbot.lib`）、`assemble` 依赖 `shadowJar`。
3. **`build.gradle.kts` 骨架**：

```kotlin
plugins {
    id("buildsrc.convention.platform-distribution")
    alias(libs.plugins.kotlinPluginSerialization)
    // 宿主有编译期注解处理时才加，例如 Velocity 的 kapt
}

extra["plumbotBuildConstants"] = true // 可选：需要构建版本号时生成 BuildConstants（只有 :velocity 这样做）

dependencies {
    implementation(project(":common"))
    implementation(project(":render"))          // 渲染门户，平台层负责装配
    implementation(project(":adapter:onebot"))
    compileOnly("your.host:api:1.0")            // 宿主 API：有 libs 别名就用别名，没有写字面坐标
    implementation(libs.libbyBukkit)            // Libby：按平台换成 libbyVelocity / libbyHytale
    implementation(libs.bstatsBukkit)           // bStats：bstatsVelocity / bstatsHytale
}

tasks { shadowJar { archiveBaseName.set("PlumBot-MyPlatform"); archiveVersion.set("") } }
```

4. **依赖别名**：`gradle/libs.versions.toml` 里现有的是 `libs.velocityApi`、`libs.libbyCore` / `libbyBukkit` / `libbyVelocity` / `libbyHytale`、`libs.adventureApi` 系列、`libs.bstatsBukkit` / `bstatsVelocity` / `bstatsHytale`、`sqlite` / `mysql` / `hikaricp` / `aedile` / `playwright` 等。**没有** `libs.platformApi`、`libs.libbyPlatform`、`spigot-api` —— `:bukkit` 的 Spigot API 是字面坐标 `compileOnly("org.spigotmc:spigot-api:1.13-R0.1-SNAPSHOT")`，`:hytale` 是 `compileOnly("com.hypixel.hytale:Server:+")`。宿主 API 没有现成别名就直接写字面坐标，别硬编一个不存在的。
5. **产物名**：`shadowJar` 的 `archiveBaseName` 要唯一（现有 `PlumBot-Bukkit` / `PlumBot-Velocity` / `PlumBot-Hytale`），`archiveVersion` 清空，避免 `dist/` 里出现带版本号的文件名。
6. **进 `dist/`**：根 `build.gradle.kts` 的 `dist` 任务（`copyDist`）会对每个应用了 `com.gradleup.shadow` 的子项目 `from(shadowJarTask)`，所以第 3、5 步做对之后 `./gradlew dist` 自动收进根目录 `dist/`，不需要改根脚本。
7. **过 `check`**：根脚本给所有 `base` 子项目挂上 `checkRenderBoundaries`、`checkRenderProtocol`、`checkRuntimeLibraries`、`checkSingleDefinitions`，`checkRelocation` 会先跑 `dist` 再断言产物里没有未重定位的第三方包。新平台模块同样受这些约束，别用 `//noinspection` 绕。

## 二、边界铁律

1. **零业务逻辑驻留**：敏感词、SQLite/MySQL 持久化、白名单判定、OneBot 协议解析都在 `:common`（协议在 `:adapter:*`），平台模块不重写任何一条。
2. **平台类型不越界**：`JavaPlugin`、`ProxyServer`、`CommandSource` 这类宿主类型只能出现在平台模块内，不得进入 `:api` 或 `:common`。
3. **七个构件齐全**：构建配置、动态依赖加载、平台上下文、控制台捕获、服务端事件网桥、扩展生态守护、主入口与指令 —— 少一个就是半成品。

## 三、七个构件

对照 `bukkit/src/main/kotlin/me/regadpole/plumbot/bukkit/` 读。构建配置见上一节。

### 1. 动态依赖加载器 `[Platform]DependencyLoader`

实现 `RenderLibraryLoader`，参考 `bukkit/.../platform/BukkitDependencyLoader.kt`、`velocity/.../platform/VelocityDependencyLoader.kt`、`hytale/.../platform/HytaleDependencyLoader.kt`。仓库顺序固定：自定义仓库 → 阿里云镜像（`libraries.use_aliyun_mirror`，默认开）→ Maven Central → JitPack → PaperMC（`libraries.use_papermc_repo`，默认开）→ 按 `KyoriSupport.manifestFor(...)` 装载清单库。

**加载哪些库以 `common/src/main/kotlin/me/regadpole/plumbot/internal/RuntimeLibraries.kt` 为唯一真源**：那里有 15 项基础库（guava、slf4j-api、HikariCP、gson、sqlite-jdbc、mysql-connector-j、configurate 系列、aedile-core、caffeine、Java-WebSocket、AOneBot 等）、Hytale 专用的 slf4j-simple、6 项 Adventure 回退包和 Playwright 三件套。平台侧不要自己另列一份清单；坐标写 `{}` 分隔（`RELOCATE_ROOT` 同款约定），重定位规则与 `platform-distribution` 保持一致。

### 2. 平台上下文 `[Platform]PlatformContext`

实现 `me.regadpole.plumbot.api.platform.PlatformContext`：`config` / `datasource`、`dataDirectory`、`scheduler`、`playerService`、`platformType`、`supportedCapabilities`、`log(LogLevel, String)`、`sendMessage(Component)`、`isPluginAvailable`、`dispatchConsoleCommand` / `dispatchConsoleCommandEcho`、`hasCapability`。

- **`supportedCapabilities` 按真实能力声明**：`:bukkit` 声明 9 项（`CHAT_RECEIVE`、`CHAT_BROADCAST`、`PRE_LOGIN_INTERCEPT`、`PLAYER_JOIN_BROADCAST`、`PLAYER_QUIT_BROADCAST`、`PLAYER_DEATH_BROADCAST`、`PLAYER_ADVANCEMENT_BROADCAST`、`SERVER_TPS_METRICS`、`COMMAND_DISPATCH`），`:velocity` 声明 7 项（多 `SERVER_SWITCH_BROADCAST`，没有死亡/成就/TPS）。`GameEventBridge` 按 `hasCapability` 守卫，虚标一项就是对外承诺一条本端永远不会产生事件源的通道。
- **`log`** 映射到宿主原生 logger；**`sendMessage`** 原生支持 Adventure 就直连，否则降级成传统文本。

`PlatformScheduler` 六个方法都要实现：`runSync`、`runAsync`、`runLater`、`runLaterAsync`、`runRepeating`、`runRepeatingAsync`，入参用 `kotlin.time.Duration`，返回 `PlatformTaskHandle`（只有 `cancel(): Boolean`，已排进宿主队列、拿不到可取消句柄时返回 `false`）。`runSync` 必须真的落到宿主主线程/世界线程，**不能写成 `runSync = runAsync` 敷衍**；宿主只提供异步时按 `HytalePlatformScheduler` 的做法串行化排进 tick 队列，没有可用世界时回退异步并只 WARN 一次。契约里没有 `isPrimaryThread()`，需要主线程判定的模块自己实现（参考 `HytaleTickThreads`）。

`PlatformPlayerService`：`listPlayers()`、`listPlayerString()`、`kickPlayer(name)` 是必选；`getPlayerUuid`、`skinTextureFromPlugin`、`skinTextureFromProfile`、`premiumUuid`、`resolveAvatarLayers` 有默认实现（返回 `null` / 空），皮肤能力按平台补。

### 3. 控制台捕获器 `Capturing[Platform]Sender`

包装 `:common` 的 `OutputCapturingBuffer(forwardTo)`（`common/src/main/kotlin/me/regadpole/plumbot/utils/OutputCapturingBuffer.kt`）：同时截字符串与 Adventure `Component` 两路输出，并**透传**给真实控制台，保证物理机屏幕同步可见。参考 `bukkit/.../platform/CapturingConsoleCommandSender.kt`、`velocity/.../platform/CapturingVelocityCommandSource.kt`、`hytale/.../platform/CapturingHytaleCommandSender.kt`。机制细节见 [`internal-dev.md`](internal-dev.md) 的「控制台回显捕获」一节，这里只要求：两路都拦、都透传。

### 4. 服务端事件网桥 `[Platform]ServerListener`

监听宿主原生事件，转成 `:common` 的调用，不要自己写判定：

| 宿主事件 | 调用 | 说明 |
|---|---|---|
| 玩家聊天 | `GameEventBridge.onChat(playerName, serverName, rawMessage)` | 剥格式码、过敏感词后群发 |
| 预登录 | `PlayerLoginService.check(playerName)` | 白名单前置拦截，见下 |
| 进服 | `GameEventBridge.onJoin(playerName)` | 顺带补写绑定表的 UUID |
| 离服 | `GameEventBridge.onLeave(playerName, serverName)` | |
| 跨服 | `GameEventBridge.onChangeServer(playerName, origin, target)` | 守卫 `SERVER_SWITCH_BROADCAST` |
| 死亡 | `GameEventBridge.onDeath(playerName, serverName, deathMessage)` | 守卫 `PLAYER_DEATH_BROADCAST` |
| 成就 | `GameEventBridge.onAdvancement(playerName, serverName, title)` | 守卫 `PLAYER_ADVANCEMENT_BROADCAST` |

登录拦截是 fail-closed 的，结果按 `PreLoginResult(allowed, kickMessage, denial)` 走：`denial = NOT_BOUND` 用宿主的白名单踢出原因（Bukkit 是 `AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST`），`denial = LOOKUP_UNAVAILABLE`（查不清：超时、数据库异常或未就绪）**照样拒绝**，但踢出原因用通用类（`KICK_OTHER`），文案取 `messages.yml` 的 `kickUnavailable`。**按 `denial` 选原因，不要只看 `allowed`。** 应急放行只有一条路：`feature.bind.whitelist` 改 `false`。`check()` 最多阻塞 `feature.bind.login_lookup_timeout_ms`（默认 500ms，钳制 50–5000），所以宿主的预登录事件必须异步可用，别在只能同步跑主线程的钩子里调它。

### 5. 扩展生态守护 `[Platform]PluginListener`

监听宿主插件卸载（Bukkit 是 `PluginDisableEvent`），对第三方插件做**双重注销**，缺一就会在热重载后留下旧 ClassLoader 的监听与工厂：

- `api.extensionRegistry.unregisterAllFor(plugin)`：注销该插件注册的外部 `BotFactory`，返回移除条数；
- `BotEventDispatcher.unregisterAllFor(plugin)`：注销它用 `subscribeGroupMessage` 等接口挂上的事件句柄。

`api` 从 `PlumBotAPI.get()` 取（实现里用 `runCatching { … }.getOrNull()`，PlumBot 已卸载时跳过）。参考 `bukkit/.../listener/PluginListener.kt`、`velocity/.../listener/VelocityPluginListener.kt`。

### 6. 主入口 `PlumBot[Platform]` 的装配时序

**以 `bukkit/src/main/kotlin/me/regadpole/plumbot/bukkit/PlumBotBukkit.kt` 的 `onEnable()` 为准**（本文是唯一真源，`internal-dev.md` 不再重复此时序）：

1. `loadDependencies()` 装运行期库；`RenderProvider.registerLibraryLoader(dependencyLoader)` 登记加载器。
2. 落地 `dataDirectory`，复制 `datasource.yml`、`config.yml` 默认文件。
3. `BotProvider.clearFactories()`，再 `BotProvider.registerFactory(...)` 注册本端支持的适配器（Bukkit 是 `OneBotFactory`、`MiraiMCFactory`）。
4. 创建平台上下文与 `PlumBot` 实现，`PlumBotApiProvider.register(apiImpl)` 公开 API，`BotEventDispatcher.attachedPlugin = this`。
5. 向宿主注册服务（Bukkit 用 `servicesManager.register(...)` 暴露 `BotProvider`、`DatabaseProvider`、`PlumBotAPI`），然后 `enable()`：它依次加载配置、渲染配置、数据库与 Bot 客户端。
6. 异步 `platformContext.warmUpEcho()`；创建 `FilterThesaurusManager(platformContext)` 挂到 `FilterManagerHolder.manager` 并 `reloadAsync()`。
7. 注册 `ServerListener`、`PluginListener`、适配器监听器，最后挂 `/plumbot` 的 executor 与 tabCompleter。

`onDisable()`：`disable()`（停 Bot、关数据库与线程池、跑 `ShutdownRegistry`）→ 遍历 `BotProvider.availableAdapters()` 逐个 `unregisterFactory(type)` → `FilterManagerHolder.manager = null` → `PlumBotApiProvider.unregister()` → `BotEventDispatcher.attachedPlugin = null`。

### 7. 原生管理指令 `/plumbot`

权限节点 `plumbot.admin`，子指令语义统一在 `:common` 的 `PlumBotCommandHandler`（`reload`、`status`、`bind`、`render`、`doctor`，其中 `doctor` 还有 `render` / `echo`）。平台侧只做三件事：权限判定、Tab 补全、把 `Component` 落成本端可发的文本。参考 `bukkit/.../command/PlumBotCommand.kt`、`velocity/.../command/PlumBotVelocityCommand.kt`。

## 四、完成前的自检

- [ ] `settings.gradle.kts` 已 `include`，模块应用了 `buildsrc.convention.platform-distribution`，`./gradlew dist` 后在 `dist/` 看到 `PlumBot-<Platform>.jar`。
- [ ] `supportedCapabilities` 与宿主真实能力一致；代理端没虚标死亡/TPS，实体服没虚标跨服。
- [ ] `runSync` 真的在主线程/世界线程上跑，不是 `runAsync` 的别名。
- [ ] `RuntimeLibraries` 的坐标在本端全部可加载，且重定位规则一致（`./gradlew checkRelocation` 通过）。
- [ ] 转发到服务端的玩家名与聊天文本都过 `escapeMiniMessageTags`（`common/src/main/kotlin/me/regadpole/plumbot/utils/ComponentUtils.kt:50`）。
- [ ] `PluginListener` 做了双重注销；`FilterThesaurusManager` 已挂到 `FilterManagerHolder.manager`；`onDisable` 完整走完上面的回收序列。
