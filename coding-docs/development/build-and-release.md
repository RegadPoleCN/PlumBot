# 构建与发布说明

本文档说明 PlumBot 的 Gradle 构建命令、模块产物和版本管理策略。

## 构建环境

- **Gradle**：9.6.1（通过 wrapper 管理）
- **Kotlin**：2.4.0
- **Java**：25（JBR 25）

## 常用构建命令

### 编译全部模块

```powershell
.\gradlew.bat :common:compileKotlin :adapter:onebot:compileKotlin :adapter:miraimc:compileKotlin :bukkit:compileKotlin
```

### 构建 Bukkit 产物

```powershell
.\gradlew.bat :bukkit:shadowJar
```

产物位置：

```text
bukkit/build/libs/PlumBot-Bukkit.jar
```

### 完整验证

```powershell
.\gradlew.bat :common:compileKotlin :adapter:onebot:compileKotlin :adapter:miraimc:compileKotlin :bukkit:compileKotlin :bukkit:shadowJar
```

## 模块产物

| 模块 | 产物类型 | 说明 |
|---|---|---|
| `:common` | JAR | 平台无关核心库 |
| `:adapter:onebot` | JAR | OneBot 适配器 |
| `:adapter:miraimc` | JAR | MiraiMC 适配器 |
| `:bukkit` | Shadow JAR | 最终插件，包含 common + adapter + bukkit 平台实现 |

## 依赖版本管理

所有依赖版本集中管理在 `gradle/libs.versions.toml`：

```toml
[versions]
kotlin = "2.4.0"
gson = "2.11.0"
configurateVersion = "4.2.0"
sqlite = "3.49.0.0"
mysql = "8.3.0"
# ...
```

各模块 `build.gradle.kts` 应引用 `libs.xxx`，避免硬编码版本号。

运行时库版本常量 `RuntimeLibraryVersions.kt` 是 `libs.versions.toml` 的镜像，修改时必须同步。

## Shadow 打包

`:bukkit` 模块使用 Shadow 插件将 `:common`、`:adapter:onebot`、`:adapter:miraimc` 及第三方依赖打包成单一 JAR，并通过 relocation 避免类冲突。

当前 relocation 包名已统一为 `me.regadpole.plumbot.lib.*` 前缀（`BukkitDependencyLoader` 中 taboolib-database / guava / AOneBot 依赖通过 Libby 运行时下载并 relocation，`bukkit/build.gradle.kts` 的 shadowJar 中 libby / commodore / adventure 也使用同一前缀）。注意：早期版本中使用 `top.alazeprt.*` 历史路径的运行时会与新版本不兼容，属于 BREAKING 变更。

## 发布流程

1. 确保所有修改通过完整构建验证。
2. 检查 `libs.versions.toml` 中的版本号是否需要更新。
3. 构建 `:bukkit:shadowJar`。
4. 取产物 `bukkit/build/libs/PlumBot-Bukkit.jar` 发布。
5. 同步更新 `coding-docs/architecture/adapter-platform-compatibility.md` 和 `bot-capabilities.md`（如有 adapter 变更）。

## 验证清单

- [ ] `common`、`:adapter:onebot`、`:adapter:miraimc`、`bukkit` 均编译通过。
- [ ] `:bukkit:shadowJar` 成功生成产物。
- [ ] adapter 源码中无 `wait()` / `notify()`。
- [ ] `common` 模块不依赖 AOneBot / MiraiMC / Bukkit 类。
- [ ] 版本号在 `libs.versions.toml` 中统一管理。
