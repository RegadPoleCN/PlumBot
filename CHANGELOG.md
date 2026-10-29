# Changelog

本项目的显著变更记录在此。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 新增 `/plumbot render doctor|install` 子命令：前者列出浏览器、驱动与模板的缺失项，后者把 Chromium 安装到 `render.playwright.browsers_path`，`--force` 可强制重装。
- Playwright 不再隐式下载浏览器：按显式 `browser_channel`、自定义路径、系统 Chrome/Edge、托管 Chromium 的顺序解析，缺失时报 `PW_BROWSER_MISSING` 并给出手动安装命令。
- 新增 `/plumbot doctor echo`，只读探测远程控制台的各回显通道并输出诊断行，不会向服务器派发指令。
- 新增运行时健康计数：渲染关闭、引擎预热失败、图片发送失败、词库重载失败、数据库初始化失败等，均汇总到群内 `/status` 的健康行与 `/plumbot doctor render`。
- QQ 与玩家的绑定记录持久化来源群（`source_group`），退群解绑只处理本群来源的记录，来源未知的历史数据不受影响。
- 新增 bStats 指标上报，用于统计适配器类型等运行信息。

### Changed
- 配置升级到 `config_version: 2`：图片开关统一收口到 `render.outputs.<功能>`，旧版配置在启动时自动迁移。
- `render.views` 支持按路由覆盖 `engine` 与 `timeout_ms`；`render.engine` 默认 `auto`，显式值优先于路由首选引擎。
- 渲染下载允许任意 https 跳转目标，以兼容 CDN 与镜像站的 302；每一跳只要求 https，头像下载的起点还受 `render.avatar.domains` 白名单约束。
- 下载日志按等级拆分：拒绝记 WARN，逐跳跟随与连接失败记 DEBUG。
- 构建与 CI 工具链统一为 JDK 25。
- `/plumbot` 的 help、status、bind、reload 逻辑由三端共用一份实现，平台模块只保留指令注册。
- 运行期优先使用宿主提供的 Adventure/kyori，不再打包或重定位；不带 kyori 的宿主由 Libby 在启动时装入。
- `IBot` 的图片发送接口返回布尔结果，`PlumBotAPI` 在失败时抛 `BotMessageSendException`；适配器失败后回退纯文本并计数，不再只写日志。
- 数据库的 `user_id` 统一按字符串处理，SQLite 与 MySQL 共用同一套契约。

### Fixed
- 登录白名单校验改为 fail-closed：查询超时或数据库未就绪时拒绝登录，并区分「未绑定」与「暂时无法校验」的踢出原因。
- 服务器指标缺失时显示统一的 `N/A`，不再回落成看起来正常的 20.0 TPS。
- Bot 上下线提示只在连接建立后发送；连接过程由状态机建模，并体现在 `/status` 的 Bot 状态里。
- 修复 SQLite/MySQL 建表、既有表补列、绑定写入与配额并发问题。
- 修复卸载流程：有界排空渲染任务，释放线程池、数据库连接池与缓存。
- 修复远程控制台输出截断与请求限流，避免回显刷屏或阻塞主线程。
- 修复云端词库拉取无上限的问题，超限时降级读取本地快照。
- 修复 Bukkit 多日志后端的回显捕获，使游戏内回复与群聊回复保持一致。

### Removed
- 移除旧渲染实现与 `kotlin-reflect` 依赖。
- 前端工作区不再随主仓库分发，`plumbot-web-sdk` 与官方模板由独立仓库发布。

## [3.0.0-beta1] - 2026-09-30
v3 架构重写的首个测试版：独立 `:api` 契约模块、Bukkit / Velocity / Hytale 三端宿主、Aho-Corasick 敏感词过滤与 AWT 渲染兜底。

## [1.3.3] - 2024-07-12
白名单管理增强（管理员不受数量限制，可查询与删除）。

## [1.3.2] - 2024-05-26
修复 Velocity 群聊数据读取，加入 BungeeCord 支持，并补充构建与文档维护。

## [1.3.1] - 2024-02-20
适配 Bukkit 1.7.10–1.20.x，修复自定义命令与 QQ 消息转发。

## [1.3.0] - 2024-02-09
集中修复已知缺陷。

[Unreleased]: https://github.com/RegadPoleCN/PlumBot/compare/3.0.0-beta1...HEAD
[3.0.0-beta1]: https://github.com/RegadPoleCN/PlumBot/releases/tag/3.0.0-beta1
[1.3.3]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.3
[1.3.2]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.2
[1.3.1]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.1
[1.3.0]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.0
