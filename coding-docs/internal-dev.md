# PlumBot 内部核心开发

面向仓库维护者：在 `:common` 里加一条端到端功能要动哪些文件、本地怎么跑、哪些坑会静默失败。平台接入（`PlatformContext` 实现、插件入口装配）见 [adding-platform.md](./adding-platform.md)，对外 API 契约见 [extension-guide.md](./extension-guide.md)。事实以当前源码为准。

## 新增一条功能的链路

### 配置与消息模板

- `common/src/main/resources/config.yml`：功能开关与参数，沿用 `feature.<功能>.<选项>` 的现有层级，不要平铺到顶层。
- `common/src/main/resources/messages.yml`：MiniMessage 模板。键名就是 `Messages` 对象的字段名，`MessagesBinder` 用 `java.lang.reflect` 按名字覆盖（不用 kotlin-reflect，否则 Shadow minimize 后抛 `KotlinReflectionNotSupportedError`）。缺键时字段保留 `<missing:xxx>` 默认值，所以新消息必须同时加字段和 yml 键。
- 结构变更（改名、挪层级）要加一步 `ConfigMigration` 并把 `ConfigMigrator.CONFIG_VERSION` +1；只新增键不用写迁移，`ConfigMigrator.completeMissingDefaults(configFile, "/config.yml")` 会用插件内模板补齐缺失节点，配置版本高于当前代码时直接跳过、不降级。
- 路径里的 `%plugin_folder%` 由各加载点自行替换成插件数据目录，不是全局预处理。

### 文本安全

外部输入（群消息、玩家名、原始聊天）进 MiniMessage 之前必须转义，否则 `<click:run_command:...>` 这类标签会被当真执行：

```kotlin
import me.regadpole.plumbot.utils.asMiniMessage
import me.regadpole.plumbot.utils.escapeMiniMessage

context.sendMessage("<gray>${user.escapeMiniMessage()}: ${text.escapeMiniMessage()}</gray>".asMiniMessage())
```

`ComponentUtils.kt` 是顶层函数文件，import 直接指向函数名：`escapeMiniMessage()`、`asMiniMessage()`、`Component.toLegacyAmpersand()`、`Component.toPlainText()`、`String.stripMinecraftFormatting()`。旧名 `escapeMiniMessageTags` / `getComponentFromMiniMsg` / `getLegacyFromComponent` / `getPlainTextFromComponent` 仍在（内部转调新名），新代码不要再用。

### 群指令

指令表在 `common/src/main/kotlin/me/regadpole/plumbot/listener/DefaultBotHandler.kt` 的 `buildCommands()`，每条是一个私有 `CommandRegistration(keys, feature, regex, payload, handler)`：

```kotlin
CommandRegistration(
    keys = config.getStringList("command.myfeature"),
    feature = "myfeature",
    regex = { prefix, key -> "^${Regex.escape(prefix)}${Regex.escape(key.orEmpty())}\\s+(.+)$" },
    payload = { prefix, key, message ->
        message.replaceFirst(Regex("^${Regex.escape(prefix)}${Regex.escape(key.orEmpty())}\\s+"), "")
    },
    handler = { text, groupId, _ -> myFeatureService.handle(text, groupId) }
)
```

- `handler` 签名是 `(payload: String, groupId: Long, userId: Long) -> Unit`；发消息是四参 `bot.sendMsg(isGroup = true, targetId = groupId, message = text, isPic = false)`。没有三参重载，也没有 `registerCommand` DSL。
- 能力用枚举 `BotCapability.GROUP_MESSAGE_SEND` / `USER_MESSAGE_SEND` / `IMAGE_SEND` / `GROUP_MESSAGE_RECEIVE`，先 `requireCapability` 再用（不满足抛 `IllegalStateException`）。没有 `SEND_GROUP_MSG`。
- 指令表带别名指纹缓存，`/plumbot reload` 后自动重建；不要把 `context.config` 缓存成构造期常量。

### 数据库

接口在 `api/src/main/kotlin/me/regadpole/plumbot/api/database/IDatabase.kt`，实现基类 `AbstractBindingDatabase`（TabooLib Table DSL + Hikari；表名必须走构造参数，做成抽象属性会在构造期 NPE）。

- 查询照 `getByName` 的写法：`table.select(dataSource) { where { "player_name" eq cleanName } }` 之后立刻 `Binding.of(...).firstOrNull()`。单行 `ResultSet` 在迭代结束时就关闭，延后映射会抛 `SQLException: ResultSet closed`。
- 幂等写入用 `addBindIfAbsent(user, name[, sourceGroup])`，返回 `false` 表示角色名已被占用；有名额上限的场景用 `addBindIfAbsentWithinLimit(...)`（名额校验与插入在同一事务内，先查后插会超卖）。
- 写入后调用 `BindingLookupCache.invalidate(name)`，否则登录校验要等到 TTL 过期。
- `getConnection()` 返回 `DataSource`，只供内部事务与测试。

