# 渲染子系统（`:render`）技术规范

`render/` 把业务数据变成发进 QQ 的图片，并统一收口所有出图路径（纯文本卡片、状态板、头像）。它不依赖平台适配层，适配层也只经门户接口取字节。

面向核心维护者与平台适配者。配置键与默认值以 `common/src/main/resources/config.yml` 的 `render:` 段为准；为什么这样取舍见 [decisions.md](decisions.md)，本文不重复决策编号。

> **出图开关只有一套**：每个功能只有 `render.outputs.<gate>` 一个键（共 13 个），`feature.*` 只留 `enable` 与业务参数；默认关闭的是 `load`、`message`、`joinAndLeave`、`death`、`advancement`、`kick`、`text`。
>
> `config_version` 固定为 2，旧键形状的迁移映射只有一份，在 `common/src/main/kotlin/me/regadpole/plumbot/internal/config/LegacyConfigMigration.kt`；高于当前版本号的配置原样保留。

## 1. 分层与门户纪律

```text
业务层（命令 / 事件 / 公共 API）   只产出 (route, payload)，不认识图片与引擎
   │
   ▼
:render  RenderProvider（唯一实例、唯一生命周期）
           └─ RenderPortal（对外唯一入口）
                 └─ ImageRenderManager（internal）
                       ├─ route 注册表 + 门控 + 引擎解析
                       ├─ 有界容量 + 同目标串行
                       ├─ 载荷长度治理
                       └─ 失败一律返回 RenderResult，不抛异常
   │
   ▼
适配层 IBot  只做「发字节」sendGroupImage / sendUserImage，或发文本
```

四条纪律，前三条由 `build.gradle.kts` 的 `checkRenderBoundaries`（随 `check` 执行）机械校验：

1. **适配层零依赖 `:render`**，只把字节发出去。
2. **其他模块不得引用渲染内部类型**：`TextToImg`、`ThemeManager`、`AwtImageRenderer`、`PlaywrightImageRenderer`、`PayloadGovernor`、`RenderSecurity`、`AvatarComposer`、`ImageRenderManager`、`ImageRenderer`。
3. **`:api` 不得依赖渲染**；`:render` 的非 `internal` 顶层声明必须带 `@PublicApi`、`@StableApi` 或 `@ExperimentalApi`。
4. **门户唯一**：平台启用时经 `RenderProvider.bootstrap(...)` 装配，禁用时 `shutdown()`；业务类不得自行构造。

### 1.1 生命周期：关闭必须异步

`/plumbot reload` 与停服都在服务端主线程上调用，而 Playwright 的 `Browser.close()` / `PlaywrightImpl.close()` 会**同步**等待 node 驱动进程退出（`ProcessImpl.waitFor()`）。直接在调用线程执行就是一次 MSPT 尖峰，因此：

| 位置 | 约束 |
|---|---|
| `ImageRenderManager.shutdown()` / 引擎重建 | 锁内只摘引用并清空表，回收任务入队 `RenderTeardown`，调用线程立即返回；门户 `shutdownAndAwait(3000)` 同步等 3 秒 |
| 引擎 `releaseAll()` | 页面池 → context → browser → driver；先摘引用再逐步 `closeWithin(...)`，单步超时 5s / 5s / 10s / 15s |
| `PlumBot.applyRenderConfig()` | 必须 `RenderProvider.getOrNull() ?: RenderProvider.bootstrap(platform)`；reload 只重新 `configure()`，引擎重建走 `reload()` 的异步路径 |
| 回收线程 | `PlumBot-Render-Reaper`，daemon 单线程串行；单任务异常只 WARN，不中断后续任务 |

代价是关闭与新引擎重建可能短暂并存（旧 Chromium 在后台退出），进行中的出图降级为纯文本。**停服时等待引擎拆除是预期行为，不是卡死。**

### 1.2 重载：只在配置指纹变化时重建引擎

