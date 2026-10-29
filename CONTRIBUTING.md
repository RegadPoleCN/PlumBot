# 贡献指南

## 环境

- JDK 25：Gradle 工具链固定为 25，产物 class 版本为 69。
- 使用仓库自带的 `./gradlew`，无需预装 Gradle。

## 构建与测试

```bash
./gradlew build                 # 编译全部模块并执行测试与校验
./gradlew check                 # 单元测试 + 契约、边界、协议、运行库校验
./gradlew dist checkRelocation  # 归集 dist/ 产物，并断言重定位与依赖边界
```

`check` 会触发 `checkApiContract`、`checkRenderBoundaries`、`checkRenderProtocol`、`checkRuntimeLibraries`、`checkSingleDefinitions`。

## 代码风格

- 遵循 Kotlin 官方风格；公开 API 与关键实现写 KDoc。
- KDoc 只说明「为什么」与契约（前置条件、副作用、线程约束、失败语义），不复述代码，也不写内部工单编号。
- 适配层不得依赖 `:render`；其他模块只能通过门户 `RenderPortal` 使用渲染能力，`:api` 不得依赖渲染实现。这些边界由 `check` 机械校验。

## 提交信息

采用 `type(scope): English summary`，例如 `fix(render): retry template downloads`。type 使用 feat、fix、refactor、docs、test、build、ci、chore；scope 使用模块名（api、render、common、bukkit、velocity、hytale、adapter 等）。

## Pull Request

- 提交前本地运行 `./gradlew check`，CI 必须全绿。
- 一个 PR 只做一件事；修改配置结构时同步更新 `common/src/main/resources/config.yml` 与迁移逻辑。
- 新增平台模块前先读 [coding-docs/adding-platform.md](coding-docs/adding-platform.md)，并在 `settings.gradle.kts` 中注册模块。
