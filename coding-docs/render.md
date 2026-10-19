# 渲染子系统（`:render`）技术规范

面向核心维护者与平台适配者。`render/` 模块负责把业务数据变成发进 QQ 的图片，并**统一收口**所有出图路径。

> ## 📌 配置说明（2026-10-19）
>
> 出图开关统一集中在 `render.outputs.<功能>`，每个功能只保留一个键，由门户统一读取。`feature.*` 只保留 `enable` 与业务参数；`feature.img`、`feature.<功能>.pic`、`feature.img.views.<route>.pic`、`feature.help` / `feature.kick` 均已移除。`text` 路由没有独立开关，始终可渲染；`hytaleAvatar` 由 `render.outputs.avatar` 控制。
>
> `config_version` 固定为 **2**，不新增 3。`ConfigMigrator` 对无版本 / 1 版配置执行 1→2 迁移；对已标记 2、但仍残留开发期旧键的配置执行一次形状归一化，**版本号保持 2**。迁移映射：`feature.img.*` → `render.*`（`file` → `font`）、旧双重出图开关 → `render.outputs.<gate>`、`avatarCard.pic` → `render.outputs.avatar`、`cmdPrefix` / `keys` → `command.prefix` / `command.keys`。版本高于当前代码时原样保留，不降级改写。

---

## 1. 分层与门户纪律（不可违背）

```
业务层（命令 / 事件 / 公共 API）
    │  只产出 (route, payload)，不认识图片、不认识引擎
    ▼
:render  RenderProvider（唯一实例 + 唯一生命周期）
              └─ RenderPortal（对外唯一入口）
                     └─ ImageRenderManager（internal）
                            ├─ route registry + 门控 + 引擎解析
                            ├─ 有界容量 + 同目标串行
                            ├─ 载荷长度治理
                            └─ 失败一律返回 RenderResult，永不抛异常
    ▼
适配层 IBot：只做「发字节」sendGroupImage / sendUserImage 或「发文本」
```

**三条硬规则**：

1. **其他模块不得引用 `:render` 的内部类型。** `TextToImg`、`ThemeManager`、`ImageRenderer`、`RenderOptions`、各驱动、`PayloadGovernor`、`RenderSecurity`、`AvatarComposer` 等全部为 `internal`。
2. **适配层零依赖 `:render`。** 适配器只把字节发出去。
3. **门户唯一。** 由平台在启用时经 `RenderProvider.bootstrap(...)` 装配，禁用时 `shutdown()`；禁止业务类自行 `new`。

以上由 Gradle 任务 **`checkRenderBoundaries`** 机械校验（4 条规则：适配层零依赖 / 跨模块禁引用内部类型 / `:api` 禁依赖渲染 / `:render` 公开面必须带契约注解）。它已接入 CI。

---

## 2. 路由表

路由名同时是前端模板的视图路径。`featureGate` 指向控制该路由是否出图的开关 `render.outputs.<featureGate>`；为 `null` 表示无开关，始终允许出图。

| route | 载荷 | outputs 键 |
|---|---|---|
| `status` | `serverName, tps[], mspt, usedMem, maxMem, online` | `status` |
| `playerList` | `total, players[{name, boundQq?, avatarUrl?}]` | `list` |
| `whitelist` | `playerName, qq, avatarUrl?, bindTime?` | `bind` |
| `bindList` | `title, players[], total` | `bind` |
| `help` | `prefix, lines[]` | `help` |
| `load` / `unload` | `text` | `load` |
| `message` | `playerName, serverName, message` | `message` |
| `joinAndLeave` | `type, playerName, serverName?` | `joinAndLeave` |
| `changeServer` | `playerName, originServer, targetServer` | `joinAndLeave` |
| `death` | `playerName, serverName, deathMessage` | `death` |
| `advancement` | `playerName, serverName, advancementTitle` | `advancement` |
| `kick` | `playerName` | `kick` |
| `console` | `title?, body, ampersandCodes` | `remote_console` |
| `text` | `title?, body, ampersandCodes` | — |
| `hytaleAvatar` | `username, avatarUrl?, fallbackQq?` | `avatar` |

**引擎解析优先级**：`render.views.<route>.engine` → `render.engine` → 内置默认 `awt`。

> `message`（游戏聊天→QQ）是唯一高频路径，**建议固定 awt**：Playwright 单次 100–500ms、池仅 2 页 2 线程。

