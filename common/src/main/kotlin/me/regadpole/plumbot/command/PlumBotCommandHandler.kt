// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.command

import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.render.PlaywrightDiagnosis
import me.regadpole.plumbot.render.PlaywrightInstallResult
import me.regadpole.plumbot.render.RenderHealth
import me.regadpole.plumbot.render.RenderProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * `/plumbot` 命令的平台侧依赖：只暴露平台真正不同的能力，其余语义统一在
 * [PlumBotCommandHandler] 里。三个平台（Bukkit / Velocity / Hytale）各自实现一次。
 */
interface CommandHost {
    /** 状态标题里展示的名字，如 `PlumBot`、`PlumBot Velocity`、`PlumBot Hytale`。 */
    val displayName: String

    /** 平台自己的热重载入口（配置/字体/敏感词库）。失败直接抛异常，由处理器转成错误消息。 */
    fun reloadPlugin()

    fun onlinePlayerCount(): Int

    fun kickPlayer(playerName: String)
}

/** 平台侧消息落地：Bukkit 序列化为 `§` 文本，Velocity 直接发 Component，Hytale 转纯文本。 */
fun interface CommandOutput {
    fun send(message: Component)
}

/**
 * `/plumbot` 的唯一实现（三端共用同一套逻辑与 Adventure 组件）。
 *
 * 各端自写一份 help/status/bind 时文案与边界条件会漂移，因此逻辑只在这里维护，
 * 平台层只剩「权限判定 + 把 Component 交给平台」。
 */
