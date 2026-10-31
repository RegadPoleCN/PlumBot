# PlumBot 构建与发布

改构建、跑 CI、发版本以 `buildSrc/`、根 `build.gradle.kts` 和 `.github/workflows/` 为准；本文与之冲突时看代码。

## buildSrc 约定插件

### `kotlin-jvm`

所有子模块（`:api`、`:render`、`:common`、`:adapter:*`、`:bukkit`、`:velocity`、`:hytale`）都应用：

- Kotlin JVM + `jvmToolchain(25)`：产物 class 版本 69，JDK 21 及以下加载不了。
- `Test` 走 `JUnitPlatform`，`java.io.tmpdir` 指到 `<module>/build/tmp/test-jvm`，测试结束递归删除。
- `generateBuildConstants` 按需生成 `me.regadpole.plumbot.internal.BuildConstants`（`VERSION`、`NAME`）。模块要在自己的 `build.gradle.kts` 里声明 `extra["plumbotBuildConstants"] = true` 才会生成（目前只有 `:velocity` 需要，把版本喂给 `@Plugin`）。全模块都生成会让同名类在 shading 时相撞。

### `platform-distribution`

只给 `:bukkit`、`:velocity`、`:hytale`：

- 应用 `com.gradleup.shadow`，`shadowJar` 挂进 `assemble`，`archiveClassifier` 置空（产物不带 `-all`）。
- 重定位到 `me.regadpole.plumbot.lib`，共 6 条：Aedile（`com.sksamuel.aedile`）、Caffeine（`com.github.benmanes.caffeine`）、HikariCP（`com.zaxxer.hikari`）、Libby（`com.alessiodp.libby`）、Commodore（`me.lucko.commodore`）、bStats（`org.bstats`）。
- **Adventure 不在重定位列表里，是被显式排除的**：`net.kyori.*` 既不打包也不改包名。Paper/Leaf 与 Velocity 宿主自带 kyori，插件直接用宿主类——打进去的 `Component` 和宿主的不是同一个类；Spigot/Hytale 这类不带 kyori 的宿主由 `KyoriSupport` 判定后交给 Libby 按原包名装入。
- `minimize` 排除 `org.bstats:*`；`dependencies` 排除 `com.mojang:brigadier`（宿主自带）；剔除 `META-INF/*.SF`、`*.DSA`、`*.RSA`（shading 后签名失效，留着会让宿主拒绝加载）。

## dist：收集发布产物

根工程的 `Copy` 任务（`group = distribution`，`destinationDir = dist`）对每个应用了 shadow 的平台模块 `from(shadowJar)`，`./gradlew dist` 后得到 `dist/PlumBot-Bukkit.jar`、`dist/PlumBot-Velocity.jar`、`dist/PlumBot-Hytale.jar`。

文件名不含版本（三个模块都 `archiveVersion.set("")`），所以 `releases/latest/download/PlumBot-Bukkit.jar` 这类链接永远有效；代价是版本只能进产物里查，发布流水线因此会解包断言。

## 校验任务

`check` 聚合的校验（`build.yml` 与 `release.yml` 跑的也是它）：

| 任务 | 归属 | 校验什么 |
|---|---|---|
| `test` | 各模块 | 单元测试 |
| `:api:checkApiContract` | `:api` | `:api` 每个文件都标了公开 API 注解 |
| `checkRenderBoundaries` | 根 | 模块边界不越级 |
| `checkRenderLayout` | 根 | render 包布局/文件大小 |
| `checkRenderProtocol` | 根 | 渲染协议清单与代码、配置一致 |
| `checkRuntimeLibraries` | 根 | `compileOnly` 依赖运行期都装得上 |
| `checkSingleDefinitions` | 根 | 只该有一份的定义没被复制 |

根工程的五个校验通过 `subprojects { plugins.withId("base") { tasks.named("check") { dependsOn(...) } } }` 挂到每个模块的 `check` 上，`:api:checkApiContract` 由 `:api` 自己挂，`checkRelocation` 不在链上（见下）。

