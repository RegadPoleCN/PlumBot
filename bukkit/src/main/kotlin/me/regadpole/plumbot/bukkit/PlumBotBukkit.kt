package me.regadpole.plumbot.bukkit

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bukkit.listener.ServerListener
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.reflect.KMutableProperty

class PlumBotBukkit: JavaPlugin(), PlumBot{
    private val libraryManager = BukkitLibraryManager(plugin)
    private val audience = BukkitAudiences.create(this)

    override var dataDirectory: Path = dataFolder.toPath()
    override var datasource: YamlConfigurator = YamlConfigurator.createConfig(dataDirectory, "datasource.yml")!!
    override var config: YamlConfigurator = YamlConfigurator.createConfig(dataDirectory, "config.yml")!!
    override var messages: Messages = Messages()

    override fun onEnable() {
        // 插件启用时的逻辑
        enable()
        server.pluginManager.registerEvents(ServerListener(this), this)
        logger.info("PlumBot has been enabled!")
    }

    override fun onDisable() {
        // 插件禁用时的逻辑
        disable()
        logger.info("PlumBot has been disabled!")
    }

    override fun log(level: LogLevel, log: String) {
        when (level) {
            LogLevel.TRACE -> logger.finest(log)
            LogLevel.DEBUG -> logger.fine(log)
            LogLevel.INFO -> logger.info(log)
            LogLevel.WARN -> logger.warning(log)
            LogLevel.ERROR -> logger.severe(log)
            LogLevel.FATAL -> logger.severe(log)
        }
    }

    override fun sendMessage(message: Component) {
        audience.sender(this.server.consoleSender).sendMessage(message)
        audience.players().sendMessage(message)
    }

    override fun kickPlayer(name: String) {
        val kickMessage = getLegacyFromComponent(getComponentFromMiniMsg(
            messages.kickServer
                .replace("%groups%", config.getLongList("groups").toString())
        ))
        server.getPlayer(name)?.kickPlayer(kickMessage)
    }

    override fun listPlayers(): List<String> {
        return server.onlinePlayers
            .map { it.name }
            .sorted()
            .toList()
    }

    override fun listPlayerString(): String {
        val newLine = 5
        var list = server.onlinePlayers.map { it.name }.sorted().toList()
        var result = ""
        while(list.size > newLine) {
            result += list.slice(0..<newLine).joinToString(postfix = "\n  ")
            list = list.drop(newLine)
        }
        result += list.joinToString()
        result += "\n"
        return result
    }

    override fun submit(task: Runnable): Future<*> {
        Bukkit.getScheduler().runTask(this, task)
        return CompletableFuture.completedFuture<Void>(null)
    }

    override fun submitAsync(task: Runnable): Future<*> {
        Bukkit.getScheduler().runTaskAsynchronously(this, task)
        return CompletableFuture.completedFuture<Void>(null)
    }

    override fun submitLater(delay: Long, task: Runnable): Future<*> {
        Bukkit.getScheduler().runTaskLater(this, task, delay)
        return CompletableFuture.completedFuture<Void>(null)
    }

    override fun submitLaterAsync(delay: Long, task: Runnable): Future<*> {
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, task, delay)
        return CompletableFuture.completedFuture<Void>(null)
    }

    override fun submitTimer(delay: Long, period: Long, task: Runnable): Future<*> {
        Bukkit.getScheduler().runTaskTimer(this, task, delay, period)
        return CompletableFuture.completedFuture<Void>(null)
    }

    override fun submitTimerAsync(delay: Long, period: Long, task: Runnable): Future<*> {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, task, delay, period)
        return CompletableFuture.completedFuture<Void>(null)
    }

    fun loadConfig() {
        config = YamlConfigurator.createConfig(dataDirectory, "config.yml")!!
        datasource = YamlConfigurator.createConfig(dataDirectory, "datasource.yml")!!
        val messagesConf = YamlConfigurator.createConfig(dataDirectory, "messages.yml")
        messages::class.members.forEach{
            if (it is KMutableProperty<*>) {
                when(it.returnType.classifier) {
                    String::class -> it.setter.call(messages, messagesConf!!.getString(it.name))
                    List::class -> it.setter.call(messages, messagesConf!!.getStringList(it.name))
                }
                log(LogLevel.DEBUG, "messages: ${it.name} -> ${it.call(messages)}")
            }
        }
    }

    override fun loadDependencies() {
        val adventureBukkitLib = Library.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-platform-bukkit")
            .version("4.3.4")
            .resolveTransitiveDependencies(true)
            .build()
        val databaseLib = Library.builder()
            .groupId("com{}github{}RegadPoleCN")
            .artifactId("taboolib-database")
            .version("1.0.2")
            .relocate("com{}google{}common", "top{}alazeprt{}aqqbot{}lib{}com{}google{}common")
            .build()
        val hikaricpLib = Library.builder()
            .groupId("com{}zaxxer")
            .artifactId("HikariCP")
            .version("4.0.3")
            .resolveTransitiveDependencies(true)
            .build()
        val guavaLib = Library.builder()
            .groupId("com{}google{}guava")
            .artifactId("guava")
            .version("21.0")
            .relocate("com{}google{}common", "top{}alazeprt{}aqqbot{}lib{}com{}google{}common")
            .resolveTransitiveDependencies(true)
            .build()
        val sqliteLib = Library.builder()
            .groupId("org{}xerial")
            .artifactId("sqlite-jdbc")
            .version("3.49.0.0")
            .resolveTransitiveDependencies(true)
            .build()
        val mysqlLib = Library.builder()
            .groupId("com{}mysql")
            .artifactId("mysql-connector-j")
            .version("8.3.0")
            .resolveTransitiveDependencies(true)
            .build()
        val aonebotLib = Library.builder()
            .groupId("com{}github{}alazeprt")
            .artifactId("AOneBot")
            .version("1.0.11-beta")
            .relocate("com{}google{}code{}gson", "top{}alazeprt{}aonebot{}lib{}com{}google")
            .resolveTransitiveDependencies(true)
            .build()
        val aedileLib = Library.builder()
            .groupId("com{}sksamuel{}aedile")
            .artifactId("aedile-core")
            .version("2.0.3")
            .resolveTransitiveDependencies(true)
            .build()
        val gsonLib = Library.builder()
            .groupId("com{}google{}code{}gson")
            .artifactId("gson")
            .version("2.11.0")
            .resolveTransitiveDependencies(true)
            .build()
        val configurateYamlLib = Library.builder()
            .groupId("org{}spongepowered")
            .artifactId("configurate-yaml")
            .version("4.2.0")
            .resolveTransitiveDependencies(true)
            .build()
        val configurateHoconLib = Library.builder()
            .groupId("org{}spongepowered")
            .artifactId("configurate-hocon")
            .version("4.2.0")
            .resolveTransitiveDependencies(true)
            .build()
        val configurateExtraKotlinLib = Library.builder()
            .groupId("org{}spongepowered")
            .artifactId("configurate-extra-kotlin")
            .version("4.2.0")
            .resolveTransitiveDependencies(true)
            .build()

        libraryManager.addRepository("https://maven.aliyun.com/repository/public")
        libraryManager.addMavenCentral()
        libraryManager.addJitPack()
        libraryManager.loadLibraries(adventureBukkitLib, guavaLib, hikaricpLib, sqliteLib, mysqlLib, databaseLib, aonebotLib, aedileLib, gsonLib, configurateYamlLib, configurateHoconLib, configurateExtraKotlinLib)
    }
}