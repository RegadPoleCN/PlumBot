package me.regadpole.plumbot.internal

import snw.jkook.command.JKookCommand
import snw.jkook.config.ConfigurationSection
import snw.kookbc.SharedConstants
import snw.kookbc.impl.CoreImpl
import snw.kookbc.impl.KBCClient
import snw.kookbc.impl.command.internal.PluginsCommand
import java.io.File


class KookClient(coreImpl: CoreImpl, config: ConfigurationSection, file: File, token: String, networkMode: String) :
    KBCClient(coreImpl, config, file, token, networkMode) {

    override fun registerInternal() {
        var commandConfig: ConfigurationSection? = config.getConfigurationSection("internal-commands")
        if (commandConfig == null) {
            commandConfig = config.createSection("internal-commands")
        }

        if (commandConfig.getBoolean("plugins", true)) {
            this.registerPluginsCommand()
        }
    }

    override fun registerStopCommand() {
    }

    override fun registerPluginsCommand() {
        (JKookCommand("plugins")).setDescription(("获取已安装到此 " + SharedConstants.IMPL_NAME).toString() + " 实例的插件列表。")
            .setExecutor(
                PluginsCommand(
                    this
                )
            ).register(this.internalPlugin)
    }

    override fun registerHelpCommand() {
    }


}