| 环节 | 行为 |
|---|---|
| `configure()` | 计算 `pendingEngineFingerprint`（`RenderEngineFingerprint.of`）；启动期首次直接认领，避免首次 reload 误判 |
| `reload()`，指纹相同 | 只打一行 INFO「渲染配置未变化」，引擎保留 |
| `reload()`，指纹不同 | 只置 `engineResetPending`，**不关闭任何引擎** |
| 下一次 `rendererFor()` | `applyPendingEngineReset()` 摘引用并交 `RenderTeardown` 回收（懒重建） |
| 主动关闭 vs 驱动崩溃 | `closingInternally` 区分两者：前者只打 INFO，后者才报「底层 Chromium 意外退出」 |

指纹只收构造期一次性读取的项：`render.engine`、各 `render.views.<route>.engine`、`render.playwright` 的 theme / template_protocol / template_url / template_sha256 / dev_web_dir / scale_factor / image_format / jpeg_quality / browsers_path / custom_chrome_path / browser_channel / disable_sandbox / fallback_to_awt / render_timeout_ms / font_max_bytes / download_host，以及字体文件的路径 + 大小 + mtime。`render.outputs.*`、载荷治理阈值、`template_urls` 每次实时读取，**不入指纹**。

测试护栏：`render/src/test/kotlin/me/regadpole/plumbot/render/RenderEngineReloadTest.kt`。

## 2. 路由表

路由名同时是前端模板的视图路径。`featureGate` 指向 `render.outputs.<gate>`，每条路由都有门控。

统一载荷 `RenderCard`（`:api` 契约）：`text` 是唯一正文真源（带 § 色码，纯文本降级去色后发送），`title` 是业务标题，`data` 只放模板需要的结构化字段。

```kotlin
RenderCard(text: String, title: String? = null, data: Map<String, Any?> = emptyMap())
```

`text` 与 `data` 由同一处 `RenderCards.<route>(...)` 产出，图片、纯文本、降级三条路径内容一致。下表列 `data` 字段：

| route | `data` 字段 | outputs 键 |
|---|---|---|
| `status` | `serverName, tps[], mspt, usedMem, maxMem, online, health` | `status` |
| `playerList` | `total, players[{name, boundQq?, avatarUrl?}]` | `list` |
| `whitelist` | `playerName, qq, bindTime?, avatarUrl?, originId?, originName?, bindCount, current[]` | `bind` |
| `bindList` | `title, userId, players[], total, limit` | `bind` |
| `help` | `prefix, commands[{usage, description}]` | `help` |
| `load` / `unload` | 无（正文即 `text`） | `load` |
| `message` | `playerName, serverName, message` | `message` |
| `joinAndLeave` | `type, playerName, serverName?` | `joinAndLeave` |
| `changeServer` | `playerName, originServer, targetServer` | `joinAndLeave` |
| `death` | `playerName, serverName, deathMessage` | `death` |
| `advancement` | `playerName, serverName, advancementTitle` | `advancement` |
| `kick` | `playerName, reason, guide` | `kick` |
| `console` | `state, channel?, code?, durationMs?` | `remote_console` |
| `text` | 无（`text`/`title` 之外为空） | `text` |
| `avatar` | `username, avatarUrl?, fallbackQq?, source, platform` | `avatar` |
| `hytaleAvatar` | 同 `avatar` 但不带 `platform`（旧路由，仅兼容旧模板） | `avatar` |

17 条 route 去重后是 13 个 `featureGate`。头像有两条 route：`avatar`（插件当前下发）与 `hytaleAvatar`（兼容保留，插件不再主动下发），共用 `render.outputs.avatar`，首选引擎都是 `playwright`（AWT 画不出头像）。

**引擎解析优先级**：`render.views.<route>.engine` → `render.engine` → 路由首选（头像类 `playwright`，其余 `awt`）。`auto` 表示「未显式配置」；显式写 `awt` / `playwright` / `none` 即以显式值为准，并打一行 INFO 说明它覆盖了路由首选。

`message`（游戏聊天 → QQ）是唯一高频路径，**建议固定 awt**：Playwright 单次 100–500ms，且页面池只有 1 页、出图串行。