**降级链**：`引擎关闭 → 平台不支持 → 模板缺失 → 握手失败 → 渲染超时 → 容量溢出` 全部落到纯文本。

---

## 3. 并发与容量

| 项 | 值 |
|---|---|
| 并发渲染 | 2（与 Playwright 页面池规模一致） |
| 排队深度 | 6 |
| 溢出行为 | **立即降级纯文本**（不排队、不丢弃） |
| 顺序保证 | 同 `targetKey`（如 `group:123456`）串行渲染，保证出图顺序与消息顺序一致 |

---

## 4. 协议清单（单一真源）

`render/src/main/resources/render-protocol.json` 是 route、载荷字段、长度额度与文本可读性规则的唯一真源。

| 消费者 | 用途 |
|---|---|
| `:render` | route 注册表、字段上限、内置默认值 |
| `config.yml` | 只做**覆盖**，不重复定义结构 |
| 前端 SDK / 模板 | 生成类型、派生运行时 manifest、实现 16 条视图 |

**运行时握手**：模板在启动时把 `window.__PLUMBOT_MANIFEST__ = {protocol, routes, sdk}` 挂到页面；插件导航后读取并校验——

- 协议版本不符 → 一次性 WARN + **直接降级**（不再白等整个导航超时）；
- 目标 route 未声明 → 跳过渲染；
- 旧模板未声明 manifest → 保持兼容，继续渲染。

`checkRenderProtocol` 任务保证：清单与 Kotlin `RenderRoute` 枚举**双向一致**、每条 route 字段齐备；当本地检出独立前端源码时，还会校验 SDK 协议版本、route 集合与模板路由路径。

> ⚠️ **模板路由路径必须与协议 route 名逐字一致**（`#/playerList` 而非 `#/list`），否则该 route 永远被判为"未实现"。开发画廊 `#/preview` 是唯一允许的额外路径，且不得写入 manifest。

### 4.1 前端 SDK 与官方模板（独立维护）

前端 SDK 与官方 Vue 模板以独立 npm package 维护，不随本仓库分发。双方只需遵守以下契约：

- 模板启动时写入 `window.__PLUMBOT_MANIFEST__`，route 集合必须与协议清单逐字一致；
- 16 条协议 route 均需有对应视图，额外仅允许开发画廊 `preview`，且 `preview` 不得写入 manifest；
- 生产模板应构建为**单文件 HTML**，确保插件用 `file://` 打开时无外部资源依赖；
- 截图目标为 `#render-target`。

插件接入：开发期用 `render.playwright.dev_web_dir` 指向本地构建目录；线上用 `template_url` 指向托管后的单文件。完整的前端使用说明随前端仓库提供。

---

## 5. 引擎与 Playwright 接通

### 5.1 按需装载（Libby）

- 依赖坐标：`com.microsoft.playwright:playwright:1.49.0`，`resolveTransitiveDependencies(true)`，**禁止 relocate**（Playwright 依赖其原生 node driver 的路径结构）。
- `:render` 对该依赖是 `compileOnly`，**不进 Jar**。
- 三个平台各自实现 `RenderLibraryLoader`（`ensurePlaywright()`）并由 `RenderProvider.registerLibraryLoader(...)` 注入。
- **三道门**：注册门（`engine != playwright` 不实例化驱动）＋ 装载门（`ensureStarted` 首行）＋ 能力门（`Class.forName` 真实探测）。

> `engine=awt` 时：**不下载、不装载、不实例化、类不加载**。

### 5.2 浏览器获取三级链

```
① render.playwright.custom_chrome_path 有效 → 直接使用
     （路径无效：告警并落 ②，不直接失败）
② browser_channel 显式指定 → 自动探测系统 Chrome/Edge
③ 均未命中 → Playwright 自动下载
```

配套：

- `PLAYWRIGHT_BROWSERS_PATH` 固定落**插件数据目录**（不污染 `~/.cache` / `%APPDATA%`）；
- `PLAYWRIGHT_DOWNLOAD_HOST` 浏览器 CDN 镜像（国内网络建议填写）；
- 两者经 `Playwright.CreateOptions().setEnv(...)` 在 `create()` **之前**注入。

### 5.3 首次下载体量（实测构件大小）

| 构件 | 大小 |
|---|---|
| `playwright-1.49.0.jar` | 0.59 MB |
| `driver-bundle-1.49.0.jar` | **189.24 MB** |
| Chromium（Playwright 下载） | ~281 MB |