### 缓存与调度

- 通用缓存 `DefaultBotCache`（`common/src/main/kotlin/me/regadpole/plumbot/cache/BotCache.kt`）基于 Aedile/Caffeine：10 分钟刷新、30 分钟过期，同时给挂起与 `CompletableFuture` 两套入口（`getAsync`/`get`/`refresh`/`invalidate`/`invalidateAll`）。加载失败会自动 `invalidate` 再抛，外面不要再包一层重试。
- 调度一律走 `context.scheduler`，全部返回 `PlatformTaskHandle`：

```kotlin
context.scheduler.runAsync { /* 阻塞 IO */ }          // 异步泳道
context.scheduler.runLater(10.seconds) { /* 回主线程 */ }
val handle = context.scheduler.runRepeating(0.seconds, 30.seconds) { /* ... */ }
handle.cancel()
```

回主线程只有 `runSync`；`runLaterAsync` 是"延迟 + 异步"。时间扩展在 `SchedulerExtensions.kt`：`Int.ticks` / `Long.ticks`（1 tick = 50ms）、`Duration.toTicks()`。

## 远程指令回显

`/cmd` 的回显不能靠包装 `ConsoleCommandSender`：Paper 的 `VanillaCommandWrapper` 会把源换回真实控制台，包装器被丢弃，原生指令输出只进后台日志。`BukkitEchoExecutor` 因此走通道候选链 `paper_feedback` → `plugin_command` → `console`（log4j2 appender / latest.log 旁路兜底）。

两条纪律：`EchoSession` 只入队原始 `Component`，投影（Legacy 序列化、`Language` 首次解析）推迟到 `snapshot()` 在 Finalizer 线程执行 —— 这些静态初始化很重，压在主线程就是卡 tick；`BukkitPlatformContext.warmUpEcho()` 在 `onEnable` 的异步泳道预热一次，把通道反射探测与类加载提前。观测点是 `/plumbot doctor echo` 输出的 `main=…ms(prepare=…ms dispatch=…ms)`。

## 本地调试

1. `./gradlew dist`，把 `dist/PlumBot-Bukkit.jar`（或 Velocity/Hytale 包）丢进服务端插件目录，重启。
2. 首启生成 `plugins/PlumBot/{config.yml,messages.yml,datasource.yml}`；每次 `loadConfig()` 都先跑迁移补全再 `YamlConfigurator.createConfig`。
3. `/plumbot reload`（`plumbot.admin`）= `loadConfig` + debug 重载 + 渲染配置与字体 + 词库异步重载。**不重建 bot 连接、不切数据库** —— 改 `bot`、`database` 段必须重启服务端。
4. 诊断：`/plumbot status`、`/plumbot doctor render`、`/plumbot doctor echo`、`/plumbot render install`（装 Chromium 驱动）。
5. 测试：`./gradlew check`；单模块 `./gradlew :common:test`。

### 日志与 debug

`config.yml` 末尾的 `debug` 节点：`debug.enable`（默认 `false`）、`debug.file`（默认 `%plugin_folder%/debug.log`）、`debug.save_interval`（`0` 实时写入，`>=1` 用 `runRepeatingAsync` 批量落盘）。`DebugProvider.isEnabled` 只在真正加载成功时为 `true`，平台层据此把 DEBUG 映射成控制台可见的 INFO；写盘失败打到 stderr 的 `[PlumBot-Debug]`。日志文件只在 `debug.enable: true` 时创建。

## 常见坑

- **配置缓存**：`/plumbot reload` 会换成新的 config 实例。`private val config = context.config` 这类快照永远读旧值，必须写 getter（`val config get() = context.config`，见 `BotCommandService`）。
- **类加载器**：插件类与资源只走插件 classloader；Libby 装入的运行期库在独立 `IsolatedClassLoader`。宿主自带的 API（尤其 Adventure）不要打进产物 —— 打进去的 `Component` 与宿主不是同一个类。
- **关闭时序**：`disable()` 的顺序由依赖决定：bot → 渲染门户 `shutdownAndAwait` → 数据库 → 任务 → debug → `ShutdownRegistry`。门户的 `shutdown` 是 fire-and-forget，不等它，平台会先关掉 classloader、回收线程之后才执行 `PlaywrightImpl.close()`，线上表现为 `The plugin classloader for PlumBot has thrown a zip file error.`；等待超时则 WARN "Render portal shutdown timed out"。
- **主线程纪律**：`onEnable` 里不做阻塞 IO。数据库初始化、bot 连接、渲染引擎重建、字体读取都在异步泳道，主线程只做配置注入（`applyRenderConfig` 只做 `configure()`）。
- **测试临时目录**：`kotlin-jvm` 把所有模块的 `java.io.tmpdir` 指到 `<module>/build/tmp/test-jvm`，`Test` 结束递归删除。用 `Files.createTempDirectory(...)` 即可，别靠 `deleteOnExit()`（对非空目录无效）。
