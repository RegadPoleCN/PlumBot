# 调试与问题排查

本文档汇总 PlumBot 开发和使用过程中常见的调试手段与问题排查思路。

## 日志

### 平台日志

- Bukkit 平台日志通过 `BukkitPlatformLogger` 输出到服务端控制台/日志文件。
- 日志级别对应 `me.regadpole.plumbot.internal.LogLevel`：DEBUG、INFO、WARN、ERROR。

### DebugProvider

`DebugProvider`（`common/.../DebugProvider.kt`）用于收集调试日志：

- 在 `config.yml` 中开启：
  ```yaml
  debug:
    enable: true
    save_interval: 0  # 0 表示立即写入文件，其他值表示按间隔批量写入
  ```
- 日志文件位于 `<dataDirectory>/debug/` 下。
- `DebugProvider.log()` 是线程安全的，使用 `ConcurrentLinkedQueue` 收集日志。

## 常见问题

### Bot 无法启动

1. 检查 `config.yml` 中 `bot.type` 是否正确（`onebot` / `mirai`）。
2. 检查 `BotProvider.loadBot()` 的日志，确认 factory 是否注册、metadata 是否支持当前平台。
3. 检查必需插件是否存在（如 MiraiMC 是否启用）。
4. 检查 adapter 能力是否满足业务需求。

### 命令无响应

1. 检查 `config.yml` 中 `feature.cmdPrefix` 和 `keys` 配置。
2. 检查 `DefaultBotHandler` 的命令注册表是否包含该命令。
3. 检查功能开关 `feature.<name>.enable` 是否开启。
4. 检查 `Messages.internalError` 是否被触发（通常意味着异常被捕获）。

### 消息转发异常

1. 检查 `feature.message.enable` 和 `feature.message.mode`。
2. mode=1 时检查 `feature.message.prefix` 是否匹配。
3. 检查 `ServerChatService` 和 `MessageForwardService` 是否统一使用 `MessageModeResolver`。

### 数据库异常

1. 检查 `datasource.yml` 配置是否正确。
2. 检查 `DatabaseProvider.getDatabase()` 是否非空。
3. 检查数据库文件路径是否有写权限（SQLite）。
4. 检查 MySQL 连接信息、表权限。

### 图片渲染失败

1. 检查 `config.yml` 中 `feature.img.file` 配置的字体文件是否存在。
2. 检查字体文件路径中的 `%plugin_folder%` 是否被正确替换。
3. 查看日志中 `TextToImg` 抛出的 `IllegalStateException`。

## 排查工具

### Gradle 编译检查

```powershell
.\gradlew.bat :common:compileKotlin :adapter:onebot:compileKotlin :adapter:miraimc:compileKotlin :bukkit:compileKotlin
```

### 静态检查

- 确认 `common` 模块不包含 AOneBot / MiraiMC / Bukkit import。
- 确认 adapter 源码中无 `wait()` / `notify()`。

### 运行时调试

- 在 `DefaultBotHandler.onGroupMessage` 入口处打印消息内容。
- 在命令类 `execute` 中打印参数解析结果。
- 开启 `DebugProvider` 收集完整事件流。

## 提交前检查

- [ ] 所有模块编译通过。
- [ ] `:bukkit:shadowJar` 成功生成产物。
- [ ] 新增配置有默认值。
- [ ] 新增消息字段有默认值。
- [ ] 未在 `common` 中引入平台/协议类。
- [ ] 已更新相关文档。
