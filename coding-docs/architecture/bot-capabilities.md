# Bot Capabilities

本文档说明 PlumBot Bot adapter 的能力模型。能力模型用于避免假设所有 Bot 接入都支持同一组功能，并为运行前校验和清晰错误提示提供依据。

## 设计目标

- adapter 只声明自己真实支持的能力。
- common 根据业务需求检查能力，而不是直接假设所有方法可用。
- 不支持的能力应在启动或调用前给出明确提示。
- 新增 adapter 时，可以通过能力表快速判断是否满足现有业务。

## 能力列表

| Capability | 含义 | 典型使用场景 | 当前相关 adapter |
|---|---|---|---|
| `GROUP_MESSAGE_SEND` | 群消息发送 | 服务器聊天转发、Bot 命令回复、群到服消息转发 | OneBot、MiraiMC |
| `USER_MESSAGE_SEND` | 用户/私聊消息发送 | 私聊命令、私聊提醒 | OneBot、MiraiMC |
| `IMAGE_SEND` | 图片消息发送 | 图片模式、长文本渲染为图片 | OneBot、MiraiMC |
| `GROUP_MEMBER_QUERY` | 查询群成员信息 | 昵称、群名片、绑定校验占位符 | OneBot、MiraiMC |
| `GROUP_MEMBER_CHECK` | 检查群成员是否在群 | 绑定校验、权限检查 | OneBot、MiraiMC |
| `GROUP_MESSAGE_RECEIVE` | 接收群消息事件 | 群消息监听、群命令触发 | OneBot、MiraiMC |
| `GROUP_MEMBER_DECREASE_RECEIVE` | 接收群成员减少事件 | 退群自动解绑 | OneBot、MiraiMC |

## 使用原则

1. common 业务服务在依赖特定能力前，应通过 metadata 或 registry 校验能力是否存在。
2. adapter 不应为了满足旧接口而伪造能力；无法实现时应不声明该能力。
3. 如果某能力只在特定配置下可用，应在 adapter 文档或 metadata 中说明限制。
4. 新增能力时应同步更新：
   - 本文档能力列表。
   - adapter metadata。
   - 兼容矩阵备注。
   - 依赖该能力的业务服务校验逻辑。

## 与现有接口的关系

`IBot` 已引入 metadata、factory 和 registry，并在业务入口增加了能力校验。能力模型已成为新增 adapter 的主要约束，adapter 只需实现自身真实支持的能力，无需被迫实现所有旧方法。
