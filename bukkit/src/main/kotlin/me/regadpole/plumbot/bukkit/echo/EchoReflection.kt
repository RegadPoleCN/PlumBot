// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 反射工具：跨 Paper / Spigot / log4j2 版本查找方法时避免依赖具体实现类。 */
internal object EchoReflection {

    fun method(type: Class<*>, name: String, parameterCount: Int, parameterType: Class<*>? = null): Method? {
        val visited = HashSet<Class<*>>()
        return search(type, name, parameterCount, parameterType, visited)
    }

    private fun search(
        type: Class<*>?,
        name: String,
        parameterCount: Int,
        parameterType: Class<*>?,
        visited: MutableSet<Class<*>>
    ): Method? {
        if (type == null || !visited.add(type)) return null
        val declared = type.declaredMethods.firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterCount == parameterCount &&
                !Modifier.isStatic(candidate.modifiers) &&
                (parameterType == null || candidate.parameterTypes[0].isAssignableFrom(parameterType))
        }
        if (declared != null) {
            runCatching { declared.isAccessible = true }
            return declared
        }
        for (iface in type.interfaces) {
            search(iface, name, parameterCount, parameterType, visited)?.let { return it }
        }
        return search(type.superclass, name, parameterCount, parameterType, visited)
    }

    fun invoke(target: Any, method: Method, vararg args: Any?): Any? = method.invoke(target, *args)

    fun string(target: Any, methodName: String): String? =
        runCatching {
            val method = method(target.javaClass, methodName, 0) ?: return@runCatching null
            method.invoke(target) as? String
        }.getOrNull()

    fun long(target: Any, methodName: String): Long? =
        runCatching {
            val method = method(target.javaClass, methodName, 0) ?: return@runCatching null
            (method.invoke(target) as? Number)?.toLong()
        }.getOrNull()
}
