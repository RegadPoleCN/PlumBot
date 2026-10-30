# Playwright 渲染引擎运维手册

面向服主与二次分发者。渲染架构、协议与安全基线见 [render.md](render.md)；本文件只讲**如何装好、出错怎么修**，并且是 `PW_` 错误码的唯一真源。

## 1. 默认行为

- 默认引擎 `awt`：不下载、不装载、不实例化、不加载任何 `com.microsoft.playwright.*` 类。
- 切到 `playwright` 后浏览器只做**检测**（`custom_chrome_path` → `browser_channel` → 系统浏览器 → `browsers_path` 托管目录），四条链都没命中才返回 `PW_BROWSER_MISSING` 并附手动命令；**绝不在渲染线程里等下载**。
- 运行库装载走 `PlaywrightInstallGate`：渲染线程发现 `com.microsoft.playwright.Playwright` 不在类路径时，只「按一下按钮」非阻塞调度一次异步装载，随即抛 `PW_DEPENDENCY_MISSING`，本次交给 AWT 兜底。单飞（同一时刻只许一次装载）；成功后不再触发；失败冷却 60 秒，冷却期内不摸网络、降级日志降为 INFO。主线程守卫在装载器一侧：Bukkit 拒绝从主线程下载，Hytale 拒绝从世界 tick 线程下载，Velocity 没有 tick 线程概念。
- `render.playwright.fallback_to_awt`（默认 `true`）：失败先出 AWT 图并写 ERROR（同引擎 + 同路由 5 分钟去重），计入 `RenderMetrics.FALLBACK`；为 `false` 时直接发纯文本。
- **doctor / install 是例外**：走同一道闸门的阻塞入口 `awaitLoad()`，先在异步线程触发 Libby 装载（首次下载 `driver-bundle` 约 190 MB），否则「查 jar」只会得到一条虚假的 `PW_DEPENDENCY_MISSING`。冷却未到期时直接报出剩余秒数，不假装成功。

## 2. 安装

以游戏内命令为主路径，配置键与默认值见 `config.yml` 的 `render.playwright` 段：

| 场景 | 做法 | 说明 |
|---|---|---|
| 首次安装 / 修复 | `/plumbot render install` | 装到 `browsers_path`，受 `render.playwright.install_timeout_seconds`（默认 600）限制 |
| 强制重装 | `/plumbot render install --force` | 已有目录也重下，用于版本损坏 |
| 用系统浏览器 | 填 `custom_chrome_path`，或 `browser_channel: chrome / msedge` | 不下载 Chromium；版本过旧仍可能启动失败 |

**三件套缺一不可**：`driver`（装载器接口，不到 10 KB）→ `driver-bundle`（原生 node 与 `cli.js`，约 190 MB）→ `playwright`（API 与 `com.microsoft.playwright.CLI` 入口，约 0.6 MB）。体积与版本以 `RuntimeLibraries.PLAYWRIGHT` / `RuntimeLibraryVersions.PLAYWRIGHT`（`1.49.0`）为准。三者都按 `transitive = false` 装载：`playwright` 的 POM 把 JUnit 列为 compile 依赖，打开传递解析会把测试框架装进服务端。

### 备用：手动 CLI

三个 jar 缺一不可，缺 `driver` 会在启动时 `NoClassDefFoundError`：

```powershell
$base = "<插件数据目录>\libraries\com\microsoft\playwright"
$cp = "$base\playwright\1.49.0\playwright-1.49.0.jar;$base\driver\1.49.0\driver-1.49.0.jar;$base\driver-bundle\1.49.0\driver-bundle-1.49.0.jar"
$env:PLAYWRIGHT_BROWSERS_PATH="<插件数据目录>\playwright-browsers"
$env:PLAYWRIGHT_DOWNLOAD_HOST="https://cdn.npmmirror.com/binaries/playwright"
java -cp $cp com.microsoft.playwright.CLI install chromium --no-shell
```

Linux/macOS 把 `;` 换成 `:`；Linux 自动追加 `--with-deps`，需要 root 或预装系统依赖。没有插件环境时可用 npm 装同版本：`npx --yes playwright@1.49.0 install chromium --no-shell`（先设上面两个环境变量）。`install_shell` 取 `no-shell`（默认）/ `only-shell` / `full`，分别对应 CLI 的 `--no-shell` / `--only-shell` / 无参数。

