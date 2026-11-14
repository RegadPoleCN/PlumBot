// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.PlaywrightErrorCodes
import me.regadpole.plumbot.render.PlaywrightInstallOptions
import me.regadpole.plumbot.render.PlaywrightRenderException
import me.regadpole.plumbot.render.engine.playwright.startup.EngineStartup
import me.regadpole.plumbot.render.install.BrowserDecision
import me.regadpole.plumbot.render.install.PlaywrightInstallGate
import me.regadpole.plumbot.render.support.RenderSecurity
import me.regadpole.plumbot.render.theme.ThemeManager

/**
 * Playwright 引擎的启动/关停与原生资源生命周期。
 *
 * 硬约束：
 * - [ensureStarted] 在 `Playwright.create()` 之前固定注入
 *   `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`（见 [EngineStartup.playwrightEnvironment]）：
 *   Playwright Java 1.49 的 DriverJar 会在 create() 内无条件执行 node cli.js install
 *   （全量 chromium/firefox/webkit，最长 10 分钟），是"首次渲染卡住、超时后整引擎不可用"的根因；
 * - 浏览器只来自 [EngineStartup.resolveBrowserDecision] 的判定：custom_chrome_path、品牌 channel、
 *   系统浏览器、playwright-browsers 下的已安装版本。没有第五种"隐式下载"；
 * - 启动/创建上下文/注册拦截规则的任一阶段失败都抛
 *   [me.regadpole.plumbot.render.PlaywrightRenderException]（由 [FailureMapper] 构造）；
 * - 调用 `create()` 时把 TCCL 切到插件类加载器，规避 DriverJar 用 TCCL 查找 driver/ 资源
 *   在 Libby 注入场景下失败。
 *
 * 页面池由 [PagePool] 负责，渲染流程由 [PlaywrightImageRenderer] 负责；启动配置解析由
 * [EngineStartup] 负责。本类只管"引擎活没活、什么时候死"，因此页面补种/回收都通过传入的
 * [pagePool] 编排。
 */
