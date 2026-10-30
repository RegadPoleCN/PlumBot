# PlumBot

*升级版本前请先备份配置文件。*

Minecraft 服务器与 QQ/IM 机器人双向互通插件，支持 Paper/Spigot 后端子服、Velocity 跨服代理端与 Hytale 服务端。

插件交流群：825894832

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![CodeFactor](https://www.codefactor.io/repository/github/regadpolecn/plumbot/badge)](https://www.codefactor.io/repository/github/regadpolecn/plumbot)
[![Build & Verify](https://github.com/RegadPoleCN/PlumBot/actions/workflows/build.yml/badge.svg)](https://github.com/RegadPoleCN/PlumBot/actions/workflows/build.yml)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/RegadPoleCN/PlumBot/total?logo=github)](https://github.com/RegadPoleCN/PlumBot)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/PlumBot?logo=modrinth&label=modrinth)](https://modrinth.com/plugin/plumbot)

## 特性

- 游戏与 QQ 群双向消息转发，可按前缀过滤，转发到游戏前转义 MiniMessage 标签。
- 白名单申请、查询与删除，进服强制校验，带登录缓存与查询超时上限。
- 群内查询 1m/5m/15m TPS、MSPT、堆内存与在线人数（`/status`、`/tps`）。
- 管理员远程执行服务器指令（`/cmd`），按 `admins` 鉴权、每分钟限流、输出截断与回显捕获。
- Aho-Corasick 多模式敏感词过滤，支持云端词库与本地快照回退。
- 图片渲染，`:render` 门户统一收口 17 条 route，AWT 与 Playwright 引擎按 route 配置。
- `:api` 公开契约，OneBot v11 与 MiraiMC 两种协议适配。
- SQLite / MySQL 持久化绑定数据。

## 模块

模块划分与依赖方向（实现依赖 `:api`，宿主依赖实现，`adapter/*` 不依赖 `:render`）：

```
PlumBot/
├── api/            # 公开 API 契约、领域模型与事件
├── render/         # 渲染门户：AWT / Playwright 引擎、route 注册表、头像合成与缓存
├── common/         # 平台无关业务核心：消息分发、白名单、敏感词、持久层、指令分发
├── bukkit/         # Paper/Spigot 宿主
├── velocity/       # Velocity 跨服代理宿主
├── hytale/         # Hytale 服务端宿主
└── adapter/
    ├── onebot/     # OneBot v11 适配层
    └── miraimc/    # MiraiMC 适配层
```

## 快速开始

1. 从 [Releases](https://github.com/RegadPoleCN/PlumBot/releases) 下载对应端的 jar：`PlumBot-Bukkit.jar`、`PlumBot-Velocity.jar` 或 `PlumBot-Hytale.jar`（[Modrinth](https://modrinth.com/plugin/plumbot) 同步发布）。
2. 放入对应服务端的插件/模块目录，启动一次生成 `config.yml`（运行需要 JDK 25）。
3. 至少配置 `bot.onebot.address`、`bot.onebot.token`、`groups` 与 `admins`。
4. 改配置后执行 `/plumbot reload`。

## 配置概述

配置文件为插件数据目录下的 `config.yml`，各键注释见 [config.yml](common/src/main/resources/config.yml)。

| 段 | 作用 |
|---|---|
| `bot.type` | `onebot` 或 `miraimc`，分别对应 `bot.onebot.*` / `bot.miraimc.*` |
| `groups` / `admins` / `command.*` | 响应的群号、可执行管理指令的 QQ 号、群指令前缀与触发词 |
| `feature.*` | 功能开关：消息转发、白名单、进服/退服通知、状态、远程控制台 |
| `render.*` | 出图开关 `render.outputs.<功能>`、引擎、文本样式、头像缓存 |
| `filter.*` | 敏感词动作、云端词库地址、本地补充与豁免词 |
| `database.*` | `sqlite`（默认）或 `mysql` 及连接参数 |

## 图片渲染（可选）

出图入口统一收口到 `:render` 的 `RenderPortal`，共 17 条 route（含兼容旧模板的 `hytaleAvatar`）。可选引擎：

| 引擎 | 说明 |
|---|---|
| `none` | 纯文本 |
| `awt` | JVM 文本卡片，不需要浏览器 |
| `playwright` | 浏览器渲染 Vue 单页模板卡片，按需装载 |

`render.engine` 默认 `auto`：头像类 route 走 `playwright`，其余走 `awt`；`render.views.<route>.engine` 可按 route 覆盖，显式值优先。功能是否出图由 `render.outputs.<功能>` 控制。Playwright 需要 Chromium，安装与诊断：

```text
/plumbot render doctor           # 输出 PW_ 错误码、驱动与浏览器路径
/plumbot render install          # 下载 Chromium 到 browsers_path
/plumbot render install --force  # 强制重装
```

浏览器查找顺序：`render.playwright.custom_chrome_path` → `browser_channel`（`chrome` / `msedge`）→ 系统 Chrome/Edge → `browsers_path` 下的托管 Chromium。全部未命中报 `PW_BROWSER_MISSING`，按 `render.playwright.fallback_to_awt`（默认 true）用 AWT 兜底或降级纯文本，不会在渲染线程等待下载。

离线环境可手动安装：设置 `PLAYWRIGHT_BROWSERS_PATH` 与 `PLAYWRIGHT_DOWNLOAD_HOST` 后执行 `npx --yes playwright@1.49.0 install chromium --no-shell`；或用插件已下载的 `playwright`、`driver`、`driver-bundle` 三个 jar 调 `com.microsoft.playwright.CLI install chromium --no-shell`。优先用 `/plumbot render install`。

头像由服务端预取并合成为单张 `data:` URL：来源限 https 且解析后拒绝内网 IP，结果写入 `render.avatar.cache_dir`，缓存时长由 `render.avatar.cache_ttl_hours` 控制（默认 168 小时）。完整规范见 [render.md](coding-docs/render.md)；安装与 `PW_` 错误码排障见 [playwright.md](coding-docs/playwright.md)。

## 构建与开发

环境：JDK 25，使用仓库自带的 `./gradlew`。

```bash
./gradlew check                 # 单元测试 + 契约/边界/协议/单一真源/装载清单校验
./gradlew dist checkRelocation  # 构建三个产物并断言重定位结果
```

构建后 `dist/` 输出 `PlumBot-Bukkit.jar`、`PlumBot-Velocity.jar`、`PlumBot-Hytale.jar`。

## 文档

- [architecture.md](coding-docs/architecture.md)：模块分层与依赖方向
- [render.md](coding-docs/render.md)：渲染门户、route 与协议
- [playwright.md](coding-docs/playwright.md)：Playwright 安装与排障
- [adding-platform.md](coding-docs/adding-platform.md)：接入新宿主平台
- [extension-guide.md](coding-docs/extension-guide.md)：附属插件与外部扩展
- [internal-dev.md](coding-docs/internal-dev.md)：内部核心开发
- [build-and-release.md](coding-docs/build-and-release.md)：构建与发布
- [CONTRIBUTING.md](CONTRIBUTING.md)：贡献流程
- [SECURITY.md](SECURITY.md)：安全问题报告
- [CHANGELOG.md](CHANGELOG.md)：版本变更

## 许可

本项目以 [GNU Affero General Public License v3.0 or later](LICENSE)（AGPL-3.0-or-later）开源。感谢所有提交 issue、PR 与使用反馈的贡献者。