> 镜像 `cdn.npmmirror.com` 不提供 headless-shell，`only-shell` 在镜像上会 404；只有清空 `download_host` 走官方 CDN 时才用 `only-shell`。
>
> Playwright 版本升级会 GC 旧浏览器：升级插件后首次出图报 `PW_BROWSER_MISSING` 属预期行为，重新 install 即可。

## 3. 诊断：`/plumbot render doctor`

- 就绪状态、`PW_` 错误码与摘要，以及当前生效的选项：`browsers_path`（绝对路径）、`download_host`（空则显示「官方 CDN」）、`install_shell`、`timeout`，每项只打印一次；
- 详情：`driver-bundle=`、`playwright.jar=`、`system-chromium=`、`managed-chromium=`、`decision`、`library=`（装载失败的具体构件与异常，如 `driver-bundle: 404 Not Found`）；
- 可整段复制的手动安装命令。

`/plumbot render install` 同样先装载：三件套仍缺失时直接返回 `PW_DEPENDENCY_MISSING` 并附手动命令，不会去下载浏览器。

## 4. 错误码矩阵

代码里共 11 个码（`PlaywrightErrorCodes`），不存在其他 `PW_` 值。

| 错误码 | 阶段 | 常见原因 | 修复 |
|---|---|---|---|
| `PW_CONFIG_INVALID` | config | `custom_chrome_path` 不存在、与 `browser_channel` 冲突、非法 `install_shell`、`browsers_path` 为空 | 改正配置或清空冲突项 |
| `PW_DEPENDENCY_MISSING` | dependency | Libby 未装载 / 下载失败；`libraries/` 下缺 driver、driver-bundle 或 playwright；解包 driver-bundle 失败 | 先 `/plumbot render doctor` 看 `library=`；检查 `libraries.use_aliyun_mirror`、`libraries.custom_repositories` 与网络 |
| `PW_BROWSER_MISSING` | browser | 四条检测链都未命中，或 `browsers_path` 下没有已安装版本 | `/plumbot render install`；或填 `custom_chrome_path` / `browser_channel` |
| `PW_DRIVER_START_FAILED` | driver | `Playwright.create()` 失败：driver 资源缺失、临时目录不可写可执行、TCCL 仍是服务端加载器（`DriverJar` 按 TCCL 找 `driver/` 资源） | 检查 `driver-bundle` 与 `java.io.tmpdir` 权限、重启服；二次开发不要改渲染线程模型（插件在 `Playwright.create()` 前后切 TCCL 并还原） |
| `PW_LAUNCH_FAILED` | launch / context | 浏览器版本过旧、缺系统依赖、可执行文件不是 Chrome、容器沙箱限制、`scale_factor` 过大 | 升级/更换浏览器；容器按 §5 显式关沙箱；看 detail |
| `PW_TEMPLATE_MISSING` | template | 候选链（`template_url` / `template_urls` + 加速站）全失败、`dev_web_dir` 无 `index.html`、入口为空 | 看 WARN 里逐条原因与手动命令；部署 HTTPS 单文件模板或改用本地目录 |
| `PW_TEMPLATE_INVALID` | template / handshake | 单文件损坏、协议号不符、`__PLUMBOT_MANIFEST__` 缺 route、`#render-target` 未渲染、入口无法转成浏览器安全 URL | 重新构建模板并确认协议号 1 |
| `PW_NAVIGATION_FAILED` | navigate | `file://` 越权、页面主文档加载失败 | 确认模板在主题目录白名单内；资源域名加进 `render.allowed_hosts` |
| `PW_SCREENSHOT_FAILED` | activate / settle / screenshot | 页面在后台标签页、READY 之后持续回流、`#render-target` 不可见或尺寸为 0、截图返回空字节 | 见 §6；detail 里带现场 |
| `PW_RENDER_TIMEOUT` | pool | 页面池等待超时（单页池 + 预算内没等到） | 调大 `render_timeout_ms`，或降低并发、改用 awt |
| `PW_INSTALL_FAILED` | install | 网络、镜像、权限、解包失败、安装超时 | 看输出尾部；重试、换 `download_host`，或用 §2 的备用命令 |

