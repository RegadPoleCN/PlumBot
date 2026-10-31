// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.bukkit.echo.support.EchoReflection
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 通过 log4j2 根 Appender 旁路捕获控制台日志。
 *
 * Paper / Spigot 的控制台回显最终都写 root logger。插件不应把 log4j2 打进依赖，因此反射拿
 * core logger 与 Appender 接口，再用动态代理挂只读旁路；服务端不用 log4j2 或版本不兼容时
 * 只是该通道不可用，不影响指令执行。
 */
internal class Log4j2EchoSource private constructor(
    private val session: EchoSession,
    private val includeLogPrefix: Boolean,
    private val logger: Any,
    private val addAppender: Method,
    private val removeAppender: Method,
    private val appenderClass: Class<*>,
    private val startedState: Any?
) : EchoSource {

    override val name: String = SOURCE

    private var appender: Any? = null
    private var attached = false

    override fun start(): Boolean {
        if (attached) return true
        return runCatching {
            val proxy = Proxy.newProxyInstance(
                appenderClass.classLoader ?: javaClass.classLoader,
                arrayOf(appenderClass),
                Handler(session, includeLogPrefix, startedState)
            )
            addAppender.invoke(logger, proxy)
            appender = proxy
            attached = true
            true
        }.getOrDefault(false)
    }

    override fun finish(session: EchoSession) {
        if (!attached) return
        attached = false
        val current = appender ?: return
        appender = null
        runCatching { removeAppender.invoke(logger, current) }
    }

    private class Handler(
        private val session: EchoSession,
        private val includeLogPrefix: Boolean,
        private val state: Any?
    ) : InvocationHandler {

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "append" -> {
                    args?.getOrNull(0)?.let(::captureEvent)
                    null
                }
                "getName" -> APPENDER_NAME
                "getLayout" -> null
                "ignoreExceptions" -> true
                "getState" -> state
                "isStarted" -> true
                "start", "stop" -> null
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> APPENDER_NAME
                else -> defaultValue(method.returnType)
            }
        }

        private fun captureEvent(event: Any) {
            if (session.isClosed) return
            val time = EchoReflection.long(event, "getTimeMillis")
            if (time != null && time < session.startMillis - START_SLACK_MS) return
            val message = runCatching {
                EchoReflection.method(event.javaClass, "getMessage", 0)?.invoke(event)
            }.getOrNull() ?: return
            val text = EchoReflection.string(message, "getFormattedMessage") ?: return
            session.logLine(SOURCE, text)
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }

    companion object {
        private const val SOURCE = "log4j2"
        private const val APPENDER_NAME = "PlumBot-Echo-Appender"
        private const val START_SLACK_MS = 20L

        /**
         * 反射句柄缓存。`Class.forName` 与 `getMethod` 过去每次派发都在主线程做一遍，而
         * log4j2 可用性在 JVM 生命周期内不变，解析一次即可。
         */
        private class Handles(
            val logger: Any,
            val addAppender: Method,
            val removeAppender: Method,
            val appenderClass: Class<*>,
            val startedState: Any?
        )

        @Volatile
        private var handles: Handles? = null

        /** 解析失败也记住：换通道不会让 log4j2 凭空出现，避免每次派发重试反射。 */
        @Volatile
        private var resolveFailed = false

        private val resolveLock = Any()

        /** 预热：把反射解析提前到启动期异步线程。 */
        fun prewarm(debug: (String) -> Unit) {
            handlesOrNull(debug)
        }

        fun tryCreate(
            session: EchoSession,
            includeLogPrefix: Boolean,
            debug: (String) -> Unit
        ): Log4j2EchoSource? {
            val resolved = handlesOrNull(debug) ?: return null
            return Log4j2EchoSource(
                session = session,
                includeLogPrefix = includeLogPrefix,
                logger = resolved.logger,
                addAppender = resolved.addAppender,
                removeAppender = resolved.removeAppender,
                appenderClass = resolved.appenderClass,
                startedState = resolved.startedState
            )
        }

        private fun handlesOrNull(debug: (String) -> Unit): Handles? {
            handles?.let { return it }
            if (resolveFailed) return null
            synchronized(resolveLock) {
                handles?.let { return it }
                if (resolveFailed) return null
                val resolved = runCatching { resolve(debug) }.getOrElse {
                    debug("log4j2 回显源不可用: " + (it.message ?: it.javaClass.simpleName))
                    null
                }
                if (resolved == null) resolveFailed = true else handles = resolved
                return resolved
            }
        }

        private fun resolve(debug: (String) -> Unit): Handles? {
            val logManager = Class.forName("org.apache.logging.log4j.LogManager")
            val root = logManager.getMethod("getRootLogger").invoke(null) ?: return null
            if (!root.javaClass.name.startsWith("org.apache.logging.log4j.core.")) {
                debug("log4j2 根 logger 不是 core 实现: " + root.javaClass.name)
                return null
            }
            val add = EchoReflection.method(root.javaClass, "addAppender", 1)
            val remove = EchoReflection.method(root.javaClass, "removeAppender", 1)
            if (add == null || remove == null) {
                debug("log4j2 Logger 缺少 addAppender/removeAppender")
                return null
            }
            val appenderClass = Class.forName("org.apache.logging.log4j.core.Appender")
            val startedState = runCatching {
                val stateClass = Class.forName("org.apache.logging.log4j.core.LifeCycle\$State")
                stateClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "STARTED" }
                    ?: stateClass.enumConstants?.firstOrNull()
            }.getOrNull()
            return Handles(root, add, remove, appenderClass, startedState)
        }
    }
}
