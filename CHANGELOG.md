# Changelog

本项目的显著变更记录在此。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [3.0.0-beta2] - 2026-11-01

### Added
- 新增独立 `:render` 渲染门户模块：AWT 与 Playwright 双引擎，`render.engine: auto` 时由各 route 自选首选引擎；17 条 route 以 `render-protocol.json` 为协议清单，构建期 `checkRenderProtocol` 保证清单、`config.yml` 与引擎默认值三方一致。
- 新增群内头像查询（`avatar` 指令词、`render.outputs.avatar`）：解析正版皮肤档案、裁出头像层并落盘缓存，`render.avatar.*` 控制域名白名单、缓存目录与 TTL、头图尺寸、超时预算与失败冷却。
- 模板下载支持多镜像与完整性校验：`render.playwright.template_urls` 非空时完全覆盖候选，`template_sha256` 校验内容，`download_host` 指定 Chromium 安装镜像；`template_url` 以 github.com 为起点时自动追加 ghproxy.net / gh-proxy.com / ghfast.top，任一可用即停止。
- 新增 `/plumbot render stats|reset`：`stats` 列出出图 / 降级 / 失败、AWT 兜底、队列拒绝与引擎未就绪、配置关闭等分类计数及最近一次失败原因；`reset` 成对清零门户与业务层计数。
- 通用文本卡片路由 `render.outputs.text`：聊天转发、控制台回显等纯文本输出可选走渲染，默认关闭以免占满浏览器队列。
- 新增 `/plumbot render doctor|install` 子命令：前者列出浏览器、驱动与模板的缺失项，后者把 Chromium 安装到 `render.playwright.browsers_path`，`--force` 可强制重装。
- Playwright 不再隐式下载浏览器：按显式 `browser_channel`、自定义路径、系统 Chrome/Edge、托管 Chromium 的顺序解析，缺失时报 `PW_BROWSER_MISSING` 并给出手动安装命令。
- 新增 `/plumbot doctor echo`，只读探测远程控制台的各回显通道并输出诊断行，不会向服务器派发指令。
- 新增运行时健康计数：渲染关闭、引擎预热失败、图片发送失败、词库重载失败、数据库初始化失败等，均汇总到群内 `/status` 的健康行与 `/plumbot doctor render`。
- QQ 与玩家的绑定记录持久化来源群（`source_group`），退群解绑只处理本群来源的记录，来源未知的历史数据不受影响。
- 新增 bStats 指标上报，用于统计适配器类型等运行信息。

### Changed
- 配置升级到 `config_version: 2`：图片开关统一收口到 `render.outputs.<功能>`，旧版配置在启动时自动迁移。
- 指令配置整体收口到 `command` 段：`feature.cmdPrefix` → `command.prefix`，`keys` → `command.keys`，并新增 `avatar` 指令词；字体项 `feature.img.file` 移到 `render.font`。
- 远程控制台细化为可调项：`feature.remote_console.log_commands` / `rate_limit_per_minute` / `max_concurrent`；回显捕获由 `feature.remote_console.echo.*` 配置通道顺序、等待窗口与旁路日志源（log4j2 / JUL / latest.log），RCON、NMS 与代理端 sender 默认关闭。
- 新增若干可调项：`feature.message.console_echo`（群消息是否同时写入服务端控制台）、`feature.bind.login_cache_ttl_seconds` 与 `login_lookup_timeout_ms`（登录校验的进程内缓存与查询超时）、`render.limits.*`（载荷上限）、`render.playwright.{fallback_to_awt, install_shell, browsers_path, custom_chrome_path, browser_channel, dev_web_dir}`。
- 构建期新增结构化校验：`:api` 契约、渲染边界与包布局、运行库清单、单一来源定义，发布流水线逐项跑负例自检，`checkRelocation` 随 `dist` 一同执行。
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
- 配置关闭出图（`render.outputs.<功能>: false`）不再与引擎兜底降级混算：前者不计入降级指标，后者每次写 ERROR（5 分钟去重）并计数。

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

[3.0.0-beta2]: https://github.com/RegadPoleCN/PlumBot/releases/tag/v3.0.0-beta2
[3.0.0-beta1]: https://github.com/RegadPoleCN/PlumBot/releases/tag/3.0.0-beta1
[1.3.3]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.3
[1.3.2]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.2
[1.3.1]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.1
[1.3.0]: https://github.com/RegadPoleCN/PlumBot/releases/tag/1.3.0
