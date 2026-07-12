# 新增 Platform 指南

本文档用于指导维护者为 PlumBot 新增服务端平台，例如 Velocity、Bungee 或 Standalone。

## 适用范围

platform 模块负责把具体运行环境适配到 common，不负责实现 Bot 协议，也不应复制 common 中的平台无关业务。

## 命名建议

长期推荐使用：

```text
:platform-<name>
```

示例：

```text
:platform-velocity
:platform-bungee
:platform-standalone
```

当前仓库中的 `:bukkit` 是历史模块名，短期保留不重命名。

## 新增步骤

1. 在 `settings.gradle.kts` 中 include 新模块。
2. 新建平台模块的 `build.gradle.kts`，应用现有 Kotlin JVM convention plugin。
3. 依赖 `project(":common")`。
4. 添加该平台 API 的 `compileOnly` 或运行时依赖。
5. 实现 platform context、logger、scheduler、player service 和 messenger。
6. 将平台事件转换为 common service 输入。
7. 注册与当前平台兼容的 Bot adapter factory。
8. 在兼容矩阵中新增或更新 platform 支持情况。
9. 编译验证新平台模块。
10. 在真实服务端或目标运行环境中验证启动、事件和关闭流程。

## 必需实现

新增 platform 至少需要覆盖以下职责：

- **生命周期**：启动、关闭、资源释放。
- **日志**：包装平台 logger，供 common 使用。
- **调度**：区分主线程和异步任务，提供取消能力。
- **玩家服务**：查询在线玩家、踢出玩家、格式化玩家列表。
- **消息发送**：向服务器广播或向特定玩家发送消息。
- **配置与数据目录**：提供 common 可访问的配置、数据目录或路径。
- **依赖检查**：检查可选插件或平台能力是否存在。

## Listener 设计原则

platform listener 应只做薄适配：

1. 从平台事件中提取必要数据。
2. 调用 common service。
3. 将 common 返回的结果应用回平台事件。

不要在 listener 中直接实现可复用业务，例如白名单绑定、Bot 命令解析、消息模板拼接或数据库查询流程。

## 注册 Adapter

platform 启动时应根据当前平台类型和可选插件状态注册 adapter factory。

示例规则：

- OneBot 基于 WebSocket，通常可在所有平台注册。
- MiraiMC 依赖 Bukkit 插件环境，只应在 Bukkit 且 MiraiMC 可用时注册。

注册前应检查：

- adapter metadata 中是否支持当前 platform。
- required plugin 是否存在并启用。
- 当前业务需要的 capability 是否由 adapter 提供。

## 验证清单

- [ ] 新模块已加入 `settings.gradle.kts`。
- [ ] 模块依赖方向为 platform -> common，不反向依赖。
- [ ] listener 只做事件适配，不复制 common 业务。
- [ ] scheduler 返回真实任务句柄或明确的取消语义。
- [ ] 可选插件缺失时不会触发不支持类的提前加载。
- [ ] 已更新兼容矩阵。
- [ ] 已完成 Gradle 编译验证。
