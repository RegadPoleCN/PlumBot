# PlumBot

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Build Status](https://github.com/RegadPole/PlumBot/actions/workflows/build.yml/badge.svg?branch=v3)](https://github.com/RegadPole/PlumBot/actions/workflows/build.yml)

现代化、高内聚、模块化的 Minecraft 服务器与 QQ/IM 机器人双向互通插件。

---

## ✨ 核心特性

- **多协议适配 (Adapter Architecture)**：
  - **OneBot (v11)**：基于纯 Kotlin 协程 + 原生 WebSocket 双向通信，全结构化 JSON Segment 消息体设计，内置指数退避重连状态机与异步缓存预热。
  - **MiraiMC**：通过本地 Bukkit 运行环境安全桥接 MiraiMC 机器人框架。
- **纯粹公开 API 契约 (`:api`)**：
  - 接口与实现完全物理隔离，命名空间统一为 `me.regadpole.plumbot.api.*`。
  - 零内部实现泄露，提供自动化模块契约检查任务（`checkApiContract`），保证第三方附属插件依赖的长期稳定性。
  - 支持 Bukkit 服务管理器（`ServicesManager`）或静态入口点跨平台动态获取。
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
├── common/         # 平台无关核心逻辑（敏感词过滤、白名单服务、数据库持久层、命令分发）
├── bukkit/         # Paper/Bukkit 宿主实现、多源类库动态加载器
└── adapter/
    ├── onebot/     # 基于 WebSocket 的 OneBot v11 独立适配层
    └── miraimc/    # MiraiMC 协议桥接适配层
```

---

## 🚀 构建与开发

### 环境要求
- JDK 21 或更高版本
- Gradle 9.x（推荐直接使用仓库自带的 `./gradlew`）

### 常用命令

```bash
# 1. 编译全模块与测试代码
./gradlew compileKotlin compileTestKotlin

# 2. 执行全量单元测试与 API 契约静态校验
./gradlew test check checkApiContract

# 3. 构建 Bukkit 最终全量分发包（位于 bukkit/build/libs/PlumBot-Bukkit-*-all.jar）
./gradlew :bukkit:shadowJar
```

---

## 📄 开源许可证

本项目采用 [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) 协议开源。