**降级链**：引擎关闭 / 平台不支持 / 模板缺失 / 握手失败 / 渲染超时 / 容量溢出，全部落到纯文本。Playwright 失败时若 `fallback_to_awt=true`（默认）先试 AWT 出图并写 ERROR；为 `false` 则直接纯文本并返回 `PW_` 错误码，**不允许静默换引擎**。

> `status.health` 口径「出图含兜底图 · 截至上一次出图，本次不计入」：正文先固化、计数发生在出图之后，所以「降级 0」不代表这张图没降级；同一份 health 只渲染一处。

## 3. 并发与容量

| 项 | 值 |
|---|---|
| 并发上限 | 2（`CONCURRENCY`）；Playwright 侧复用单页串行，第 2 个额度是给 AWT 兜底与排队的余量 |
| Playwright 页面池 | 1 页（`PAGE_POOL_SIZE = 1`） |
| 排队深度 | 6（信号量总许可 = 并发 + 排队） |
| 溢出行为 | **立即降级纯文本**，不排队、不静默丢弃 |
| 顺序保证 | 同 `targetKey`（如 `group:123456`）串行，出图顺序与消息顺序一致 |

## 4. 协议清单（单一真源）

`render/src/main/resources/render-protocol.json` 是 route、载荷字段、长度额度与文本可读性规则的唯一真源。协议号 **1**；所有 route 的载荷统一为 `RenderCard { text, title?, data }`。

消费者：`:render` 读 route 注册表与字段上限，`config.yml` 只做覆盖（不重复定义结构），前端 SDK 据此生成类型与 manifest。

**运行时握手**：模板启动时把 `window.__PLUMBOT_MANIFEST__ = {protocol, routes, sdk}` 挂到页面，插件导航后读取校验——协议版本不符则一次性 WARN 并直接降级（不白等导航超时）；目标 route 未声明则跳过渲染；旧模板无 manifest 时保持兼容。

`checkRenderProtocol` 保证清单与 Kotlin `RenderRoute` 双向一致、字段齐备，且文本可读性规则在清单 / `config.yml` / 引擎默认值之间一致。前端字段漂移由运行时自检兜底：`/plumbot render doctor` 输出 `protocol fields: 17 routes, mismatches: N`。

前端契约（SDK 与官方 Vue 模板独立 npm 发布，不随本仓库分发）：

- manifest 的 route 集合与路由路径都必须与清单逐字一致（`#/playerList` 而非 `#/list`）；额外只允许开发画廊 `preview`，且 `preview` 不得写进 manifest；
- 生产模板构建为单文件 HTML，插件用 `file://` 打开时无外部资源依赖；截图目标为 `#render-target`。
- 开发期用 `render.playwright.dev_web_dir` 指向本地构建目录；线上用 `template_url`（单地址，兼容旧配置）或 `template_urls`（非空即完全覆盖默认候选）。

## 5. 引擎接通

### 5.1 按需装载（Libby）

- 坐标 `com.microsoft.playwright:playwright:1.49.0`，`resolveTransitiveDependencies(true)`，**禁止 relocate**（Playwright 依赖自身原生 node driver 的路径结构）。
- `:render` 对该依赖是 `compileOnly`，**不进 Jar**；三个平台各自实现 `RenderLibraryLoader.ensurePlaywright()` 并通过 `RenderProvider.registerLibraryLoader(...)` 注入。
- **三道门**：注册门（`engine != playwright` 不实例化驱动）、装载门（`PlaywrightInstallGate`）、能力门（`Class.forName` 真实探测）。

装载门把「要不要下载」收进一个状态机（`IDLE → RUNNING → READY | COOLING`），三条不变式：

