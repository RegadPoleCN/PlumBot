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

package me.regadpole.plumbot.config

import java.lang.reflect.Modifier

/**
 * 把 `messages.yml` 覆盖到 [Messages] 的默认值上。
 *
 * 这里刻意用 `java.lang.reflect` 读取 Kotlin object 的静态字段，而不是 `Messages::class.members`：
 * 后者依赖 kotlin-reflect 的实现类，而 Shadow 的最小化只按 jdeps 的静态引用裁剪，看不到反射入口，
 * 产物里因此缺少 kotlin-reflect 的实现类，运行期会抛 `KotlinReflectionNotSupportedError`
 * （H1 三端真机启动实测命中）。object 的 `var` 属性编译为非 final 静态字段，字段名即属性名，
 * 与 `messages.yml` 的键一一对应；这样既保留自动绑定，也不必为 37 个字符串字段打包约 4 MB 的
 * kotlin-reflect 实现。
 */
internal object MessagesBinder {

    fun apply(conf: YamlConfigurator) {
        for (field in Messages::class.java.declaredFields) {
            val modifiers = field.modifiers
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue
            field.isAccessible = true
            when (field.type) {
                String::class.java -> conf.getString(field.name)?.let { field.set(Messages, it) }
                List::class.java -> field.set(Messages, conf.getStringList(field.name))
            }
        }
    }
}
