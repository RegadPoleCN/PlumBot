package me.regadpole.plumbot.api.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.AbstractConfigurationLoader
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path

class YamlConfigurator(config: ConfigurationNode) : AbstractConfigurator<YamlConfigurator>(config) {

    override fun createConfigurator(node: ConfigurationNode): YamlConfigurator {
        return YamlConfigurator(node)
    }

    override fun loaderBuilder(): AbstractConfigurationLoader.Builder<*, *> {
        return YamlConfigurationLoader.builder()
    }

    companion object {
        @JvmStatic
        fun createConfig(folder: Path, fileName: String): YamlConfigurator? {
            return AbstractConfigurator.createConfig(folder, fileName, ::YamlConfigurator, YamlConfigurationLoader::builder)
        }
    }

    public override fun clone(): YamlConfigurator {
        return YamlConfigurator(config.copy())
    }
}