1. **非阻塞**：`startOnce(reason)` 只把装载排进 `PlatformScheduler.runAsync` 就返回。渲染路径发现类不在类路径时只按一下按钮就抛 `PW_DEPENDENCY_MISSING`，本次交给 AWT；在渲染线程里内联下载会让出图卡到超时。
2. **单飞 + 冷却**：`RUNNING` 期间重复请求直接拒绝，`startOnce` 与 `awaitLoad()`（doctor / install 的阻塞入口）共用一把装载锁；`READY` 后不再触发，失败冷却 60s，`COOLING` 期间不摸网络、降级日志降为 INFO。

装载入口必须齐备，否则「只有消息能出图、只敲命令的服务器永远降级」：`configure()` 见已启用且 `engine=playwright` 的路由即调度（`schedulePlaywrightLoadIfConfigured`）；消息走 `warmUpEngines()`；命令回复走 `sendRendered()` → `warmUpEngines()`；`doRender()` 再兜底一次（幂等 + 冷却）。

主线程守卫在装载器一侧：Bukkit 用 `Bukkit.isPrimaryThread()`，Hytale 靠 `HytaleTickThreads` 登记后查 `isOnGameThread()`，Velocity 无 tick 线程概念不设守卫。`engine=awt` 时**不下载、不装载、不实例化、类不加载**。

### 5.2 浏览器获取链（只检测，不自动下载）

```text
① custom_chrome_path 存在且可执行 → executablePath
② browser_channel = chrome / msedge → 该品牌 channel；与 custom 冲突或不可用 → PW_CONFIG_INVALID
③ 系统浏览器探测：Windows（LOCALAPPDATA、Program Files[ (x86)]）、Linux（多路径含 chromium）、macOS（三个 App 路径）
④ browsers_path 托管目录：chromium-* / chromium_headless_shell-*，revision 倒序，同 revision 完整版优先
⑤ 全未命中 → PW_BROWSER_MISSING（附安装命令），绝不自动下载
```

- `PLAYWRIGHT_BROWSERS_PATH` 固定落**插件数据目录**（不污染 `~/.cache` / `%APPDATA%`）；**`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` 必须在 `Playwright.create()` 之前注入**（`DriverJar.initialize(true)` 会无条件执行 `node cli.js install`，漏掉会把渲染线程阻塞到超时）；`PLAYWRIGHT_DOWNLOAD_HOST` 只影响显式安装器；三者都经 `Playwright.CreateOptions().setEnv(...)` 注入。

### 5.3 模板获取与校验

- 候选链：未配置 `template_urls` 时 = `template_url`（缺省为官方直链，其 release 必须是正式版，否则 `releases/latest` 不指向它）打头，其后追加 `ghproxy.net` / `gh-proxy.com` / `ghfast.top` 前缀候选；只有 `https://github.com/...` 起点才追加，且只用 https 前缀，非 https 条目判不可用。
- 逐候选尝试：连接失败 / 非 2xx / 空内容 / 超体积 / 落盘失败 → 换下一个；全失败 → `PW_TEMPLATE_MISSING`，WARN 附逐条原因与手动下载命令。
- **sha256 不匹配直接失败、不换镜像**（ERROR 说明镜像被篡改 / 模板已更新 / 配置过期，并保留旧模板）；下载开始与完成各 1 行 INFO（注明「第 N/M 个候选」），逐跳明细走 DEBUG，候选列表不进指纹。

### 5.4 安装

安装与排障全部见 [playwright.md](playwright.md)：`/plumbot render doctor` 诊断，`/plumbot render install [--force]` 安装；三个 jar 的体积、手动 CLI 与镜像限制也在那里。运行时由 Libby 按需下载，坐标与版本以 `RuntimeLibraries.PLAYWRIGHT` / `RuntimeLibraryVersions.PLAYWRIGHT` 为准。

### 5.5 失败语义与版本升级

- 渲染失败按阶段返回 `PW_` 错误码并附阶段、详情、修复提示与手动命令；码表见 [playwright.md](playwright.md)，本文不复制。
- `fallback_to_awt=true`（默认）仍出 AWT 图，但每次降级写 ERROR（同引擎 + 同路由 5 分钟去重）并计 `RenderMetrics.FALLBACK`；`false` 时直接失败 + 纯文本。
- Playwright 版本变更必须重装浏览器二进制，其自带的旧版本会被 GC。**升级插件后首次出图报 `PW_BROWSER_MISSING` 是预期行为**，重新 install 即可。

