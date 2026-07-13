# Adapter / Platform 兼容矩阵

本文档用于人工维护 Bot adapter 与 platform 的兼容关系。新增、迁移或删除 adapter 时，必须同步更新本矩阵。

## 兼容矩阵

| Adapter | Module | Bukkit | Velocity | Bungee | Standalone | Required Plugin | Notes |
|---|---|---:|---:|---:|---:|---|---|
| onebot | `:adapter:onebot` | 是 | 是 | 是 | 是 | 无 | 基于 WebSocket 接入，理论上平台无关；具体启动方式由 platform 注册 factory 决定。 |
| miraimc | `:adapter:miraimc` | 是 | 否 | 否 | 否 | MiraiMC | 依赖 MiraiMC 插件 API，当前视为 Bukkit-only adapter。 |

## 字段说明

- **Adapter**：稳定 adapter id，建议与配置中的 `bot.type` 保持可映射关系。
- **Module**：Gradle 模块名。
- **Bukkit / Velocity / Bungee / Standalone**：是否支持对应运行平台。
- **Required Plugin**：运行时必须存在的平台插件；没有则填“无”。
- **Notes**：兼容性限制、运行方式或迁移备注。

## 维护规则

1. 新增 adapter 时必须新增一行。
2. adapter 支持的平台必须保守填写；不确定时填写“否”或注明“待验证”。
3. 如果 adapter 依赖 Bukkit、Velocity 等平台 API，必须在 Notes 中说明，并避免在不支持的平台提前加载相关类。
4. 如果 adapter 依赖可选插件，必须在 Required Plugin 中列出插件名。
5. 修改 adapter metadata 时，应同步检查本矩阵是否一致。

## 矩阵说明

代码迁移已完成，矩阵记录 `:adapter:onebot` 与 `:adapter:miraimc` 的实际兼容状态。新增或修改 adapter 时应同步更新本文件。