**`checkApiContract`**：按**文件**判定，不是按类或按包——`:api` 下每个 `.kt` 文件剥离注释后必须出现 `@PublicApi` / `@StableApi` / `@ExperimentalApi` 之一；两处硬编码豁免（`fun interface PlatformTaskHandle`、`val Int.ticks`）；不校验包名，也不要求必须用 `@StableApi`。先剥注释是为了防止"注解只写在注释里"骗过校验。失败原因基本只有一个：新增文件忘打注解。改检测逻辑必须跑负例自检 `./gradlew :api:checkApiContract -PapiContractSelfTest=true`（4 个负例 + 4 个正例）。

**`checkRenderBoundaries`**：三条规则——适配层不得出现任何 `import me.regadpole.plumbot.render`；`:render` 的内部类型不得被跨模块引用，**注释里提到也算**；`:api` 不得依赖渲染实现。内部类型清单分两层：9 个门户核心类型（`TextToImg`、`ThemeManager`、`AwtImageRenderer`、`PlaywrightImageRenderer`、`PayloadGovernor`、`RenderSecurity`、`AvatarComposer`、`ImageRenderManager`、`ImageRenderer`）是白纸黑字的硬约束，不随重构漂移；其余清单**从源码派生**——扫描 `render/src/main/kotlin` 里所有 `internal` 顶层声明（`class` / `interface` / `object` / `typealias`），再减去其它模块的同名声明（含它们从第三方库导入的同名类型，无法归因即不纳入），派生只判代码。一个内部类型都没派生出来时直接判失败（防空转）。失败原因：图省事直接 `new ThemeManager`，绕过门户。

**`checkRenderLayout`**：三条布局规则——单文件**代码行**（非空、非注释）不得超过 600；同一个包（目录）里的 `.kt` 文件不得超过 8 个；直接位于 `render` 包根的文件必须带 `@PublicApi` / `@StableApi` / `@ExperimentalApi` 之一（根包即公开面，实现一律下沉到领域子包）。当前 49 个源文件、13 个包，最大文件 518 代码行，输出形如「checkRenderLayout: 49 个源文件、13 个包，布局校验通过」。超长文件的遗留豁免集当前是空集：新增长文件必须真拆，不允许往豁免里加名字。失败原因只有两个方向：往一个文件里堆职责（回到千行文件）、往一个包里平铺（回到「一个包塞实现」）。

**`checkRenderProtocol`**：`render/src/main/resources/render-protocol.json` 是单一真源。校验 route 集合与 `RenderRoute` 枚举双向一致；每条 route 必须声明非空 `featureGate`、`defaultEngine` 和 `payload`，且 `payload` 严格是 `{ text: string, title: string?, data: object }`；文本样式还要在协议清单、`config.yml`、AWT 引擎默认值、运行时载体之间四方一致。前端源码不在构建期读——协议号与 route 集合靠运行时 manifest 握手（对不上报 `PW_TEMPLATE_INVALID`）。渲染细则见 [render.md](./render.md)。

**`checkRuntimeLibraries`**：读 `common/src/main/kotlin/me/regadpole/plumbot/internal/RuntimeLibraries.kt` 里的 groupId/artifactId，与所有子模块的 `compileOnly` 声明做差集，差额必须落在带理由的豁免表里（spigot-api、`com.hypixel.hytale:Server`、velocity-api、MiraiMC 的三个坐标、按需装载的 playwright）。解析失败或一个 `compileOnly` 都没读到时直接失败（防空转）。失败原因：加了 `compileOnly` 依赖却没进 `RuntimeLibraries`——编译期毫无提示，运行期 `NoClassDefFoundError`。

**`checkSingleDefinitions`**：扫描 `common/src/main/kotlin` 与 `api/src/main/kotlin`，要求玩家名校验正则 `[a-zA-Z0-9_.]{3,16}` 出现且仅出现一次（应在 `PlayerNameRules`）。失败原因：把规则又抄了一份——两份会各自演化，出现"群里能绑、走 API 绑不上"。目录缺失或一个文件都没扫到同样判失败。

