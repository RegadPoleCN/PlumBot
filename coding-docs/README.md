# PlumBot 开发与架构文档

面向核心维护者、代码贡献者与第三方附属插件开发者。结构、时序与契约以 [架构概览](architecture.md) 为准；已知安全风险见 [SECURITY.md](../SECURITY.md)。

## 文档索引

| 文档 | 面向对象 | 内容 |
|---|---|---|
| [架构概览](architecture.md) | 全员 | 模块分层与依赖方向、启动关停时序、消息与事件流转、渲染子系统数据流、构建发布链路、公开 API 契约分级 |
| [渲染子系统规范](render.md) | 核心维护者 / 平台适配者 | 渲染门户纪律、协议与路由、头像与模板管线、并发与降级、安全边界 |
| [Playwright 运维手册](playwright.md) | 服主 / 二次分发者 | 驱动安装、`PW_` 错误码（唯一真源）、`render doctor` 诊断、镜像与沙箱排障 |
| [新平台接入指南](adding-platform.md) | 平台适配者 | 接入清单、约定插件与依赖别名、装配时序、能力补齐与核对 |
| [附属插件开发手册](extension-guide.md) | 外部开发者 | 依赖与本仓库不发布 Maven 的事实、`PlumBotAPI` 用法、事件监听、能力校验、可编译样例 |
| [内部核心开发手册](internal-dev.md) | 内部维护者 | 调试流程、`debug` 开关、配置缓存与类加载器坑、关闭时序 |
| [构建与发布规范](build-and-release.md) | 全员 / DevOps | `buildSrc` 约定插件、`dist` 产物、四个校验任务、版本号与发布流程 |

仓库根目录另有 [CHANGELOG.md](../CHANGELOG.md)、[CONTRIBUTING.md](../CONTRIBUTING.md)、[SECURITY.md](../SECURITY.md)。

## 阅读路径

- **只想装和调**：仓库根 [README](../README.md) → 渲染出问题看 [Playwright 运维手册](playwright.md)。
- **要写附属插件**：[架构概览](architecture.md) 的契约分级 → [附属插件开发手册](extension-guide.md)，并遵守其中的弃用流程。
- **要改核心**：[架构概览](architecture.md) → [内部核心开发手册](internal-dev.md) → [构建与发布规范](build-and-release.md)。
- **要接新平台**：[新平台接入指南](adding-platform.md) → [架构概览](architecture.md) 的时序与能力枚举。

## 维护约定

1. 文档只描述当前代码；与代码冲突以代码为准，改代码时同步改文档。
2. 不写内部工单编号、阶段称呼与一次性性能采样；这类信息留在提交信息或 PR 里。
3. KDoc 写「为什么」与契约边界，复述签名的注释不写。
