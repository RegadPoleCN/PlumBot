package me.regadpole.plumbot.api.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.loader.AbstractConfigurationLoader
import java.nio.file.Path

/**
 * 基于 Configurate 的 HOCON 配置读取器。
 *
 * 保留供后续 HOCON 配置支持使用：当前默认以 `YamlConfigurator` 加载 `config.yml`，
 * 该类提供等价的 HOCON 入口以便未来切换或扩展配置格式时复用。
 */
class HoconConfigurator(config: ConfigurationNode) : AbstractConfigurator<HoconConfigurator>(config) {

    override fun createConfigurator(node: ConfigurationNode): HoconConfigurator {
        return HoconConfigurator(node)
    }

    override fun loaderBuilder(): AbstractConfigurationLoader.Builder<*, *> {
        return HoconConfigurationLoader.builder()
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
