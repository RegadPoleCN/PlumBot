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
            return createConfig(folder, fileName, ::YamlConfigurator, YamlConfigurationLoader::builder)
        }
    }
}