## 6. 头像管线（服务端预取 + 合成 + 五级漏斗）

为什么在服务端做：浏览器侧取图意味着每张卡片 N 次网络往返全落在渲染超时窗口内，且跨域要开 `--disable-web-security`。

```text
① 皮肤插件（SkinsRestorer，反射零硬依赖）
② 内存 GameProfile 纹理（Bukkit 走 Paper PlayerProfile 反射；Velocity 走 GameProfile.properties）
③ 正版皮肤源（premiumUuid → sessionserver → textures.minecraft.net）
④ 绑定 QQ 头像
⑤ 前端中性占位图
```

三级来源统一产出 **base64 纹理属性**，所以 `base64 → JSON → textures.SKIN.url` 的解析只在 `AvatarResolver` 一处，平台层不碰 JSON。

下游只收两种中间表示，其余都在服务端收敛：平台图层经 `composeAvatar(有序图层)` 合成为单张 PNG data URL；绑定 QQ 头像经 `fetchAvatarAsDataUrl(url)` 走磁盘缓存后同样是 data URL；都不可用则由前端逐级降级到中性占位图。

| 纪律 | 值 | 理由 |
|---|---|---|
| 逐级超时 | 800ms | 某一级卡住只拖慢该级 |
| 总预算 | 3000ms | 独立于渲染超时窗口；在 `payload(portal)` 求值时执行，**先于** `portal.render` |
| 失败冷却 | 10 分钟 | 否则查不到皮肤的玩家每次列表刷新都跑满整条漏斗 |
| 缓存键 | `skin:<source>:<name>` | 含来源级 |

- **出口白名单只约束起点 + 仅 https（逐跳）+ 体积上限**：`render.avatar.domains` 可覆盖内置列表；不做解析后 IP 判定。
- **磁盘缓存** `AvatarCache`：SHA-256 文件名、TTL、原子替换，`cache_dir` / `cache_ttl_hours` 可配。
- **裁头在服务端完成**（`MinecraftSkinCropper`）：64×64 现代（面部 `(8,8)` + 外层 `(40,8)` 按 alpha 合成）、64×32 旧版无外层、128×128 按 2 倍布局，最近邻放大到 `head_size`（默认 128）。线上契约因此收敛为单张成品图，浏览器端不需要 canvas、跨域或模式嗅探。

Hytale 资产为 **Hypixel Studios 专有许可**：绝不随插件分发任何 Hytale 资产（构建断言禁止产物含 `com/hypixel/hytale/**`）；解析顺序为服主覆盖 `plugins/PlumBot/assets/characters/<category>/` → 官方 `AssetModule` → `<category>/default.png`，调用链见 `hytale/src/main/kotlin/me/regadpole/plumbot/hytale/avatar/HytaleAvatarResolver.kt`。

## 7. 长度治理

| 层 | 对象 | 策略 |
|---|---|---|
| ① 展示文本 | 玩家名、聊天、控制台回显 | per-field 上限；超限截断并标省略号 + 聚合告警；组合符收敛 |
| ② 二进制 / 图层 | `data:` URL | 不做字符截断；按单层上限 + 总量预算 + 图层数上限判定，超限丢该层 |
| ③ 整体预算 | payload 文本总量 | 超阈值告警 |

递归穿透 `RenderCard` / `Map` / `List` / `Set` / `Array`（旧实现只处理顶层，嵌套结构完全绕过），额度由 `render.limits.*` 配置。`RenderCard.text` 是唯一正文真源，**不参与** per-field 截断（否则图片与纯文本降级会变成两套内容），只计入整体预算告警；`title` 与 `data` 内的字符串仍按上表治理。

## 8. 安全基线