internal class EngineLifecycle(
    private val context: PlatformContext,
    private val installGate: PlaywrightInstallGate?,
    private val pagePool: PagePool,
    private val failureMapper: FailureMapper,
    private val startup: EngineStartup,
    /** 主题管理器可被门户重载替换，因此不能是构造期固化的引用。 */
    private val themeManager: () -> ThemeManager,
    /** 关停时的附加动作（例如清空 manifest 学到的 routes），先于资源回收执行。 */
    private val onShutdown: () -> Unit = {}
) {

    /**
     * 引擎状态机：IDLE（未启动/已回收）→ STARTING（启动中）→ STARTED。
     *
     * 取代旧的单布尔 isInitialized：启动窗口（STARTING）必须对关闭方可见，否则
     * shutdown 与正在进行的启动交错时会漏掉对刚启动完成引擎的回收（复活泄漏）。
     */
    private enum class EngineState { IDLE, STARTING, STARTED }

    @Volatile
    private var state: EngineState = EngineState.IDLE

    private val shutdownHookRegistered = AtomicBoolean(false)

    /** JVM 关闭钩子（用于在插件禁用时主动摘除，见 [removeShutdownHookIfNeeded]）。 */
    @Volatile
    private var shutdownHook: Thread? = null

    /**
     * 是否是**我们自己**在关浏览器（shutdown / reload 回收 / 关服钩子）。
     *
     * `onDisconnected` 对主动 close 和进程崩溃给出同一个回调，没有这个标记时
     * 每次 reload 与关服都会打一条"底层 Chromium 意外退出"的假故障。
     */
    @Volatile
    private var closingInternally = false

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var browserContext: BrowserContext? = null

    /** 当前浏览器上下文；页面池补页时读取（null = 引擎已关停，补页直接放弃）。 */
    val currentBrowserContext: BrowserContext?
        get() = browserContext

    @Synchronized
    fun ensureStarted() {
        if (state == EngineState.STARTED) return

        // DriverJar.getDriverResourceURI() 用的是 Thread.currentThread().contextClassLoader。
        // Libby 把 playwright 注入插件加载器后，工作线程的 TCCL 可能仍是服务端加载器，
        // 这里显式切到本类（插件）加载器，create() 结束后恢复。
        val previousTccl = Thread.currentThread().contextClassLoader
        val ownLoader = EngineLifecycle::class.java.classLoader
        if (ownLoader != null) {
            Thread.currentThread().contextClassLoader = ownLoader
        }
        try {
            // 进入启动窗口：shutdown 与 onDisconnected 都按状态判断，启动完成后
            // 要么被正常接管（STARTED），要么被关闭方回收，不再出现无人认领的引擎。
            state = EngineState.STARTING
            startEngine()
        } catch (e: Throwable) {
            // 启动失败回到 IDLE 允许后续重试；STARTING 不能停留。
            state = EngineState.IDLE
            throw e
        } finally {
            // 无条件恢复：previousTccl 为 null（平台线程本就没设 TCCL）时也要清掉
            // 本次注入的 ownLoader，否则插件类加载器会滞留在平台线程上（TCCL 泄漏）。
            Thread.currentThread().contextClassLoader = previousTccl
        }
    }

    /**
     * 关闭引擎。幂等；重量级清理的线程策略由调用方决定。
     *
     * 与 [ensureStarted] 共用同一把监视器锁：正在进行的启动会先完成，随后的关闭
     * 必然看到 STARTED 并回收资源。旧实现的 CAS 失败早退会让启动完成后的引擎
     * 既无人拥有也无人回收（复活泄漏）；意外断连后残留的关服钩子也在此一并摘除。
     */
    @Synchronized
    fun shutdown() {
        // 先标记"主动关闭"，再改状态：onDisconnected 立刻据此把这次断连判为预期内。
        closingInternally = true
        onShutdown()
        if (state != EngineState.STARTED) {
            // 未启动 / 已被意外回收 —— 没有已发布资源可回收，只摘关服钩子。
            // （STARTING 期间不会走到这里：本方法与 ensureStarted 同锁，启动先完成。）
            removeShutdownHookIfNeeded()
            return
        }
        state = EngineState.IDLE
        releaseAll()
        removeShutdownHookIfNeeded()
    }

    /** 运行期是否确实能加载到 Playwright（决定 `isSupported`）。 */
    fun isPlaywrightPresent(): Boolean = runCatching {
        val loader = EngineLifecycle::class.java.classLoader
        if (loader != null) {
            Class.forName("com.microsoft.playwright.Playwright", false, loader)
        } else {
            Class.forName("com.microsoft.playwright.Playwright")
        }
    }.isSuccess

    private fun startEngine() {
        if (!isPlaywrightPresent()) {
            // **绝不在渲染线程内联下载**：这里只"按一下"装载门就立刻失败，
            // 真正的下载在平台异步泳道上进行，完成后下一次出图自动改用高清引擎。
            val requested = installGate?.startOnce("render") == true
            val stateText = if (requested) {
                "已按需在后台开始装载，本次先用 AWT 兜底，装载完成后自动改用 Playwright 引擎。"
            } else {
                "装载未在本次触发（已就绪 / 正在装载 / 失败冷却中），执行 /plumbot render doctor 查看原因。"
            }
            val options = startup.installOptions()
            throw failureMapper.dependencyMissing(
                "Playwright 运行库尚未装载：com.microsoft.playwright.Playwright 不在类路径。$stateText",
                startup.manualInstallCommand(options)
            )
        }

        val options = startup.installOptions()
        val decision = EngineStartup.resolveBrowserDecision(options)
        val env = EngineStartup.playwrightEnvironment(options)
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 启用 Playwright 引擎，浏览器来源：" + EngineStartup.describeDecision(decision)
        )

        val pw = createDriver(env)

        var launchedBrowser: Browser? = null
        var createdContext: BrowserContext? = null
        try {
            val sandboxDisabled = context.config.getBoolean("render", "playwright", "disable_sandbox")
            if (sandboxDisabled) {
                context.log(LogLevel.WARN, PlaywrightSandbox.disabledWarning())
            }

            val launchOptions = buildLaunchOptions(decision, sandboxDisabled, options)
            val browserInstance = launchBrowser(pw, launchOptions, sandboxDisabled)
            launchedBrowser = browserInstance
            browserInstance.onDisconnected { onDisconnected(browserInstance) }

            val browserCtx = createContext(browserInstance)
            createdContext = browserCtx
            installRequestGuard(browserCtx)
            pagePool.seed(browserCtx)

            // 发布前校验：启动窗口内浏览器崩溃时，onDisconnected 因 STARTING 不做回收，
            // 这里必须兜底，否则会把已死的浏览器发布成已启动引擎（复活泄漏）。
            if (!browserInstance.isConnected) {
                throw PlaywrightRenderException(
                    code = PlaywrightErrorCodes.LAUNCH_FAILED,
                    stage = "launch",
                    detail = "浏览器在启动完成前断开连接（启动窗口内崩溃），已中止发布。",
                    hint = "检查系统浏览器路径/版本是否有效，或执行 /plumbot render install 安装内置 Chromium。",
                    command = null,
                    route = null,
                    cause = null
                )
            }

            playwright = pw
            browser = browserInstance
            browserContext = browserCtx

            registerShutdownHook()
            // 新引擎就绪：复位"主动关闭"标记，让后续真实断连仍然报警。
            closingInternally = false
            state = EngineState.STARTED
            context.log(LogLevel.INFO, "[PlumBot-Render] Playwright 高清渲染引擎就绪！")
        } catch (e: Throwable) {
            runCatching { createdContext?.close() }
            runCatching { launchedBrowser?.close() }
            runCatching { pw.close() }
            throw e
        }
    }

    private fun createDriver(env: Map<String, String>): Playwright = try {
        Playwright.create(Playwright.CreateOptions().setEnv(env))
    } catch (e: Throwable) {
        throw failureMapper.driverStartFailed(
            "Playwright.create() 失败：" + (e.message ?: e::class.java.simpleName),
            e
        )
    }

    private fun buildLaunchOptions(
        decision: BrowserDecision,
        sandboxDisabled: Boolean,
        options: PlaywrightInstallOptions
    ): BrowserType.LaunchOptions {
        val launchOptions = BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(
                listOf(
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-crash-reporter",
                    "--disable-breakpad",
                    "--no-crash-upload",
                    "--mute-audio"
                ) + PlaywrightSandbox.args(sandboxDisabled)
            )

        when (decision) {
            is BrowserDecision.Executable -> {
                launchOptions.setExecutablePath(Path.of(decision.path))
                context.log(LogLevel.INFO, "[PlumBot-Render] 使用浏览器可执行文件（" + decision.source + "）：" + decision.path)
            }

            is BrowserDecision.Channel -> {
                launchOptions.setChannel(decision.channel)
                context.log(LogLevel.INFO, "[PlumBot-Render] 使用浏览器品牌通道：" + decision.channel)
            }

            is BrowserDecision.InvalidConfig -> failureMapper.configFailure(decision.detail)
            is BrowserDecision.Missing -> failureMapper.browserMissing(
                options,
                decision.detail,
                startup.manualInstallCommand(options)
            )
        }
        return launchOptions
    }

    private fun launchBrowser(
        pw: Playwright,
        launchOptions: BrowserType.LaunchOptions,
        sandboxDisabled: Boolean
    ): Browser = try {
        pw.chromium().launch(launchOptions)
    } catch (e: Throwable) {
        if (!sandboxDisabled && PlaywrightSandbox.looksLikeSandboxFailure(e.message)) {
            context.log(LogLevel.ERROR, PlaywrightSandbox.sandboxFailureHint())
        }
        throw failureMapper.launchFailed(
            "Chromium 启动失败：" + (e.message ?: e::class.java.simpleName),
            e
        )
    }

    private fun createContext(browserInstance: Browser): BrowserContext {
        val ctxOptions = Browser.NewContextOptions()
            .setDeviceScaleFactor(startup.globalScaleFactor())
            .setBypassCSP(true)

        return try {
            browserInstance.newContext(ctxOptions)
        } catch (e: Throwable) {
            throw failureMapper.contextFailed(
                "创建浏览器上下文失败：" + (e.message ?: e::class.java.simpleName),
                e
            )
        }
    }

    /**
     * 请求放行判定。
     *
     * - file: 必须落在主题根目录内；
     * - http(s): 只按主机名白名单放行，不做 DNS 解析后的内网/保留地址判定；
     * - data/blob/about: 放行（服务端预取的头像以 data URL 交付）。
     */
    private fun isRequestAllowed(url: String, themeRoots: List<File>, allowedHosts: Set<String>): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false
        return when (scheme) {
            "file" -> themeRoots.any { RenderSecurity.isFileUrlWithinRoot(url, it) }
            "data", "blob", "about" -> true
            "http", "https" -> {
                val host = RenderSecurity.hostOf(url)
                host != null && RenderSecurity.isHostAllowed(host, allowedHosts)
            }
            else -> false
        }
    }

    /** 出口白名单：来自配置，缺省回落到内置列表。 */
    private fun resolveAllowedHosts(): Set<String> = RenderSecurity.allowedHostsFrom(context.config)

    private fun installRequestGuard(browserCtx: BrowserContext) {
        val themeRoots = themeManager().allowedFileRoots
        try {
            browserCtx.route("**/*") { route ->
                val request = route.request()
                val url = request.url()
                // 白名单每次请求时从配置现读：render.avatar.domains 不在引擎指纹里，
                // reload 不会重建浏览器；固化进闭包的话改配置必须重启进程才生效。
                if (isRequestAllowed(url, themeRoots, resolveAllowedHosts())) {
                    route.resume()
                } else {
                    if (request.resourceType() == "document") {
                        context.log(LogLevel.WARN, "[PlumBot-Render] 已拦截越权请求：" + url)
                    }
                    route.abort()
                }
            }
        } catch (e: Throwable) {
            throw failureMapper.requestGuardFailed(
                "注册请求拦截规则失败：" + (e.message ?: e::class.java.simpleName),
                e
            )
        }
    }

    private fun onDisconnected(browserInstance: Browser) {
        // 旧实例的迟到回调（重建后 browser 已指向新实例）直接忽略。
        if (browser !== browserInstance) return
        // 回调来自驱动线程，不取引擎监视器：releaseAll 的 closeWithin 正在等驱动退出，
        // 若这里再排队等监视器就会与 shutdown 互等死锁。改用状态判断：
        // - STARTING：启动窗口内的崩溃由 startEngine 发布前的 isConnected 守卫兜底；
        // - 非 STARTED：资源已由 shutdown()/releaseAll() 回收，这里不再重复回收。
        if (state != EngineState.STARTED) {
            if (closingInternally) {
                context.log(LogLevel.INFO, "[PlumBot-Render] Chromium 已按请求关闭（主动释放资源）。")
            }
            return
        }
        context.log(LogLevel.WARN, "[PlumBot-Render] 底层 Chromium 意外退出，回收资源并重置。")
        releaseAll()
        state = EngineState.IDLE
    }

    private fun registerShutdownHook() {
        if (!shutdownHookRegistered.compareAndSet(false, true)) return
        val hook = Thread({ shutdown() }, "PlumBot-Render-ShutdownHook")
        shutdownHook = hook
        runCatching { Runtime.getRuntime().addShutdownHook(hook) }
    }

    /**
     * 摘除本引擎注册的 JVM 关闭钩子（插件被禁用/重载时）。
     *
     * 必须摘：钩子持有引擎引用并会在 JVM 退出时调用 [shutdown]；插件类加载器一旦关闭，
     * 钩子再去 `PlaywrightImpl.close()` 就会抛
     * "The plugin classloader for PlumBot has thrown a zip file error."。
     * 关服时钩子自己正在执行，不能移除自身 —— 此时直接跳过。
     */
    private fun removeShutdownHookIfNeeded() {
        val hook = shutdownHook ?: return
        if (Thread.currentThread() === hook) return
        shutdownHook = null
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }

    /**
     * 回收全部原生资源（页面池 → 上下文 → 浏览器 → 驱动）。
     *
     * **先摘引用、再关闭**：并发渲染线程拿到 null 会直接降级为文本，
     * 而不是在已经关掉的浏览器上继续操作。每一步都在**独立守护线程**上执行并带超时：
     * `Browser.close()` 与 `PlaywrightImpl.close()` 会同步等待 node 驱动进程
     * （`ProcessImpl.waitFor()`），实测单次 2~3s，调用线程不该被拖住。
     */
    private fun releaseAll() {
        val pooledPages = pagePool.releaseAll()
        val closingContext = browserContext
        val closingBrowser = browser
        val closingPlaywright = playwright
        browserContext = null
        browser = null
        playwright = null

        closeWithin("pages", PAGE_CLOSE_TIMEOUT_MS) { pooledPages.forEach { runCatching { it.close() } } }
        closeWithin("browser-context", CONTEXT_CLOSE_TIMEOUT_MS) { closingContext?.close() }
        closeWithin("browser", BROWSER_CLOSE_TIMEOUT_MS) { closingBrowser?.close() }
        closeWithin("driver", DRIVER_CLOSE_TIMEOUT_MS) { closingPlaywright?.close() }
    }

    /**
     * 在一次性守护线程上执行一步 close，最多等 [timeoutMillis]。
     *
     * 复制 TCCL 是必须的：Playwright 的驱动类与回调用 TCCL 解析资源，在 Libby 注入
     * 场景下工作线程的 TCCL 可能不是插件加载器。
     */
    private fun closeWithin(label: String, timeoutMillis: Long, block: () -> Unit) {
        val tccl = Thread.currentThread().contextClassLoader
        val worker = Thread({
            runCatching { block() }.onFailure { t ->
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Render] 关闭 " + label + " 失败：" + (t.message ?: t.javaClass.simpleName)
                )
            }
        }, "PlumBot-Render-Close-" + label).apply {
            isDaemon = true
            if (tccl != null) contextClassLoader = tccl
        }
        worker.start()
        worker.join(timeoutMillis)
        if (worker.isAlive) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 关闭 " + label + " 超时（" + timeoutMillis + "ms），已放弃等待；" +
                    "Chromium 驱动进程会随 JVM 退出被回收。"
            )
        }
    }

    private companion object {
        /**
         * 回收各阶段的等待上限。
         *
         * 取值来自实测：`Browser.close()` 约 2~3s（含等驱动回包）、`PlaywrightImpl.close()`
         * 约 2.4s（`Process.waitFor()`）。留足正常关闭的余量，只拦截真正卡死的驱动。
         */
        const val PAGE_CLOSE_TIMEOUT_MS = 5_000L
        const val CONTEXT_CLOSE_TIMEOUT_MS = 5_000L
        const val BROWSER_CLOSE_TIMEOUT_MS = 10_000L
        const val DRIVER_CLOSE_TIMEOUT_MS = 15_000L
    }
}