→ 首次启用 `engine=playwright` 合计约 **470 MB**。因此：

**推荐手动预装（README 记录）**，可用 `--only-shell` 只取 headless shell 显著省体积：

```bash
# 手动预装（可选，推荐给愿意优化体积/走镜像的服主）
PLAYWRIGHT_BROWSERS_PATH="<插件数据目录>/playwright-browsers" \
PLAYWRIGHT_DOWNLOAD_HOST="<镜像地址>" \
java -cp "<插件数据目录>/libraries/com/microsoft/playwright/playwright/1.49.0/playwright-1.49.0.jar" \
     com.microsoft.playwright.CLI install --only-shell chromium
```

未手动安装的服主由 ③ 自动下载兜底（取完整 Chromium）。

> `--only-shell` 只能在手动预装时经 CLI 传入，无法通过环境变量生效。

### 5.4 版本升级语义

Playwright 版本变更必须重新下载浏览器二进制，且其自带旧版本 GC。**升级插件后首次出图会重新下载**，属预期行为。

---

## 6. 头像管线（服务端预取 + 合成）

**为什么在服务端做**：浏览器侧取图意味着每张卡片 N 次网络往返，全部落在渲染超时窗口内；且跨域需要 `--disable-web-security`。

```
① 平台内置外观图层（如 Hytale 部件化皮肤）→ RenderPortal.composeAvatar(有序图层) → 单张 PNG data URL
② 绑定 QQ 头像 → RenderPortal.fetchAvatarAsDataUrl(url) → 磁盘缓存 → data URL
③ 都不可用 → 前端组件逐级降级（绑定 QQ → 中性占位图）
```

- **磁盘缓存**：`AvatarCache`，SHA-256 文件名、TTL、原子替换；`render.avatar.cache_dir` / `cache_ttl_hours` 可配。
- **出口白名单 + 解析后 IP 判定 + 体积上限 + 仅 https**：`render.avatar.domains` 可覆盖内置列表。
- **有序图层只是服务端内部中间表示**，线上契约收敛为**单张成品图** → 浏览器端不需要 canvas、不需要跨域、也不需要模式嗅探。

### 6.1 Hytale 资产策略（重要）

Hytale 服务端为 **Hypixel Studios 专有许可**。因此：

- **绝不随插件分发任何 Hytale 资产**（构建断言禁止产物含 `com/hypixel/hytale/**`）；
- 三段解析：**服主覆盖** `plugins/PlumBot/assets/characters/<category>/` → **官方 `AssetModule`**（运行时读取服务端自身文件）→ `<category>/default.png`；
- 调用链见 `hytale/src/main/kotlin/me/regadpole/plumbot/hytale/avatar/HytaleAvatarResolver.kt` 的文档注释。

---

## 7. 长度治理

取代旧的"对顶层 Map 每个字符串一刀切截断到 500 字符且完全静默"：

| 层 | 对象 | 策略 |
|---|---|---|
| ① 展示文本 | 玩家名、聊天、控制台回显 | per-field 上限；超限**截断并标记省略号 + 聚合告警**；组合符收敛 |
| ② 二进制/图层 | `data:` URL | **不做字符截断**；按单层上限 + 总量预算 + 图层数上限判定，超限**丢该层** |
| ③ 整体预算 | payload 文本总量 | 超阈值告警 |

递归穿透 `Map` / `List` / `Set` / `Array`（旧实现只处理顶层，嵌套结构完全绕过）。额度由 `render.limits.*` 配置。

---

## 8. 安全基线

| 项 | 做法 |
|---|---|
| `file://` 白名单 | **仅主题根目录**（旧实现放行整个数据目录，而那里存放数据库口令） |
| 网络出口 | **主机白名单**（精确/子域）+ **解析后 IP 判定**（拒绝全部内网段，含 CGNAT 与 IPv6 映射，掩码式 CIDR 覆盖 `/12`、`/10`、`/15`） |
| 模板下载 | **仅 https**；体积上限；原子替换；`disconnect()`；不忽略任何返回值 |
| 浏览器开关 | 已移除 `--disable-web-security` 与 `--allow-file-access-from-files`（头像改为服务端 data URL 后不再需要） |

---

## 9. 明确降级 / 放弃项（§12，不留空头承诺）