**`checkRelocation`**：**不在 `check` 链上**（依赖 `dist` 产物）。断言三个包里不含 `top/`、不含 `net/kyori/`、也不含 `me/regadpole/plumbot/lib/net/kyori/`；`com/google/gson` 允许保留原前缀。`dist` 是空的会直接报"dist/ 下没有产物，请先运行 gradlew dist"。失败原因：某个依赖把 Adventure 传递带进了产物，典型症状是 Velocity 上 `/plumbot status` 抛 `NoClassDefFoundError`。

## 常用命令

```bash
./gradlew check                 # 测试 + 全部校验任务（CI 用的就是它）
./gradlew dist checkRelocation   # 出包并断言重定位（发布门禁）
./gradlew :common:test           # 单模块测试
./gradlew clean
```

> 旧文档写的 `./gradlew test check --offline` 与 CI 不符：CI **不用** `--offline`。本地缓存齐全时加 `--offline` 能让构建完全不联网，但新环境照抄会失败。

## 版本与发布

`gradle.properties` 的 `version` 是唯一真源；`-Pversion=x.y.z` 可在不改文件的情况下覆盖（流水线用它和 tag 对表）。注入位置：

| 产物清单 | 注入方式 |
|---|---|
| `bukkit/src/main/resources/plugin.yml` | `processResources` expand `version` |
| `hytale/src/main/resources/manifest.json` | `processResources` expand `"Version"` |
| Velocity | `:velocity` 没有 `resources`；`@Plugin(version = BuildConstants.VERSION)` 由 kapt 生成 `velocity-plugin.json` |

所以发布流水线只解包断言 Bukkit 的 `plugin.yml` 和 Hytale 的 `manifest.json`。

### 发布流水线（`release.yml`）

触发：推 `v*` tag，或手动 `workflow_dispatch` 填版本。每一步都是门禁：

1. 解析版本并校验 semver（`x.y.z` 或 `x.y.z-suffix`），非法直接 `::error`。
2. 断言 `gradle.properties` 的 `version` 和 tag 一致。
3. `./gradlew --no-daemon check`。
4. 四个负例自检：`-PrenderProtocolSelfTest=true`、`-PrenderBoundarySelfTest=true`、`-PapiContractSelfTest=true`、`-PruntimeLibrarySelfTest=true`。
5. `./gradlew --no-daemon -Pversion=$V dist checkRelocation`。
6. 解包断言两个清单里的版本号等于 `$V`。
7. 生成 `SHA256SUMS.txt`（三个 jar + `render-protocol.json`）。
8. 上传 artifact（保留 30 天），用 `softprops/action-gh-release` 发 Release；版本号含 `-` 自动标 prerelease。

`build.yml` 在 `v3` 分支和 PR 上跑 `./gradlew check` 与 `./gradlew dist checkRelocation`，同样出 `SHA256SUMS.txt` 和 `render-protocol.json`，artifact 保留 14 天。提交信息带 `[ci skip]` / `[skip ci]` / `***NO_CI***` 会跳过。

### 与 PlumBot-Web 的对接

前端工作区不在本仓库，由独立仓库发布两样东西：

| 产物 | 分发 | 插件侧消费点 |
|---|---|---|
| `plumbot-web-sdk` | npm 包 | 模板构建期依赖 |
| 官方模板（单文件 HTML） | PlumBot-Web 的 GitHub Release 资产 | `render.playwright.template_url` 默认指向 `releases/latest/download/index.html` |

模板 release **不要标 prerelease**：`releases/latest` 不指向 prerelease，标错会让默认 `template_url` 404，`engine: playwright` 下报 `PW_TEMPLATE_MISSING`。仓库发布的 `render-protocol.json` 是协议的权威快照，PlumBot-Web 靠人工 PR 同步它。
