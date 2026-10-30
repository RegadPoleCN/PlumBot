// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.config

import me.regadpole.plumbot.api.platform.PlatformConfig

/**
 * `feature.message.console_echo`：群消息转发到游戏时，是否同时写入服务端控制台。
 *
 * 默认值必须是**开**：Bukkit 一直双写（控制台 + 在线玩家），Hytale 漏了控制台，
 * 同一句话在 Bukkit 端可见、在 Hytale 端一行都没有。所以不能只写 `config.getBoolean(...)`：
 * 它按 `raw()` 读，缺失键返回 false，玩家删掉这一行就会静默关掉回显。这里先用
 * `rawTypeOf` 判断键是否存在，只有缺失才回落 `true`；它是纯读，不会像 `node.boolean`
 * 那样把默认值写回配置树。显式写成 `false` / `"false"` / `0` 一律按关闭处理。
 */
object ConsoleEchoSetting {
    private const val MISSING = "missing"

    fun enabled(config: PlatformConfig): Boolean {
        if (config.rawTypeOf("feature", "message", "console_echo") == MISSING) return true
        return config.getBoolean("feature", "message", "console_echo")
    }
}