| 项 | 处置 | 原因 |
|---|---|---|
| WebP/PNG 自适应协商 | **不做**，固定 PNG | 需先确认各 QQ 客户端与 Mirai/OneBot 对 WebP 的接受度；PNG 是安全默认 |
| 断网 1 秒前置嗅探 | **改为**"下载/首帧失败 → 降级 + 冷却" | 更简单可靠，无需专门嗅探 |
| 文件句柄防耗尽 | **低优先** | 收益有限 |
| `PR_SET_PDEATHSIG` 防孤儿进程 | **改为**"shutdown hook + 显式 close + 存活检测" | 该机制为 Linux 专有，纯 JVM 内无法直接调用 |
| 插件重载 ClassLoader 解绑 | **改为**"重载复用已装载库，不重启浏览器" | Libby 注入的 jar 无法真正卸载 |
| 超宽图手机缩放适配 | **低优先** | 纯 CSS 层面 |
| 低配 CPU 保护 | **仅缓解**（并发限 2） | 无法对 Chromium 做硬保证 |

---

## 10. 验证命令

```bash
./gradlew compileKotlin check checkApiContract checkRenderBoundaries checkRenderProtocol
./gradlew test          # :render 单测（颜色/描边、守卫、治理、门户、JSON、浏览器策略、合成、缓存、裁头、漏斗、熔断、端到端）
./gradlew dist          # 打包；应确认产物内 playwright 类数为 0
```

**运行期验收（需真实服务器）**：

- [ ] `engine=awt` 时以 `-verbose:class` 观察，确认**无 `com.microsoft.playwright.*` 装载、无相关网络请求**
- [ ] `engine=playwright` 首次出图能自动下载并成功渲染；离线/仓库不可达时平滑降级 AWT 且 WARN 明确
- [ ] 配置指定浏览器 / 探测系统浏览器 / 自动下载三级链各自生效
- [ ] 监听 `-verbose:class` 与进程列表，确认 `shutdown()` 后无残留 Chromium 进程

---

## 11. 构建产物与分发决策

**`:render` 不作为独立构件对外发布。**

- 它随各平台的 `shadowJar` 一起分发（三个平台模块都显式 `implementation(project(":render"))`）；
- `render/build/libs/render-*.jar` 只是构建中间产物，`dist/` 不收集它；
- 因此 `:render` 的公开面无"对外二进制兼容"承诺 —— 真正的对外契约是 `render-protocol.json`
  与 `RenderPortal`，二者分别由 `checkRenderProtocol` / `checkRenderBoundaries` 守护。

**Playwright 是 `compileOnly`，永不进包**（`dist` 产物内 `com/microsoft/playwright/**` 类数应为 0），
运行期由 Libby 按需下载 —— 见 §5。

**`BuildConstants` 只在 `:velocity` 生成**：它含构建版本号，早期实现对每个模块各生成一份，
shading 时 8 份同名类相撞、其中 7 份是纯死重量。需要它的模块在自身 `build.gradle.kts` 中
声明 `extra["plumbotBuildConstants"] = true`。

---

## 12. 皮肤头像五级漏斗（§8.1 / D-2）

```
① 皮肤插件（SkinsRestorer，反射零硬依赖）
② 内存 GameProfile 纹理（Bukkit 走 Paper PlayerProfile 反射；Velocity 走 GameProfile.properties）
③ 正版皮肤源（premiumUuid → sessionserver → textures.minecraft.net）
④ 绑定 QQ 头像
⑤ 前端中性占位图
```

**三级来源统一产出 base64 纹理属性**，因此 `base64 → JSON → textures.SKIN.url` 的解析
只有 `AvatarResolver` 一处，平台层完全不处理 JSON。

调度纪律：

| 项 | 值 | 理由 |
|---|---|---|
| 逐级超时 | 800ms | 某一级卡住只拖慢该级，不拖垮整条漏斗 |
| 总预算 | 3000ms | 独立于渲染超时窗口；漏斗在 `payload(portal)` 求值时执行，**先于** `portal.render` |
| 失败冷却 | 10 分钟 | 否则查不到皮肤的玩家会在每次列表刷新时跑满整条漏斗 |
| 缓存键 | `skin:<source>:<name>` | 含来源级，换源后不脏读到上一级的皮肤 |

**裁头在服务端完成**（`MinecraftSkinCropper`）：64×64 / 64×32 / 128×128 三种布局，
面部 `(8,8)` + 外层 `(40,8)` 按 alpha 合成，最近邻放大。这样可以移除浏览器的
`--disable-web-security`，并把网络往返移出渲染窗口。

