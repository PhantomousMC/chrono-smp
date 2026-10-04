package com.chronosmp.fabric

import com.chronosmp.ChronoSMP
import com.chronosmp.commands.TimeAmount
import com.chronosmp.platform.PlatformCommandSender
import com.chronosmp.platform.fabric.FabricPlatformPlayer
import com.chronosmp.platform.fabric.FabricPlatformCommandSender
import com.chronosmp.platform.fabric.FabricPlatformServer
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.commands.Commands
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object FabricChronoSMP : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        ChronoSMP.initialize()

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            ChronoSMP.onServerStarted(FabricPlatformServer(server))
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            ChronoSMP.onServerStopping()
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            ChronoSMP.onTick(FabricPlatformServer(server))
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            ChronoSMP.onPlayerJoin(FabricPlatformPlayer(handler.player))
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            ChronoSMP.onPlayerDisconnect(FabricPlatformPlayer(handler.player))
        }

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register { _, killer, killedEntity, _ ->
            if (killer is ServerPlayer && killedEntity is ServerPlayer) {
                ChronoSMP.onPlayerKill(FabricPlatformPlayer(killer), FabricPlatformPlayer(killedEntity))
            }
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(registerChronoCommand())
        }
    }

    fun onAdvancementCompleted(player: ServerPlayer, advancement: AdvancementHolder) {
        val display = advancement.value().display().orElse(null) ?: return
        val type = display.type.toString().lowercase()
        ChronoSMP.onAdvancementCompleted(FabricPlatformPlayer(player), advancement.id.toString(), type)
    }

    private fun registerChronoCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("chrono")
            .executes { context ->
                ChronoSMP.chronoCommand.executeHelp(commandSender(context.source))
            }
            .then(
                Commands.literal("start")
                    .executes { context ->
                        ChronoSMP.chronoCommand.executeStart(
                            commandSender(context.source),
                            FabricPlatformServer(context.source.server)
                        )
                    }
            )
            .then(
                Commands.literal("stop")
                    .executes { context ->
                        ChronoSMP.chronoCommand.executeStop(commandSender(context.source))
                    }
            )
            .then(
                Commands.literal("reset")
                    .executes { context ->
                        ChronoSMP.chronoCommand.executeReset(commandSender(context.source))
                    }
            )
            .then(
                Commands.literal("balance")
                    .executes { context ->
                        val player = context.source.entity as? ServerPlayer
                        if (player == null) {
                            ChronoSMP.chronoCommand.executeConsoleBalance(commandSender(context.source))
                        } else {
                            val platformPlayer = FabricPlatformPlayer(player)
                            ChronoSMP.chronoCommand.executeBalance(platformPlayer, platformPlayer)
                        }
                    }
                    .then(
                        playerArgument().executes { context ->
                            ChronoSMP.chronoCommand.executeBalanceByName(
                                commandSender(context.source),
                                StringArgumentType.getString(context, "player"),
                                FabricPlatformServer(context.source.server)
                            )
                        }
                    )
            )
            .then(
                Commands.literal("list")
                    .executes { context ->
                        ChronoSMP.chronoCommand.executeList(
                            commandSender(context.source),
                            FabricPlatformServer(context.source.server)
                        )
                    }
                    .then(
                        Commands.argument("scope", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(listOf("all", "online"), builder) }
                            .executes { context ->
                                val scope = StringArgumentType.getString(context, "scope")
                                if (scope.equals("all", true) || scope.equals("online", true)) {
                                    ChronoSMP.chronoCommand.executeList(
                                        commandSender(context.source),
                                        FabricPlatformServer(context.source.server),
                                        scope
                                    )
                                } else {
                                    ChronoSMP.chronoCommand.executeHelp(commandSender(context.source))
                                }
                            }
                    )
            )
            .then(
                Commands.literal("help")
                    .executes { context -> ChronoSMP.chronoCommand.executeHelp(commandSender(context.source)) }
                    .then(
                        Commands.argument("invalid", StringArgumentType.greedyString())
                            .executes { context -> ChronoSMP.chronoCommand.executeHelp(commandSender(context.source)) }
                    )
            )
            .then(
                Commands.literal("reload")
                    .executes { context ->
                        ChronoSMP.chronoCommand.executeReload(
                            commandSender(context.source),
                            ChronoSMP::reloadConfiguration
                        )
                    }
                    .then(
                        Commands.argument("invalid", StringArgumentType.greedyString())
                            .executes { context -> ChronoSMP.chronoCommand.executeHelp(commandSender(context.source)) }
                    )
            )
            .then(
                Commands.argument("invalid", StringArgumentType.greedyString())
                    .executes { context -> ChronoSMP.chronoCommand.executeHelp(commandSender(context.source)) }
            )
            .then(
                Commands.literal("transfer")
                    .then(
                        playerArgument().then(
                            Commands.argument("amount", StringArgumentType.word())
                                .executes { context ->
                                    val player = context.source.entity as? ServerPlayer
                                    if (player == null) {
                                        context.source.sendFailure(Component.literal("Transfer can only be used by a player."))
                                        0
                                    } else {
                                        val amountSeconds = TimeAmount.parseSeconds(
                                            StringArgumentType.getString(context, "amount")
                                        )
                                        if (amountSeconds == null || amountSeconds <= 0) {
                                            ChronoSMP.chronoCommand.executeHelp(commandSender(context.source))
                                        } else {
                                            ChronoSMP.chronoCommand.executeTransfer(
                                                FabricPlatformPlayer(player),
                                                StringArgumentType.getString(context, "player"),
                                                FabricPlatformServer(context.source.server),
                                                amountSeconds
                                            )
                                        }
                                    }
                                }
                        )
                    )
            )
            .then(adminPlayerCommand("add") { sender, target, server, minutes ->
                ChronoSMP.chronoCommand.executeAdd(sender, target, server, minutes)
            })
            .then(adminPlayerCommand("remove") { sender, target, server, minutes ->
                ChronoSMP.chronoCommand.executeRemove(sender, target, server, minutes)
            })
            .then(adminPlayerCommand("set") { sender, target, server, minutes ->
                ChronoSMP.chronoCommand.executeSet(sender, target, server, minutes)
            })
            .then(
                Commands.literal("rushhour")
                    .then(
                        Commands.literal("start")
                            .then(
                                Commands.argument("minutes", LongArgumentType.longArg())
                                    .executes { context ->
                                        ChronoSMP.chronoCommand.executeRushHourStart(
                                            commandSender(context.source),
                                            FabricPlatformServer(context.source.server),
                                            LongArgumentType.getLong(context, "minutes")
                                        )
                                    }
                            )
                    )
                    .then(
                        Commands.literal("end")
                            .executes { context ->
                                ChronoSMP.chronoCommand.executeRushHourEnd(
                                    commandSender(context.source),
                                    FabricPlatformServer(context.source.server)
                                )
                            }
                    )
            )

    private fun playerArgument() = Commands.argument("player", StringArgumentType.word())
        .suggests { _, builder -> SharedSuggestionProvider.suggest(knownPlayerNames(), builder) }

    private fun adminPlayerCommand(
        name: String,
        execute: (PlatformCommandSender, String, FabricPlatformServer, Long) -> Int
    ) = Commands.literal(name)
        .then(
            playerArgument().then(
                Commands.argument("amount", StringArgumentType.word())
                    .executes { context ->
                        val amountSeconds = TimeAmount.parseSeconds(
                            StringArgumentType.getString(context, "amount")
                        )
                        if (amountSeconds == null || (name != "set" && amountSeconds <= 0)) {
                            ChronoSMP.chronoCommand.executeHelp(commandSender(context.source))
                        } else execute(
                            commandSender(context.source),
                            StringArgumentType.getString(context, "player"),
                            FabricPlatformServer(context.source.server),
                            amountSeconds
                        )
                    }
            )
        )

    private fun commandSender(source: CommandSourceStack): PlatformCommandSender {
        val player = source.entity as? ServerPlayer
        return player?.let(::FabricPlatformPlayer)
            ?: FabricPlatformCommandSender(source, source.entity == null && source.textName == "Server")
    }

    private fun knownPlayerNames(): List<String> =
        (ChronoSMP.currentServer?.players?.map { it.name }.orEmpty() +
            ChronoSMP.dataManager.getAll().mapNotNull { it.username.ifBlank { null } })
            .distinct()
}
