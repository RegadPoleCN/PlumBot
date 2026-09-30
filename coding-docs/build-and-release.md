# PlumBot 构建与工程化发布规范

本文档面向所有参与编译、测试与 CI/CD 维护的开发者，详细阐述 PlumBot 的构建逻辑体系、多平台产物归集流水线与代码质量守护任务。

---

## 🛠️ 1. Gradle `buildSrc` 约定插件体系

为了消除各子模块 `build.gradle.kts` 中冗余重复的构建配置，项目在 `buildSrc/` 中抽象出了两组正交的 **Convention Plugins（预编译脚本插件）**：

### 1) `kotlin-jvm`（基础 JVM 约定插件）
* **定位**：供所有子模块（`:api`, `:common`, `:adapter:*`, `:bukkit`）应用。
* **主要职责**：
  - 自动引入 Kotlin JVM 插件；
  - 强制全局编译环境使用 **JDK 21 LTS** (`jvmToolchain(21)`)；
  - 配置全局 Java/Kotlin 编译任务的源码字符集为 `UTF-8`；
  - 配置基于 `JUnitPlatform` 的单测运行器，并开启直观的控制台测试状态日志。

### 2) `platform-distribution`（平台分发约定插件）
* **定位**：仅供**平台宿主分发模块**（当前为 `:bukkit`，未来为 `:velocity`, `:fabric` 等）应用。
* **主要职责**：
  - 自动引入 `com.gradleup.shadow` 插件并挂载至 `assemble` 依赖链；
  - 归一化最终产物命名格式（移除冗余的 `-all` 分类器后缀）；
  - **依赖重定向（Relocation 隔离）**：统一将容易与外界冲突的核心库重定向至 `me.regadpole.plumbot.lib.*`（涵盖 Aedile, Caffeine, HikariCP, Libby, Commodore, Adventure）；
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

## 🚀 4. 日常高频构建命令速查

```bash
# 1. 快速编译全模块（推荐日常编码中使用）
./gradlew compileKotlin compileTestKotlin

# 2. 离线快速执行全量测试与 API 契约检查
./gradlew test check checkApiContract --offline

# 3. 产生最终可发布的各平台 Jar 文件（输出到 dist/ 目录）
./gradlew dist --offline

# 4. 全量清理构建缓存与中间产物
./gradlew clean
```