class PlumBotCommandHandler(
    private val host: CommandHost,
    private val renderCommands: RenderCommandService? = null,
    private val echoDiagnostics: (() -> List<String>)? = null
) {

    fun execute(output: CommandOutput, args: List<String>) {
        if (args.isEmpty()) {
            sendHelp(output)
            return
        }

        when (args[0].lowercase()) {
            "reload" -> reload(output)
            "status" -> status(output)
            "bind" -> bind(output, args.drop(1))
            "render" -> render(output, args.drop(1))
            "doctor" -> doctor(output, args.drop(1))
            else -> output.send(error("[PlumBot] 未知子命令，请输入 /plumbot 查看帮助。"))
        }
    }

    /** 供各平台 Tab 补全使用；不在命令层做权限判断（平台适配器负责）。 */
    fun suggest(args: List<String>): List<String> = when {
        args.isEmpty() -> SUBCOMMANDS
        args.size == 1 -> SUBCOMMANDS.filter { it.startsWith(args[0], ignoreCase = true) }
        args.size == 2 && args[0].equals("bind", ignoreCase = true) ->
            BIND_SUBCOMMANDS.filter { it.startsWith(args[1], ignoreCase = true) }
        args.size == 2 && args[0].equals("render", ignoreCase = true) ->
            RENDER_SUBCOMMANDS.filter { it.startsWith(args[1], ignoreCase = true) }
        args.size == 2 && args[0].equals("doctor", ignoreCase = true) ->
            DOCTOR_SUBCOMMANDS.filter { it.startsWith(args[1], ignoreCase = true) }
        else -> emptyList()
    }

    private fun reload(output: CommandOutput) {
        try {
            host.reloadPlugin()
            output.send(success("[PlumBot] 配置文件、字体资源与敏感词库已成功重载！"))
        } catch (e: Exception) {
            output.send(error("[PlumBot] 重载失败: ${e.message}"))
        }
    }

    private fun status(output: CommandOutput) {
        val bot = BotProvider.getBot()
        val botType = bot?.metadata?.displayName ?: "未连接"
        val dbStatus = if (DatabaseProvider.getDatabase() != null) "已连接" else "未连接"

        output.send(Component.text("===== [${host.displayName} 运行状态] =====", NamedTextColor.AQUA))
        output.send(labelValue("Bot 类型: ", botType))
        output.send(labelValue("数据库状态: ", dbStatus))
        output.send(labelValue("在线玩家数: ", host.onlinePlayerCount().toString()))
    }

    /** `/plumbot doctor [render|echo]`；不带参数等价于 render。 */
    private fun doctor(output: CommandOutput, subArgs: List<String>) {
        when (subArgs.firstOrNull()?.lowercase()) {
            null, "render" -> render(output, listOf("doctor"))
            "echo" -> echoDoctor(output)
            else -> output.send(usage("/plumbot doctor [render|echo]"))
        }
    }

    private fun echoDoctor(output: CommandOutput) {
        output.send(Component.text("===== [PlumBot 远程控制台回显诊断] =====", NamedTextColor.AQUA))
        val provider = echoDiagnostics
        if (provider == null) {
            output.send(Component.text("当前平台未接入远程控制台回显诊断。", NamedTextColor.GRAY))
            return
        }
        val lines = try {
            provider()
        } catch (t: Throwable) {
            output.send(error("[PlumBot] 采集回显诊断失败: " + (t.message ?: t.javaClass.simpleName)))
            return
        }
        lines.forEach { output.send(Component.text(" | " + it, NamedTextColor.GRAY)) }
        output.send(Component.text("探测只读，不会向服务器派发指令。", NamedTextColor.DARK_GRAY))
    }

    private fun bind(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.isEmpty()) {
            output.send(usage("/plumbot bind <add|remove|query> ..."))
            return
        }
        if (DatabaseProvider.getDatabase() == null) {
            output.send(error("[PlumBot] 数据库尚未就绪，无法执行绑定操作。"))
            return
        }

        when (subArgs[0].lowercase()) {
            "query" -> bindQuery(output, subArgs)
            "remove" -> bindRemove(output, subArgs)
            "add" -> bindAdd(output, subArgs)
            else -> output.send(error("[PlumBot] 未知的绑定子命令: ${subArgs[0]}"))
        }
    }

    private fun bindQuery(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.size < 2) {
            output.send(usage("/plumbot bind query <游戏名|qq:QQ号>"))
            return
        }
        val target = subArgs[1]
        if (target.startsWith("qq:")) {
            val qq = target.removePrefix("qq:")
            val binds = DatabaseProvider.getBindByUser(qq)
            output.send(success("[PlumBot] QQ $qq 绑定的玩家: ${binds.keys.ifEmpty { listOf("无") }}"))
            return
        }
        val qq = DatabaseProvider.getBindByName(target)
        if (qq != null) {
            output.send(success("[PlumBot] 玩家 $target 绑定的 QQ: $qq"))
        } else {
            output.send(error("[PlumBot] 玩家 $target 尚未绑定 QQ。"))
        }
    }

    private fun bindRemove(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.size < 2) {
            output.send(usage("/plumbot bind remove <游戏名>"))
            return
        }
        val playerName = subArgs[1]
        if (DatabaseProvider.getBindByName(playerName) == null) {
            output.send(error("[PlumBot] 玩家 $playerName 未被绑定。"))
            return
        }
        DatabaseProvider.getDatabase()?.removeBind(playerName)
        host.kickPlayer(playerName)
        output.send(success("[PlumBot] 已成功解绑并移出白名单: $playerName"))
    }

    private fun bindAdd(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.size < 3) {
            output.send(usage("/plumbot bind add <QQ号> <游戏名>"))
            return
        }
        val qq = subArgs[1].toLongOrNull()
        val playerName = subArgs[2]
        if (qq == null) {
            output.send(error("[PlumBot] 无效的 QQ 号: ${subArgs[1]}"))
            return
        }
        val database = DatabaseProvider.getDatabase()
        if (database == null) {
            output.send(error("[PlumBot] 数据库尚未就绪，无法执行绑定。"))
            return
        }
        val existing = DatabaseProvider.getBindByName(playerName)
        if (existing != null) {
            output.send(error("[PlumBot] 玩家 $playerName 已被 QQ $existing 绑定！"))
            return
        }
        database.addBind(qq, playerName)
        output.send(success("[PlumBot] 成功将玩家 $playerName 绑定至 QQ $qq！"))
    }

    private fun render(output: CommandOutput, subArgs: List<String>) {
        val service = renderCommands
        if (service == null) {
            output.send(error("[PlumBot] 当前平台未接入渲染诊断。"))
            return
        }
        if (subArgs.isEmpty()) {
            output.send(usage("/plumbot render <doctor|install|stats|reset> [--force]"))
            return
        }
        when (subArgs[0].lowercase()) {
            "doctor" -> renderDoctor(output, service)
            "install" -> renderInstall(output, service, subArgs.drop(1))
            "stats" -> renderStats(output)
            "reset" -> renderResetStats(output)
            else -> output.send(error("[PlumBot] 未知的渲染子命令: ${subArgs[0]}；可用：doctor / install / stats / reset"))
        }
    }

    /**
     * 打印渲染健康计数的分类明细。
     *
     * `/plumbot status` 的一行摘要放不下分类口径，排查「为什么降级」时需要看清
     * 出图/降级/失败/拒绝 各自的数字与最近一次失败原因。
     */
    private fun renderStats(output: CommandOutput) {
        val health = RenderProvider.getOrNull()?.renderHealth() ?: RenderHealth.EMPTY
        output.send(Component.text("===== [PlumBot 渲染计数] =====", NamedTextColor.AQUA))
        output.send(
            labelValue(
                "出图/降级/失败：",
                "${health.emitted} / ${health.degraded} / ${health.failed}"
            )
        )
        output.send(
            labelValue(
                "其中 AWT 兜底：",
                "${health.fallbackAwt}"
            )
        )
        output.send(
            labelValue(
                "队列拒绝/引擎未就绪：",
                "${health.queueRejected} / ${health.engineUnavailable}"
            )
        )
        output.send(
            labelValue(
                "配置关闭/预热失败/发图失败：",
                "${HealthCounters.get(HealthCounters.Key.RENDER_DISABLED)} / " +
                    "${HealthCounters.get(HealthCounters.Key.RENDER_FALLBACK)} / " +
                    HealthCounters.get(HealthCounters.Key.RENDER_SEND_FAILED)
            )
        )
        output.send(labelValue("最近失败：", health.lastFailure ?: "无"))
        output.send(Component.text("计数自插件启用以来累计；/plumbot render reset 可清零。", NamedTextColor.GRAY))
    }

    /**
     * 清零渲染健康计数：门户与业务层**必须成对清零**，只清一边会出现
     * 「出图 0 但降级 5」这类自相矛盾的读数。
     */
    private fun renderResetStats(output: CommandOutput) {
        val portal = RenderProvider.getOrNull()
        if (portal == null) {
            output.send(error("[PlumBot] 渲染门户尚未初始化，无可清零的计数。"))
            return
        }
        portal.resetRenderHealth()
        HealthCounters.reset()
        output.send(success("[PlumBot] 渲染健康计数已清零。"))
    }

    private fun renderDoctor(output: CommandOutput, service: RenderCommandService) {
        output.send(Component.text("===== [PlumBot 渲染诊断] =====", NamedTextColor.AQUA))
        output.send(Component.text("正在诊断…", NamedTextColor.GRAY))
        try {
            service.diagnoseAsync(
                onDone = { diagnosis -> sendDiagnosis(output, service, diagnosis) },
                onError = { t ->
                    output.send(error("[PlumBot] 渲染诊断失败: ${t.message ?: t.javaClass.simpleName}"))
                }
            )
        } catch (t: Throwable) {
            // diagnoseAsync 自身已兜底调度异常，这里只防回调装配阶段的意外。
            output.send(error("[PlumBot] 渲染诊断失败: ${t.message ?: t.javaClass.simpleName}"))
        }
    }

    private fun sendDiagnosis(
        output: CommandOutput,
        service: RenderCommandService,
        diagnosis: PlaywrightDiagnosis
    ) {
        output.send(labelValue("Playwright: ", if (diagnosis.ready) "就绪" else "未就绪"))
        diagnosis.code?.let { output.send(labelValue("错误码: ", it)) }
        output.send(Component.text(diagnosis.summary, NamedTextColor.GRAY))
        service.describeOptions().forEach { output.send(Component.text(" - $it", NamedTextColor.DARK_GRAY)) }
        diagnosis.details.forEach { output.send(Component.text(" - $it", NamedTextColor.DARK_GRAY)) }
        output.send(Component.text("手动安装: ${diagnosis.manualCommand}", NamedTextColor.YELLOW))
        if (!diagnosis.ready) {
            output.send(Component.text("可在游戏内执行 /plumbot render install 自动安装（需要可访问下载镜像）。", NamedTextColor.GRAY))
        }
    }

    private fun renderInstall(output: CommandOutput, service: RenderCommandService, flags: List<String>) {
        val unknown = flags.firstOrNull { it.lowercase() != "--force" }
        if (unknown != null) {
            output.send(usage("/plumbot render install [--force]"))
            return
        }
        val force = flags.any { it.equals("--force", ignoreCase = true) }
        output.send(Component.text(
            "===== [PlumBot 渲染安装] =====",
            NamedTextColor.AQUA
        ))
        output.send(Component.text("目标目录: ${service.browsersPathDisplay()}", NamedTextColor.GRAY))
        try {
            service.installAsync(
                force = force,
                onProgress = { line -> output.send(Component.text(line, NamedTextColor.GRAY)) },
                onDone = { result -> reportInstallResult(output, result) }
            )
        } catch (t: Throwable) {
            output.send(error("[PlumBot] 无法启动安装: ${t.message ?: t.javaClass.simpleName}"))
        }
    }

    private fun reportInstallResult(output: CommandOutput, result: PlaywrightInstallResult) {
        if (result.success) {
            output.send(success("[PlumBot] Chromium 安装完成：${result.installedBrowserPath ?: "已就绪"}"))
            output.send(Component.text("执行 /plumbot render doctor 复核环境。", NamedTextColor.GRAY))
            return
        }
        output.send(error("[PlumBot] Chromium 安装失败${result.code?.let { " [$it]" } ?: ""}：${result.message}"))
        result.outputTail.takeLast(10).forEach { output.send(Component.text(" | $it", NamedTextColor.DARK_GRAY)) }
        output.send(Component.text("手动命令: ${result.command}", NamedTextColor.YELLOW))
    }

    private fun sendHelp(output: CommandOutput) {
        output.send(Component.text("===== [PlumBot 指令帮助] =====", NamedTextColor.AQUA))
        output.send(helpLine("/plumbot reload", "热重载插件配置、字体资源与敏感词库"))
        output.send(helpLine("/plumbot status", "查看当前 Bot 与数据库状态"))
        output.send(helpLine("/plumbot bind query <游戏名|qq:QQ号>", "查询白名单绑定"))
        output.send(helpLine("/plumbot bind add <QQ号> <游戏名>", "管理员强制绑定"))
        output.send(helpLine("/plumbot bind remove <游戏名>", "管理员解绑并移出白名单"))
        output.send(helpLine("/plumbot render doctor", "诊断 Playwright 渲染环境"))
        output.send(helpLine("/plumbot render install [--force]", "下载并安装 Chromium"))
        output.send(helpLine("/plumbot render stats", "查看渲染出图/降级/失败的分类计数"))
        output.send(helpLine("/plumbot render reset", "清零渲染与健康计数"))
        output.send(helpLine("/plumbot doctor echo", "诊断远程控制台回显通道"))
    }

    private fun helpLine(command: String, description: String): Component =
        Component.text(command, NamedTextColor.YELLOW)
            .append(Component.text(" - $description", NamedTextColor.GRAY))

    private fun labelValue(label: String, value: String): Component =
        Component.text(label, NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.GREEN))

    private fun success(text: String): Component = Component.text(text, NamedTextColor.GREEN)

    private fun error(text: String): Component = Component.text(text, NamedTextColor.RED)

    private fun usage(text: String): Component = Component.text("用法: $text", NamedTextColor.YELLOW)

    private companion object {
        val SUBCOMMANDS = listOf("reload", "status", "bind", "render", "doctor")
        val BIND_SUBCOMMANDS = listOf("query", "add", "remove")
        val RENDER_SUBCOMMANDS = listOf("doctor", "install", "stats", "reset")
        val DOCTOR_SUBCOMMANDS = listOf("render", "echo")
    }
}
