# PlumBot 构建与工程化发布规范

本文档面向所有参与编译、测试与 CI/CD 维护的开发者，详细阐述 PlumBot 的构建逻辑体系、多平台产物归集流水线与代码质量守护任务。

---

## 🛠️ 1. Gradle `buildSrc` 约定插件体系

为了消除各子模块 `build.gradle.kts` 中冗余重复的构建配置，项目在 `buildSrc/` 中抽象出了两组正交的 **Convention Plugins（预编译脚本插件）**：

### 1) `kotlin-jvm`（基础 JVM 约定插件）
* **定位**：供所有子模块（`:api`, `:render`, `:common`, `:adapter:*`, `:bukkit`, `:velocity`, `:hytale`）应用。
* **主要职责**：
  - 自动引入 Kotlin JVM 插件；
  - 强制编译工具链为 **JDK 25**（`jvmToolchain(25)`）—— 产物 class 文件版本为 **69**，**JDK 21 连产物都无法加载**，请勿按旧文档使用 21；
  - 配置基于 `JUnitPlatform` 的单测运行器，并开启直观的控制台测试状态日志；
  - 生成 `BuildConstants`（包名 `me.regadpole.plumbot.internal` 声明为任务输入，生成前清空输出目录，避免旧包名的 class 残留进产物）。

> 注：旧文档所称"配置全局源码字符集为 UTF-8"**并不存在**（构建脚本中没有任何 charset/encoding 配置），已删除该描述。

### 2) `platform-distribution`（平台分发约定插件）
* **定位**：仅供**平台宿主分发模块**（`:bukkit`, `:velocity`, `:hytale`）应用。
* **主要职责**：
  - 自动引入 `com.gradleup.shadow` 插件并挂载至 `assemble` 依赖链；
  - 归一化最终产物命名格式（移除冗余的 `-all` 分类器后缀）；
  - **依赖重定向（Relocation 隔离）**：由单一真源 `relocateRoot = "me.regadpole.plumbot.lib"` 派生全部规则 ——
    Aedile、Caffeine、HikariCP、Libby、Commodore、Adventure、**bStats**（共 7 条）；
    新增库时只改这一处，运行期 Libby 的 relocate 目标必须与它逐字一致；
  - `minimize` 排除 `org.bstats:*`，并排除 `com.mojang:brigadier`（宿主自带）；
  - 自动剔除发布包中无效的数字签名与元数据文件（`META-INF/*.SF`, `*.DSA`, `*.RSA`）。

---

## 📦 2. 统一产物收集流水线 (`:dist` 任务)

在支持多平台的架构下，若各模块产物分散在各自子目录（如 `bukkit/build/libs/`），CI 脚本与发布流程将极易碎片化。为此根工程声明了统一聚合任务：

### 运行方式
```bash
# 执行多平台发布包统一构建与收集
./gradlew dist
```

### 执行逻辑
1. 遍历所有应用了 ShadowJar 插件的平台子模块；
2. 触发对应的 `shadowJar` 任务生成生产发布包；
3. 将最终发布包自动复制到项目根目录下的 **`dist/`** 目录：
   ```
   PlumBot/
   └── dist/
       ├── PlumBot-Bukkit.jar     # 用于 Paper/Spigot 实体后端子服
       ├── PlumBot-Velocity.jar   # 用于 Velocity 跨服代理端
       └── PlumBot-Hytale.jar     # 用于 Hytale 官方实体服务端
   ```

---

## 🛡️ 3. API 契约静态守护机制 (`:api:checkApiContract`)

为了避免开发人员在维护中无意中将实现类暴露到 `:api` 模块，或者意外修改标记了 `@StableApi` 的契约签名，项目内置了契约校验任务：

```bash
# 执行公开 API 契约静态校验
./gradlew :api:checkApiContract
```

### 规则判定矩阵
1. **公开 API 纯粹性**：`:api` 模块下的所有公共类型必须且只能位于 `me.regadpole.plumbot.api.*` 包命名空间下。
2. **注解标注完整性**：公共类型必须显式打上 `@StableApi` 或 `@PublicApi` 注解。
3. **零内部包侵入**：严禁在 `:api` 模块中导入或暴露 `me.regadpole.plumbot.internal.*` 内部包下的符号。

如果校验不通过，构建将直接中断并给出具体的违规类名与修复指引。

---

## 🧱 3.1 渲染子系统边界与协议守护（`checkRenderBoundaries` / `checkRenderProtocol`）

渲染子系统采用**统一门户**：其他模块只能经 `RenderPortal` 对话，不得引用 `:render` 的内部类型。该纪律由两个机械校验任务保证 —— 二者**已随 `check` 一起执行**（挂在各模块 `check` 上），因此 `./gradlew check` 即包含它们：

```bash
./gradlew check                       # 等价于 test + checkApiContract + 下列两个检查
./gradlew checkRenderBoundaries checkRenderProtocol   # 单独运行
```

**`checkRenderBoundaries` 规则矩阵**

1. **适配层零依赖**：`adapter/**` 不得出现任何 `me.regadpole.plumbot.render` 导入（出图统一由门户负责）。
2. **跨模块禁引用内部类型**：`:api` / `:common` / 各宿主模块不得引用 `TextToImg`、`ThemeManager`、各驱动、`ImageRenderManager` 等内部类型 —— 含**注释中的提及**。
3. **`:api` 不得依赖渲染实现**。
4. **公开发布面必须带契约注解**：`:render` 中任何非 `internal` 的顶层声明，其所在文件必须含 `@PublicApi` / `@StableApi`。

**`checkRenderProtocol` 规则矩阵**

1. `render/src/main/resources/render-protocol.json` 的 route 集合与 Kotlin `RenderRoute` 枚举**双向一致**；
2. 每条 route 均声明 `legacyGate` / `defaultEngine` / 非空 `payload`；
3. 若前端 SDK（`web-t/src/sdk/protocol.ts`）存在，其协议版本与 route 集合必须与清单一致。

> 详见 **[渲染子系统规范](./render.md)**。

---

## 🚀 4. 日常高频构建命令速查

```bash
# 1. 快速编译全模块（推荐日常编码中使用）
./gradlew compileKotlin compileTestKotlin

# 2. 离线执行全量测试与全部契约/边界检查（check 已包含三个检查任务）
./gradlew test check --offline

# 2b. 契约检测器自检（负例回归，改动 checkApiContract 后必须跑）
./gradlew :api:checkApiContract -PapiContractSelfTest=true

# 3. 产生最终可发布的各平台 Jar 文件（输出到 dist/ 目录）
./gradlew dist --offline

# 4. 全量清理构建缓存与中间产物
./gradlew clean
```
