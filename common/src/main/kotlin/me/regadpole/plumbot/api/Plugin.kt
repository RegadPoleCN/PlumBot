package me.regadpole.plumbot.api

/**
 * Platform-neutral view of a host plugin.
 *
 * Concrete platform implementations:
 *  - [me.regadpole.plumbot.bukkit.platform.BukkitPlugin] (Bukkit API).
 *
 * Adding a new platform means adding an adapter wrapping the platform's
 * native plugin object. Third-party plugins keep using this contract.
 */
@PublicApi
interface Plugin {
    val name: String
    val version: String
    val isEnabled: Boolean
}
