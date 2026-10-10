# PlumBot

***升级版本时请提前备份配置文件，以防数据丢失***

插件交流群：[825894832](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=-PcufP7TIjLBMOte4H8bHoNmMkP5xZT0&authKey=aPKkGldknKtdCUfX7hhWMFkAOuOpOUYuNZihsUZi9DXvIHVzJhuIRLVfTdCsobZt&noverify=0&group_code=825894832)

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![CodeFactor](https://www.codefactor.io/repository/github/regadpolecn/plumbot/badge)](https://www.codefactor.io/repository/github/regadpolecn/plumbot)
[![Build & Verify v3](https://github.com/RegadPoleCN/PlumBot/actions/workflows/build.yml/badge.svg)](https://github.com/RegadPoleCN/PlumBot/actions/workflows/build.yml)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/RegadPoleCN/PlumBot/total?logo=github)](https://github.com/RegadPoleCN/PlumBot)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/PlumBot?logo=modrinth&label=modrinth)](https://modrinth.com/plugin/plumbot)

现代化、高内聚、模块化的 Minecraft 服务器与 QQ/IM 机器人双向互通插件。同时支持 **Paper/Spigot 后端子服** 与 **Velocity 跨服代理端**。

---

## ✨ 核心特性

- **多端跨服网络架构 (Multi-Platform Host)**：
  - **Bukkit / Paper**：单服实体服支持，覆盖死亡广播、成就广播、游戏主线程安全调度。
  - **Velocity 3.x / 4.x**：代理端总控支持，前置白名单网关鉴权（未绑定直接在代理层切断，保护后端子服免受压测），支持子服跨服跳转群广播。
- **多协议适配矩阵 (Adapter Architecture)**：
  - **OneBot (v11)**：基于纯 Kotlin 协程 + 原生 WebSocket 双向通信，全结构化 JSON Segment 消息体设计，内置指数退避重连状态机与异步缓存预热（全平台跨端通用）。
  - **MiraiMC**：通过本地桥接服务挂载 Mirai 框架，Bukkit 与 Velocity 代理端均已获得官方原生支持。
- **纯粹公开 API 契约 (`:api`)**：
  - 接口与实现完全物理隔离，命名空间统一为 `me.regadpole.plumbot.api.*`。
  - 零内部实现泄露，提供自动化模块契约检查任务（`checkApiContract`），保证第三方附属插件依赖的长期稳定性。
  - 支持 Bukkit 服务管理器（`ServicesManager`）或跨平台静态入口点（`PlumBotAPI.get()`）动态获取。
- **远程运维与性能监控 (Remote Console & Monitoring)**：
  - **群内性能报告 (`/status` / `/tps`)**：支持 1m/5m/15m 三段式 TPS 与平滑多级降级的 MSPT（毫秒级刻耗时采样），附带 JVM 堆内存与在线统计。
  - **安全远程控制台 (`/cmd`)**：白名单 QQ 严格鉴权、控制台审计日志记录、主线程安全调度派发，基于 `OutputCapturingBuffer` 双通道拦截纯文本与 Adventure 组件回显。
- **高性能敏感词过滤体系**：
  - 基于 **Aho-Corasick (AC 自动机)** 双向多模式匹配引擎，支持本地忽略标点/白名单字符跳过。
  - 支持多源云端敏感词库拉取与本地快照自愈回退机制。
- **安全加固 (Security First)**：
  - 群聊转发到游戏全链路 MiniMessage 标签严格转义，杜绝群成员利用 `<click:run_command>` 等标签在游戏控制台实施提权注入。
- **现代异步持久层 & 协程缓存**：
  - 数据库引擎解耦支持 SQLite 与 MySQL。
  - 引入 `Aedile`（基于 Caffeine 的 Kotlin 协程缓存封装），消除传统阻塞等待，保证主事件循环零卡顿。

---

## 🏗️ 模块划分

```
PlumBot/
├── api/            # 纯粹对外 API 契约、领域模型与事件系统 (me.regadpole.plumbot.api.*)
├── render/         # 渲染子系统（唯一门户 RenderPortal：AWT / Playwright 双引擎、路由注册表、头像服务端合成与缓存）
├── common/         # 平台无关核心逻辑（敏感词过滤、白名单服务、数据库持久层、命令分发、回显捕获器）
├── bukkit/         # Paper/Bukkit 实体子服宿主实现、多源类库动态加载器
├── velocity/       # Velocity 跨服代理端宿主实现 (支持前置白名单鉴权、跨服换服广播)
├── hytale/         # Hytale 官方实体服务端宿主实现 (支持握手鉴权、原生外观头像解析)
└── adapter/
    ├── onebot/     # 基于纯协程 WebSocket 的 OneBot v11 独立适配层 (全平台支持)
    └── miraimc/    # MiraiMC 协议桥接适配层 (支持 Bukkit 与 Velocity)
```

> 渲染子系统的完整规范（门户纪律、路由表、协议清单与握手、Playwright 按需装载与浏览器三级链、头像管线、安全基线、明确降级项）见 **[coding-docs/render.md](coding-docs/render.md)**。

---

## 🖼️ 图片渲染（可选）

所有内置指令的出图入口统一收口到 `:render` 的**渲染门户**，共 16 条 route；引擎可按 route 单独配置。

| 引擎 | 说明 | 依赖 |
|---|---|---|
| `none` | 纯文本（全局关闭出图） | 无 |
| `awt` | JVM 轻量文本卡片（**默认**） | 无（需图形库；缺失时自动回退纯文本） |
| `playwright` | Vue 单页模板超清卡片（按需启用） | 首次约 470MB（见下） |

配置：`feature.img.engine` 为全局默认，`feature.img.views.<route>.engine` 可按命令覆盖（建议 `message` 保持 `awt`，它是唯一高频路径）。

### Playwright 引擎的安装

保持默认 `engine: awt` 时**不下载、不装载、不启动任何浏览器**。

启用 `playwright` 后按**三级链**获取浏览器：① `custom_chrome_path` 指定 → ② 自动探测系统 Chrome/Edge → ③ 由 Playwright 自动下载。

> ⚠️ 首次启用（走 ③）需下载 **Playwright 驱动 ~190MB + Chromium ~281MB ≈ 470MB**，请预留磁盘并在低峰期进行。

**推荐手动预装**（可指定镜像、并用 `--only-shell` 只取 headless shell 显著省体积）：

```bash
PLAYWRIGHT_BROWSERS_PATH="<插件数据目录>/playwright-browsers" \
PLAYWRIGHT_DOWNLOAD_HOST="<你的镜像地址>" \
java -cp "<插件数据目录>/libraries/com/microsoft/playwright/playwright/1.49.0/playwright-1.49.0.jar" \
     com.microsoft.playwright.CLI install --only-shell chromium
```

未手动预装的服主由 ③ 自动下载兜底。`feature.img.playwright.download_host` 可在插件内直接指定镜像。

> Playwright 版本变更必须重新下载浏览器且会 GC 旧版本 —— **升级插件后首次出图会重新下载**，属预期行为。

### 头像

头像由**服务端预取并合成**为单张成品图（`data:` URL）后下发给模板，浏览器端不做跨域取图与 canvas 合成：

- 内置来源白名单（Mojang / crafatar / mc-heads / minotar / QQ），仅允许 https，并做**解析后 IP 判定**拒绝内网；
- 结果写入磁盘缓存（`feature.img.avatar.cache_dir`，默认 168 小时）；
- Hytale 原生外观由部件化皮肤实时解析合成，**资产不随插件分发**（服务端为专有许可）。

---

## 🚀 构建与开发

### 环境要求
- JDK 21 或更高版本
- Gradle 9.x（推荐直接使用仓库自带的 `./gradlew`）

### 常用命令

```bash
# 1. 编译全模块与测试代码
./gradlew compileKotlin compileTestKotlin

# 2. 执行全量单元测试与契约/边界静态校验
#    checkApiContract      : `:api` 公开契约注解校验
#    checkRenderBoundaries : 渲染门户边界（适配层零依赖 :render、跨模块禁引用内部类型、公开发布面须有注解）
#    checkRenderProtocol   : 渲染协议清单与 Kotlin route 枚举、前端 SDK 的一致性
./gradlew test check checkApiContract checkRenderBoundaries checkRenderProtocol

# 3. 构建多平台最终全量分发包（统一归集输出到 dist/ 目录）
./gradlew dist
```

构建完成后，`dist/` 目录将直接生成：
- `dist/PlumBot-Bukkit.jar` (适用于 Spigot/Paper/Purpur 后端子服)
- `dist/PlumBot-Velocity.jar` (适用于 Velocity 跨服代理端)
- `dist/PlumBot-Hytale.jar` (适用于 Hytale 官方实体服务端)

---

## 📄 开源许可证

本项目采用 [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) 协议开源。
