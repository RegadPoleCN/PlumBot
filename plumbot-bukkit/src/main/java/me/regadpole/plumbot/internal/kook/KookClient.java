package me.regadpole.plumbot.internal.kook;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snw.jkook.command.CommandManager;
import snw.jkook.command.JKookCommand;
import snw.jkook.config.ConfigurationSection;
import snw.kookbc.SharedConstants;
import snw.kookbc.impl.CoreImpl;
import snw.kookbc.impl.KBCClient;
import snw.kookbc.impl.command.internal.PluginsCommand;
import snw.kookbc.impl.entity.builder.EntityBuilder;
import snw.kookbc.impl.entity.builder.MessageBuilder;
import snw.kookbc.impl.event.EventFactory;
import snw.kookbc.impl.network.NetworkClient;
import snw.kookbc.impl.storage.EntityStorage;
import snw.kookbc.interfaces.network.NetworkSystem;
import snw.kookbc.util.ReturnNotNullFunction;

import java.io.File;

public class KookClient extends KBCClient {
    public KookClient(CoreImpl core, ConfigurationSection config, File pluginsFolder, String token) {
        super(core, config, pluginsFolder, token);
    }

    public KookClient(CoreImpl core, ConfigurationSection config, File pluginsFolder, String token, @NotNull String networkMode) {
        super(core, config, pluginsFolder, token, networkMode);
    }

    public KookClient(CoreImpl core, ConfigurationSection config, File pluginsFolder, String token, @Nullable ReturnNotNullFunction<KBCClient, CommandManager> commandManager, @Nullable ReturnNotNullFunction<KBCClient, NetworkClient> networkClient, @Nullable ReturnNotNullFunction<KBCClient, EntityStorage> storage, @Nullable ReturnNotNullFunction<KBCClient, EntityBuilder> entityBuilder, @Nullable ReturnNotNullFunction<KBCClient, MessageBuilder> msgBuilder, @Nullable ReturnNotNullFunction<KBCClient, EventFactory> eventFactory, @Nullable ReturnNotNullFunction<KBCClient, NetworkSystem> networkSystem) {
        super(core, config, pluginsFolder, token, commandManager, networkClient, storage, entityBuilder, msgBuilder, eventFactory, networkSystem);
    }

    @Override
    protected void registerInternal() {
        ConfigurationSection commandConfig = this.getConfig().getConfigurationSection("internal-commands");
        if (commandConfig == null) {
            commandConfig = this.getConfig().createSection("internal-commands");
        }

        if (commandConfig.getBoolean("plugins", true)) {
            this.registerPluginsCommand();
        }
    }

    @Override
    protected void registerStopCommand() {

    }

    @Override
    protected void registerPluginsCommand() {
        (new JKookCommand("plugins")).setDescription("获取已安装到此 " + SharedConstants.IMPL_NAME + " 实例的插件列表。").setExecutor(new PluginsCommand(this)).register(this.getInternalPlugin());
    }

    @Override
    protected void registerHelpCommand() {
    }


}
