package com.chronosmp

import com.chronosmp.platform.paper.PaperPlatformPlayer
import com.chronosmp.platform.paper.PaperPlatformCommandSender
import com.chronosmp.platform.paper.PaperPlatformServer
import com.chronosmp.commands.TimeAmount
import io.papermc.paper.advancement.AdvancementDisplay
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class PaperChronoPlugin : JavaPlugin(), Listener {
    override fun onEnable() {
    ChronoSMP.initialize(this.dataFolder.toPath())
    ChronoSMP.onServerStarted(PaperPlatformServer(server))
    server.pluginManager.registerEvents(this, this)
    getCommand("chrono")?.setExecutor(this)
    getCommand("chrono")?.tabCompleter = this

    server.scheduler.runTaskTimer(this, Runnable {
        ChronoSMP.onTick(PaperPlatformServer(server))
    }, 1L, 1L)

    if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
        ChronoExpansion(ChronoSMP.dataManager).register()
        logger.info("PlaceholderAPI found - registered Chrono placeholders.")
    } else {
        logger.warning("PlaceholderAPI not found - %chrono_...% placeholders will not be available.")
    }
}

    override fun onDisable() {
        ChronoSMP.onServerStopping()
    }

    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (!ChronoSMP.canPlayerJoin(event.uniqueId)) {
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                Component.text("Your time quota has been depleted! Come back later.")
            )
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        ChronoSMP.onPlayerJoin(PaperPlatformPlayer(event.player))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        ChronoSMP.onPlayerDisconnect(PaperPlatformPlayer(event.player))
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val directKiller = event.entity.killer
        if (directKiller == null) return
        ChronoSMP.onPlayerKill(PaperPlatformPlayer(directKiller), PaperPlatformPlayer(event.entity))
    }

    @EventHandler
    fun onAdvancementDone(event: PlayerAdvancementDoneEvent) {
        val advancement = event.advancement.key.key
        val type = event.advancement.display?.frame()?.let { frame ->
            when (frame) {
                AdvancementDisplay.Frame.TASK -> "task"
                AdvancementDisplay.Frame.GOAL -> "goal"
                AdvancementDisplay.Frame.CHALLENGE -> "challenge"
                else -> null
            }
        } ?: return

        ChronoSMP.onAdvancementCompleted(
            PaperPlatformPlayer(event.player),
            advancement,
            type
        )
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val executor = if (sender is Player) PaperPlatformPlayer(sender) else PaperPlatformCommandSender(sender)
        val player = executor as? PaperPlatformPlayer
        val server = PaperPlatformServer(Bukkit.getServer())

        if (args.isEmpty()) return handled { ChronoSMP.chronoCommand.executeHelp(executor) }

        return when (args[0].lowercase()) {
            "balance" -> {
                when (args.size) {
                    1 -> if (player != null) {
                        handled { ChronoSMP.chronoCommand.executeBalance(player, player) }
                    } else {
                        sender.sendMessage("The console doesn't have a balance, add a players username to check their time")
                        true
                    }
                    2 -> handled { ChronoSMP.chronoCommand.executeBalanceByName(executor, args[1], server) }
                    else -> handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                }
            }
            "list" -> {
                when (args.size) {
                    1 -> handled { ChronoSMP.chronoCommand.executeList(executor, server, "all") }
                    2 -> if (args[1].equals("all", true) || args[1].equals("online", true)) {
                        handled { ChronoSMP.chronoCommand.executeList(executor, server, args[1]) }
                    } else handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                    else -> handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                }
            }
            "transfer" -> {
                if (player == null) {
                    sender.sendMessage("§cTransfer can only be used by a player.")
                    true
                } else {
                    val amountSeconds = args.getOrNull(2)?.let(TimeAmount::parseSeconds)
                    if (args.size != 3 || amountSeconds == null || amountSeconds <= 0) {
                        handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                    } else handled { ChronoSMP.chronoCommand.executeTransfer(player, args[1], server, amountSeconds) }
                }
            }
            "add" -> {
                val amountSeconds = args.getOrNull(2)?.let(TimeAmount::parseSeconds)
                if (args.size != 3 || amountSeconds == null || amountSeconds <= 0)
                    handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                else handled { ChronoSMP.chronoCommand.executeAdd(executor, args[1], server, amountSeconds) }
            }
            "remove" -> {
                val amountSeconds = args.getOrNull(2)?.let(TimeAmount::parseSeconds)
                if (args.size != 3 || amountSeconds == null || amountSeconds <= 0)
                    handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                else handled { ChronoSMP.chronoCommand.executeRemove(executor, args[1], server, amountSeconds) }
            }
            "set" -> {
                val amountSeconds = args.getOrNull(2)?.let(TimeAmount::parseSeconds)
                if (args.size != 3 || amountSeconds == null)
                    handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                else handled { ChronoSMP.chronoCommand.executeSet(executor, args[1], server, amountSeconds) }
            }
            "rushhour" -> {
                if (args.size < 2) {
                    handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                } else when (args[1].lowercase()) {
                    "start" -> {
                        val minutes = args.getOrNull(2)?.toLongOrNull()
                        if (args.size != 3 || minutes == null || minutes < 1)
                            handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                        else handled { ChronoSMP.chronoCommand.executeRushHourStart(executor, server, minutes) }
                    }
                    "end" -> if (args.size == 2)
                        handled { ChronoSMP.chronoCommand.executeRushHourEnd(executor, server) }
                    else handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                    else -> handled { ChronoSMP.chronoCommand.executeHelp(executor) }
                }
            }
            "help" -> handled { ChronoSMP.chronoCommand.executeHelp(executor) }
            "reload" -> if (args.size == 1) handled {
                ChronoSMP.chronoCommand.executeReload(executor, ChronoSMP::reloadConfiguration)
            } else handled { ChronoSMP.chronoCommand.executeHelp(executor) }
            else -> {
                handled { ChronoSMP.chronoCommand.executeHelp(executor) }
            }
        }
    }

    private inline fun handled(action: () -> Int): Boolean {
        action()
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): MutableList<String> {
        if (command.name != "chrono") return mutableListOf()
        if (args.isEmpty()) return mutableListOf()

        val query = args.lastOrNull()?.lowercase() ?: ""
        val suggestions = when (args.size) {
            1 -> listOf("balance", "list", "transfer", "add", "remove", "set", "rushhour", "reload", "help")
            2 -> when (args[0].lowercase()) {
                "balance", "transfer", "add", "remove", "set" -> {
                    (Bukkit.getOnlinePlayers().map { it.name } + ChronoSMP.dataManager.getAll().mapNotNull { it.username.ifBlank { null } })
                        .distinct()
                }
                "list" -> listOf("all", "online")
                "rushhour" -> listOf("start", "end")
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "rushhour" -> when (args[1].lowercase()) {
                    "start" -> mutableListOf("1", "30", "60", "120").sortedBy { it.toLong() }
                    else -> emptyList()
                }
                else -> emptyList()
            }
            else -> emptyList()
        }

        return suggestions
            .filter { it.lowercase().startsWith(query) }
            .toMutableList()
    }
}
