/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.utils

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale

/** 可替换的原版翻译键解析器：生产实现是 [MinecraftTranslationSource]，测试可注入 fake。 */
fun interface MinecraftTranslationResolver {
    /** 命中时返回已格式化的文本；未命中或不可用时返回 null。 */
    fun resolve(key: String, args: List<String>): String?
}

/**
 * Paper / Spigot 服务端原版翻译键的纯反射读取器。
 *
 * [net.kyori.adventure.translation.GlobalTranslator] 只含宿主插件注册的翻译源，Paper 不会把
 * 原版 `commands.*` 注册进去，因此直接反射读取 NMS 的 [net.minecraft.locale.Language]
 * （Mojang mappings）或旧 Spigot 的 [net.minecraft.locale.LocaleLanguage]：不编译依赖 NMS，
 * 也不抛异常。
 *
 * 查找顺序：`Bukkit.getServer()` 的类加载器 → Bukkit 类加载器 → TCCL。找不到时只记录一次
 * 原因，后续直接返回 null；[status] 供 doctor 诊断。
 */
object MinecraftTranslationSource : MinecraftTranslationResolver {

    private const val BUKKIT_CLASS = "org.bukkit.Bukkit"
    private const val LANGUAGE_CLASS = "net.minecraft.locale.Language"
    private const val LEGACY_LANGUAGE_CLASS = "net.minecraft.locale.LocaleLanguage"

    private val languageClassNames = listOf(LANGUAGE_CLASS, LEGACY_LANGUAGE_CLASS)

    private enum class State { UNINITIALIZED, READY, UNAVAILABLE }

    @Volatile
    private var state = State.UNINITIALIZED

    @Volatile
    private var handle: LanguageHandle? = null

    @Volatile
    private var failureReason: String? = null

    override fun resolve(key: String, args: List<String>): String? {
        val language = language() ?: return null
        return resolveTemplate(key, language.getOrDefault(key), args)
    }

    /** 诊断行：可用时标明默认语言与来源类；不可用时给出一次记录的原因（无堆栈、无路径）。 */
    fun status(): String {
        val language = language()
        return if (language != null) {
            "可用（en_us，来源：" + language.source + "）"
        } else {
            "不可用（" + (failureReason ?: "未找到可用的原版翻译源") + "）"
        }
    }

    /** 把模板变成最终文本；模板等于 key 说明 `getOrDefault` 没命中。抽出以便无 NMS 单测。 */
    internal fun resolveTemplate(key: String, template: String?, args: List<String>): String? {
        if (template.isNullOrBlank()) return null
        if (template == key) return null
        return formatTemplate(template, args)
    }

    /**
     * 按 `String.format` 填充参数；失败时保留模板和全部参数，绝不静默丢参。
     * 参数是字符串而模板仍可能写 `%d`，故 [IllegalArgumentException]（含
     * [java.util.IllegalFormatException]）必须被兜住。
     */
    internal fun formatTemplate(template: String, args: List<String>): String {
        if (args.isEmpty()) return template
        return try {
            String.format(Locale.ROOT, template, *args.toTypedArray())
        } catch (_: IllegalArgumentException) {
            template + " (" + args.joinToString(", ") + ")"
        }
    }

    /**
     * 预热：启动期把 en_us 翻译表解析一次（幂等，失败只记原因）——首次投影往往发生在
     * **主线程**的回显回调里，冷派发实测 48ms 落在这里及其下游。
     */
    fun warmUp() {
        runCatching { language() }
    }

    private fun language(): LanguageHandle? {
        handle?.let { return it }
        if (state == State.UNAVAILABLE) return null
        synchronized(this) {
            handle?.let { return it }
            if (state == State.UNAVAILABLE) return null
            val found = discover()
            if (found != null) {
                handle = found
                state = State.READY
            } else {
                state = State.UNAVAILABLE
            }
            return found
        }
    }

    private fun discover(): LanguageHandle? {
        val candidates = classLoaders()
        val labels = candidates.map { it.label }.distinct()
        val reasons = ArrayList<String>()

        for (candidate in candidates) {
            for (className in languageClassNames) {
                val type = try {
                    Class.forName(className, false, candidate.loader)
                } catch (_: Throwable) {
                    null
                }
                if (type == null) {
                    reasons += candidate.label + " 缺少 " + className
                    continue
                }

                val instance = try {
                    val getInstance = findMethod(type, "getInstance")
                    if (getInstance == null || !Modifier.isStatic(getInstance.modifiers)) {
                        null
                    } else {
                        getInstance.invoke(null)
                    }
                } catch (_: Throwable) {
                    null
                }
                if (instance == null) {
                    reasons += candidate.label + " 的 " + className + "#getInstance 不可用"
                    continue
                }

                val getOrDefault = findMethod(type, "getOrDefault", String::class.java)
                if (getOrDefault == null) {
                    reasons += candidate.label + " 的 " + className + "#getOrDefault 不可用"
                    continue
                }

                return LanguageHandle(instance, getOrDefault, candidate.label + ":" + type.name)
            }
        }

        failureReason = reasons.firstOrNull { it.contains("#getInstance") || it.contains("#getOrDefault") }
            ?: ("未找到 " + LANGUAGE_CLASS + "/" + LEGACY_LANGUAGE_CLASS +
                "（已尝试：" + labels.joinToString("/") + "）")
        return null
    }

    private fun classLoaders(): List<LoaderCandidate> {
        val found = LinkedHashMap<ClassLoader, String>()
        val own = MinecraftTranslationSource::class.java.classLoader
        val bukkit = findBukkitClass(own) ?: findBukkitClass(Thread.currentThread().contextClassLoader)

        if (bukkit != null) {
            val serverLoader = try {
                val server = bukkit.getMethod("getServer").invoke(null)
                server?.javaClass?.classLoader
            } catch (_: Throwable) {
                null
            }
            if (serverLoader != null) addLoader(found, serverLoader, "server")
            val bukkitLoader = bukkit.classLoader
            if (bukkitLoader != null) addLoader(found, bukkitLoader, "bukkit")
        }

        Thread.currentThread().contextClassLoader?.let { addLoader(found, it, "tccl") }
        // 兜底：某些宿主把 Bukkit API 与插件放在同一 loader，但 TCCL 被改成别的 loader。
        own?.let { addLoader(found, it, "plugin") }

        return found.map { LoaderCandidate(it.key, it.value) }
    }

    private fun addLoader(loaders: MutableMap<ClassLoader, String>, loader: ClassLoader, label: String) {
        if (!loaders.containsKey(loader)) loaders[loader] = label
    }

    private fun findBukkitClass(loader: ClassLoader?): Class<*>? {
        if (loader == null) return null
        return try {
            Class.forName(BUKKIT_CLASS, false, loader)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findMethod(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        try {
            return type.getMethod(name, *parameterTypes)
        } catch (_: Throwable) {
            // 回退到 declared 方法；NMS 在不同映射下可见性可能不同。
        }
        return try {
            type.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    private data class LoaderCandidate(val loader: ClassLoader, val label: String)

    private class LanguageHandle(
        private val instance: Any,
        private val method: Method,
        val source: String
    ) {
        fun getOrDefault(key: String): String? {
            return try {
                method.invoke(instance, key) as? String
            } catch (_: Throwable) {
                null
            }
        }
    }
}