| 项 | 做法 |
|---|---|
| `file://` 白名单 | **仅主题根目录**（旧实现放行整个数据目录，而那里存着数据库口令） |
| 网络出口 | 起点主机白名单（精确 / 子域）+ **仅 https 逐跳**；重定向手动逐跳跟随、上限 20 跳；不做解析后 IP 判定 |
| 模板下载 | 仅 https；体积上限；原子替换；`disconnect()`；不忽略返回值 |
| 浏览器开关 | 已移除 `--disable-web-security` 与 `--allow-file-access-from-files`（头像改为服务端 data URL 后不需要） |

## 9. 明确放弃项（不留空头承诺）

| 项 | 处置 | 原因 |
|---|---|---|
| WebP 自适应协商 | **不做**，固定 PNG | 需先确认各 QQ 客户端与 Mirai/OneBot 对 WebP 的接受度 |
| 断网前置嗅探 | 改为「下载/首帧失败 → 降级 + 冷却」 | 更简单可靠 |
| `PR_SET_PDEATHSIG` 防孤儿进程 | 改为「shutdown hook + 显式 close + 存活检测」 | Linux 专有，纯 JVM 无法直接调用 |
| 插件重载时解绑 ClassLoader | 改为「复用已装载库，不重启浏览器」 | Libby 注入的 jar 无法真正卸载 |
| 超宽图手机缩放适配 | 低优先 | 纯 CSS 层面 |
| 低配 CPU 保护 | 仅缓解（并发上限 2，Playwright 单页） | 无法对 Chromium 做硬保证 |

## 10. 验证

```bash
./gradlew check          # 含 checkRenderBoundaries / checkRenderProtocol / checkRuntimeLibraries / checkSingleDefinitions
./gradlew test           # :render 单测
./gradlew dist           # 打包；确认产物内 playwright 类数为 0
```

三个校验任务都带负例自检（`-PrenderBoundarySelfTest=true`、`-PrenderProtocolSelfTest=true`、`-PruntimeLibrarySelfTest=true`），改规则时应跑。

**运行期验收（需真实服务器）**：

- [ ] `engine=awt` 时以 `-verbose:class` 观察，确认无 `com.microsoft.playwright.*` 装载、无相关网络请求
- [ ] 未装浏览器时 `engine=playwright` 立即返回 `PW_BROWSER_MISSING`（不阻塞、不下载），doctor 能看到缺项与手动命令
- [ ] `/plumbot render install` 成功后同一 route 正常出图；`--force` 可重装
- [ ] `fallback_to_awt=false` 时失败只发纯文本；`true` 时出 AWT 图且日志有 `[PlumBot-Render]` ERROR 与 `PW_` 原因
- [ ] `engine=playwright` 下 `/plumbot reload` 在毫秒级返回，等待进程退出的是 `PlumBot-Render-Reaper`；主线程栈里不应出现 `ProcessImpl.waitFor()`，进程列表无残留 Chromium
- [ ] 只敲命令（不触发消息事件）时首帧即由 Playwright 出图，日志无「引擎 playwright 不可用」
- [ ] 配置未改时 reload 只出现「渲染配置未变化」；改 `scale_factor` 后 reload 不立即关浏览器，下一次出图前才回收旧引擎

## 11. 构建产物与分发

**`:render` 不作为独立构件发布**：随各平台 `shadowJar` 分发（三个平台模块都显式 `implementation(project(":render"))`），`render/build/libs/render-*.jar` 只是中间产物，`dist/` 不收集。对外契约是 `render-protocol.json` 与 `RenderPortal`，分别由 `checkRenderProtocol` / `checkRenderBoundaries` 守护。

- **Playwright 是 `compileOnly`，永不进包**（`dist` 产物内 `com/microsoft/playwright/**` 类数为 0），运行期按需下载。
- **`BuildConstants` 只在 `:velocity` 生成**（早期每个模块各生成一份，shading 时 8 份同名类相撞）；需要它的模块在自身 `build.gradle.kts` 声明 `extra["plumbotBuildConstants"] = true`。
