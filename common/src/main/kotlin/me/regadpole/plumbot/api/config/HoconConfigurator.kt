package me.regadpole.plumbot.api.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.loader.AbstractConfigurationLoader
import java.nio.file.Path

class HoconConfigurator(config: ConfigurationNode) : AbstractConfigurator<HoconConfigurator>(config) {

    override fun createConfigurator(node: ConfigurationNode): HoconConfigurator {
        return HoconConfigurator(node)
    }

    override fun loaderBuilder(): AbstractConfigurationLoader.Builder<*, *> {
        return HoconConfigurationLoader.builder()
    }

    @Deprecated("Use getString instead", ReplaceWith("getString(*nodePath)"))
    fun getStringFromConfig(vararg nodePath: String?): String? {
        return getString(*nodePath)
    }

    companion object {
        @JvmStatic
        fun createConfig(folder: Path, fileName: String): HoconConfigurator? {
            return AbstractConfigurator.createConfig(folder, fileName, ::HoconConfigurator, HoconConfigurationLoader::builder)
        }
    }

    public override fun clone(): HoconConfigurator {
        return HoconConfigurator(config.copy())
    }
}