## 5. 沙箱与容器

- 默认**开启** Chromium 沙箱（不传 `--no-sandbox`）；报 `SUID sandbox`、`namespace`、`clone`、`No usable sandbox` 时只输出针对性提示，**不会自动关沙箱**。
- 确需关闭时显式设 `render.playwright.disable_sandbox: true`，启动时打 WARN：模板必须可信，隔离由容器负责。
- 更推荐 `custom_chrome_path` 指向宿主已配好的浏览器，或给容器补 `CAP_SYS_ADMIN` / user namespace；Linux 启动即失败多半是系统库缺失（`--with-deps` 未执行或无 root），用安装器或按发行版补依赖。

## 6. 截图失败的两个决定性判据

- **detail 里 `tab=hidden`** → 页面在后台标签页。Chromium 后台页不产合成帧，`Page.captureScreenshot` 会一直等帧到超时；`tab=visible` 才该怀疑 READY 之后仍在回流（异步字体/图片解码），此时调大 `render_timeout_ms`。代码侧对应：页面池只放 1 页、截图前 `bringToFront()` 兜底；单页同时把渲染串行化，规避 Playwright API 非线程安全。
- **detail 是 `TimeoutError ... waiting for element to be stable`** → 不要用 `Locator.screenshot()`：它会先 `scrollIntoView` 再等元素连续两帧几何完全一致，页面有迟到字体/图片时必超时。现实现改为自己采样 `#render-target` 的 `getBoundingClientRect()`（`SETTLE_TIMEOUT_MS = 1500ms`、`SETTLE_INTERVAL_MS = 50ms`、连续两次一致即停），再用 `page.screenshot(clip)` 截该矩形并 `setAnimations(DISABLED)`；失败现场含 box、scrollY、viewport、fonts、animations、display、visibility、opacity、tab。

## 7. 常见故障速查

| 现象 | 判断 | 处理 |
|---|---|---|
| 有 AWT 图，同时有 `PW_` ERROR | `fallback_to_awt=true` 正在兜底 | 修 Playwright 根因；不想出 AWT 图就设 false |
| 只有文字没有图 | `render.outputs.<功能>` 为 false、引擎 `none`，或已降级 | 看 `/plumbot render doctor`、`/plumbot render stats` 与日志 |
| 升级插件后全部失败 | 浏览器版本目录被 GC | `/plumbot render install` |
| 模板 404 或候选全失败 | 默认模板仓库未发布，或官方直链与加速站同时不可达 | 部署自己的单文件模板并改 `template_url` / `template_urls`；WARN 里逐条列了失败原因 |
| doctor 报「driver-bundle 未找到」但 `libraries/` 里有 jar | 装载从未触发（或仍在 60s 冷却），或 ClassLoader 看不到 | doctor/install 会先装载；仍失败就看 `library=` 行的构件名与异常 |
| 首次出图长时间无响应 | 渲染线程触发了 Playwright 自带的浏览器下载 | 本版本强制 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`（在 `Playwright.create()` 之前注入）；仍出现就检查是否有旧版驱动或自定义启动参数覆盖 |

## 8. 验收清单

- [ ] `engine=awt` 时无 Playwright 类装载、无浏览器进程、无相关网络请求；
- [ ] 未安装浏览器时立即报 `PW_BROWSER_MISSING`，不阻塞渲染线程；
- [ ] `/plumbot render install` 后同一 route 正常出图，`--force` 可重装；
- [ ] `fallback_to_awt=false` 时失败只发纯文本；
- [ ] 停服后无残留 Chromium 进程，日志无 `plugin classloader ... zip file error`（`RenderProvider.shutdownAndAwait` 已等待排空，默认 3s，超时只 WARN）；
- [ ] 出图链路无 `PW_SCREENSHOT_FAILED`（单页池 + `bringToFront`），且 `/plumbot render stats` 的「AWT 兜底」为 0、出图/降级/失败与日志降级记录口径一致，`/plumbot render reset` 清零后仍能继续出图；
- [ ] 容器内显式关沙箱时有 WARN，且确认模板来源可信。
