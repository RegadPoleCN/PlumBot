// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import me.regadpole.plumbot.render.PlaywrightErrorCodes
import me.regadpole.plumbot.render.PlaywrightInstallOptions
import me.regadpole.plumbot.render.PlaywrightRenderException
import me.regadpole.plumbot.render.theme.ThemeManager

/**
 * 渲染失败映射：把**阶段**与**原始异常**翻译成带 code/stage/route/detail/hint/命令的
 * [PlaywrightRenderException]。
 *
 * 纪律：任一阶段失败都必须抛出（不返回 null、不吞异常）。没有这一步，调用方只能看到
 * "渲染失败"四个字，无法区分"模板没实现 route"与"页面没在前台导致截图超时"。
 *
 * 依赖主题管理器只为了生成"手动放置模板"的命令；用 provider 是因为门户重载会替换
 * [ThemeManager] 实例（构造期固化会拿到过期对象）。
 */
internal class FailureMapper(
    private val themeManager: () -> ThemeManager
) {

    /** 构造一次失败（统一入口，保证字段顺序不会被写歪）。 */
    fun failure(
        code: String,
        stage: String,
        route: String?,
        detail: String,
        hint: String? = null,
        command: String? = null,
        cause: Throwable? = null
    ): PlaywrightRenderException = PlaywrightRenderException(code, stage, detail, hint, command, route, cause)

    fun templateHint(): String =
        "模板不随插件分发。请按手动命令把单文件模板放到插件数据目录 web/default/index.html，或配置 render.playwright.dev_web_dir 指向本地模板。"

    fun templateManualCommand(theme: String): String? =
        runCatching { themeManager().manualTemplateCommand(theme) }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 阶段失败映射。
     *
     * 覆盖主流程会写入的全部 stage：payload/navigate/handshake/evaluate/ready/activate/
     * settle/screenshot；未知阶段统一按"渲染失败"处理，但仍保留原始 stage 便于排障。
     */
    fun mapStageFailure(stage: String, viewRoute: String, theme: String, cause: Throwable): PlaywrightRenderException {
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
        return when (stage) {
            "navigate" -> failure(
                PlaywrightErrorCodes.NAVIGATION_FAILED,
                stage,
                viewRoute,
                "导航到模板失败：" + detail,
                "检查入口文件是否存在、route 是否在模板中实现；若日志出现已拦截越权请求，请把资源域名加入 render.allowed_hosts。",
                null,
                cause
            )

            "handshake", "evaluate", "ready", "payload", "template" -> failure(
                PlaywrightErrorCodes.TEMPLATE_INVALID,
                stage,
                viewRoute,
                "模板握手/就绪阶段失败：" + detail,
                "模板需在数据就绪后把 window.__PLUMBOT_RENDER_READY__ 置为 true，并渲染 #render-target；协议需为 1。",
                templateManualCommand(theme),
                cause
            )

            "screenshot", "settle", "activate" -> failure(
                PlaywrightErrorCodes.SCREENSHOT_FAILED,
                stage,
                viewRoute,
                "截图失败：" + detail,
                "确认 #render-target 存在且可见，并确认页面已在前台（后台标签页不产帧，截图会超时）；必要时提高 render.playwright.render_timeout_ms。",
                null,
                cause
            )

            else -> failure(
                PlaywrightErrorCodes.NAVIGATION_FAILED,
                stage,
                viewRoute,
                "渲染失败：" + detail,
                "执行 /plumbot render doctor 并查看服务端日志。",
                null,
                cause
            )
        }
    }

    fun configFailure(detail: String): Nothing = throw failure(
        PlaywrightErrorCodes.CONFIG_INVALID,
        "config",
        null,
        detail,
        "检查 config.yml 的 render.playwright.*；custom_chrome_path 与 browser_channel 互斥，channel 仅支持 chrome/msedge 家族。"
    )

    fun browserMissing(options: PlaywrightInstallOptions, detail: String, manualCommand: String?): Nothing = throw failure(
        PlaywrightErrorCodes.BROWSER_MISSING,
        "browser",
        null,
        "未找到可用的 Chromium/Chrome/Edge，且 playwright-browsers 下没有已安装版本：" + detail,
        "运行 /plumbot render install 安装内置 Chromium；或设置 render.playwright.custom_chrome_path 指向 Chrome/Edge；或设置 browser_channel=chrome/msedge。渲染不再自动下载浏览器。",
        manualCommand
    )

    /** 依赖缺失：只"按一下"装载门就失败，绝不内联下载。 */
    fun dependencyMissing(detail: String, installCommand: String?): PlaywrightRenderException = failure(
        PlaywrightErrorCodes.DEPENDENCY_MISSING,
        "dependency",
        null,
        detail,
        "确认服务器可访问 Maven 仓库并已下载 playwright 1.49.0；离线环境请手动放入 lib 目录后执行 /plumbot render doctor。",
        installCommand
    )

    /** Playwright 驱动启动失败（driver-bundle / TCCL / 临时目录）。 */
    fun driverStartFailed(detail: String, cause: Throwable): PlaywrightRenderException = failure(
        PlaywrightErrorCodes.DRIVER_START_FAILED,
        "driver",
        null,
        detail,
        "检查 driver-bundle 是否被 Libby 注入、临时目录是否可写可执行；本插件已把 TCCL 切到插件加载器。执行 /plumbot render doctor 查看 jar 路径。",
        null,
        cause
    )

    /** Chromium 启动失败（可执行文件 / 沙箱）。 */
    fun launchFailed(detail: String, cause: Throwable): PlaywrightRenderException = failure(
        PlaywrightErrorCodes.LAUNCH_FAILED,
        "launch",
        null,
        detail,
        "确认可执行文件确实是 Chrome/Edge；容器内如确需关闭沙箱，可设 render.playwright.disable_sandbox=true（不推荐），并优先使用 custom_chrome_path 或系统浏览器。",
        null,
        cause
    )

    /** 浏览器上下文创建失败（scale_factor / 驱动状态）。 */
    fun contextFailed(detail: String, cause: Throwable): PlaywrightRenderException = failure(
        PlaywrightErrorCodes.LAUNCH_FAILED,
        "context",
        null,
        detail,
        "降低 render.playwright.scale_factor 后重试；若持续失败请执行 /plumbot render doctor。",
        null,
        cause
    )

    /** 请求拦截规则注册失败（render.allowed_hosts 配置形态）。 */
    fun requestGuardFailed(detail: String, cause: Throwable): PlaywrightRenderException = failure(
        PlaywrightErrorCodes.LAUNCH_FAILED,
        "context",
        null,
        detail,
        "检查 render.allowed_hosts 配置是否为字符串列表。",
        null,
        cause
    )
}
