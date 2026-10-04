package com.chronosmp.commands

import com.chronosmp.config.ModConfig
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.commands.ChronoPermissions
import com.chronosmp.platform.PermissionDefault
import com.chronosmp.platform.PlatformCommandSender
import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformServer
import com.chronosmp.systems.BossbarTracker
import com.chronosmp.systems.RushHourManager
import org.slf4j.Logger

/** Handles /chrono command logic in a runtime-neutral way. */
class ChronoCommand(
    private val dataManager: PlayerDataManager,
    private val happyHourManager: RushHourManager,
    private val bossbarTracker: BossbarTracker,
    private var config: ModConfig,
    private val logger: Logger
) {

    private fun requirePermission(
        sender: PlatformCommandSender,
        permission: String,
        default: PermissionDefault
    ): Boolean {
        if (hasPermission(sender, permission, default)) {
            return true
        }

        sender.sendMessage("§cYou don't have permission to use this command.")
        return false
    }

    private fun hasPermission(
        sender: PlatformCommandSender,
        permission: String,
        default: PermissionDefault
    ): Boolean = (default == PermissionDefault.OPERATOR &&
        sender.hasPermission(ChronoPermissions.ADMIN, PermissionDefault.OPERATOR)) ||
        sender.hasPermission(permission, default)

    /** Display only commands the sender is currently permitted to use. */
    fun executeHelp(sender: PlatformCommandSender): Int {
        if (!requirePermission(sender, ChronoPermissions.HELP, PermissionDefault.EVERYONE)) return 0

        val entries = listOf(
            Triple(ChronoPermissions.BALANCE, PermissionDefault.EVERYONE, "/chrono balance — View your or another player's remaining quota."),
            Triple(ChronoPermissions.LIST, PermissionDefault.EVERYONE, "/chrono list — List player quotas."),
            Triple(ChronoPermissions.TRANSFER, PermissionDefault.EVERYONE, "/chrono transfer <player> <amount> — Transfer quota; suffixes s/m/h/d are supported, bare amounts mean minutes."),
            Triple(ChronoPermissions.HELP, PermissionDefault.EVERYONE, "/chrono help — Show this list."),
            Triple(ChronoPermissions.ADD, PermissionDefault.OPERATOR, "/chrono add <player> <amount> — Add quota; suffixes s/m/h/d are supported, bare amounts mean minutes."),
            Triple(ChronoPermissions.REMOVE, PermissionDefault.OPERATOR, "/chrono remove <player> <amount> — Remove quota; suffixes s/m/h/d are supported, bare amounts mean minutes."),
            Triple(ChronoPermissions.SET, PermissionDefault.OPERATOR, "/chrono set <player> <amount> — Set quota; suffixes s/m/h/d are supported, bare amounts mean minutes."),
            Triple(ChronoPermissions.RUSHHOUR_START, PermissionDefault.OPERATOR, "/chrono rushhour start — Start rush hour."),
            Triple(ChronoPermissions.RUSHHOUR_END, PermissionDefault.OPERATOR, "/chrono rushhour end — End rush hour."),
            Triple(ChronoPermissions.RELOAD, PermissionDefault.OPERATOR, "/chrono reload — Reload configuration from config.yml.")
        )

        sender.sendMessage("§6Chrono commands:")
        entries.filter { (permission, default, _) -> hasPermission(sender, permission, default) }
            .forEach { (_, _, description) -> sender.sendMessage("§e$description") }
        return 1
    }

    /** Reload configuration and replace the runtime's active config references. */
    fun executeReload(sender: PlatformCommandSender, reload: () -> Unit): Int {
        if (!requirePermission(sender, ChronoPermissions.RELOAD, PermissionDefault.OPERATOR)) return 0
        return try {
            reload()
            sender.sendMessage("§aChrono configuration reloaded from config.yml.")
            1
        } catch (exception: Exception) {
            logger.error("Failed to reload Chrono configuration", exception)
            sender.sendMessage("§cFailed to reload Chrono configuration. See the server log.")
            0
        }
    }

    fun updateConfig(updatedConfig: ModConfig) {
        config = updatedConfig
    }

    /** Execute the transfer subcommand */
    fun executeTransfer(sender: PlatformPlayer, targetName: String, server: PlatformServer, amountSeconds: Long): Int {
        if (!requirePermission(sender, ChronoPermissions.TRANSFER, PermissionDefault.EVERYONE)) return 0
        if (amountSeconds <= 0) {
            sender.sendMessage("§cTransfer amount must be greater than zero.")
            return 0
        }

        val (targetPlayer, targetData) = dataManager.resolvePlayer(targetName, server)

        if (targetData == null) {
            sender.sendMessage("§cPlayer '$targetName' not found.")
            return 0
        }

        if (sender.uuid == targetData.uuid) {
            sender.sendMessage("§cYou cannot transfer quota to yourself.")
            return 0
        }

        val senderData = dataManager.getOrCreate(sender.uuid)

        if (senderData.remainingTimeSeconds < amountSeconds) {
            sender.sendMessage("§cYou don't have enough quota. You have ${senderData.formatRemainingTime()} remaining.")
            return 0
        }

        val wasRevived = targetData.remainingTimeSeconds == 0L
        val transferred = senderData.transferQuotaTo(targetData, amountSeconds)
        val transferredFormatted = formatSeconds(transferred)
        val displayName = if (targetData.username.isNotEmpty()) targetData.username else targetName

        sender.sendMessage("§aTransferred $transferredFormatted to $displayName. You have ${senderData.formatRemainingTime()} remaining.")

        if (targetPlayer != null) {
            targetPlayer.sendMessage("§a+$transferredFormatted received from ${sender.name}. You have ${targetData.formatRemainingTime()} remaining.")
        } else {
            targetData.pendingNotifications.add(
                "§aWhile you were offline, §e${sender.name}§a transferred §e$transferredFormatted§a to you! You now have §e${targetData.formatRemainingTime()}§a remaining."
            )
            if (wasRevived) {
                targetData.pendingNotifications.add("§aYou were revived! You can now rejoin the server.")
                sender.sendMessage("§a✦ $displayName has been revived and can now rejoin the server!")
            }
        }

        logger.info("Quota transfer: ${sender.name} -> $displayName, $transferredFormatted ($transferred s)")
        dataManager.save()
        return 1
    }

    private fun formatSeconds(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format("%dh %dm %ds", hours, minutes, secs)
            minutes > 0 -> String.format("%dm %ds", minutes, secs)
            else -> String.format("%ds", secs)
        }
    }

    fun executeList(executor: PlatformCommandSender, server: PlatformServer, scope: String = "all"): Int {
        if (!requirePermission(executor, ChronoPermissions.LIST, PermissionDefault.EVERYONE)) return 0

        val allPlayers = dataManager.getAll()

        if (allPlayers.isEmpty()) {
            executor.sendMessage("§cNo player data available.")
            return 1
        }

        val filteredPlayers = when (scope.lowercase()) {
            "online" -> allPlayers.filter { server.getPlayer(it.uuid) != null }
            else -> allPlayers
        }

        if (filteredPlayers.isEmpty()) {
            executor.sendMessage("§cNo players found for scope: $scope")
            return 1
        }

        val playerList = filteredPlayers
            .map { data ->
                val playerName = server.getPlayer(data.uuid)?.name ?: data.username.ifEmpty { data.uuid.toString().substring(0, 8) }
                Pair(playerName, data)
            }
            .sortedBy { it.first }

        for ((name, data) in playerList) {
            val timeStr = if (data.remainingTimeSeconds == 0L) "§c${data.formatRemainingTime()}" else "§a${data.formatRemainingTime()}"
            executor.sendMessage("§f$name: $timeStr")
        }

        return 1
    }

    fun executeBalance(executor: PlatformPlayer, target: PlatformPlayer): Int {
        if (!requirePermission(executor, ChronoPermissions.BALANCE, PermissionDefault.EVERYONE)) return 0

        val playerData = dataManager.get(target.uuid)

        if (playerData == null) {
            executor.sendMessage("§c${target.name} has no quota data yet.")
            return 0
        }

        val message = if (executor.uuid == target.uuid) {
            "§fYour remaining time: §a${playerData.formatRemainingTime()}"
        } else {
            "§f${target.name}'s remaining time: §a${playerData.formatRemainingTime()}"
        }

        executor.sendMessage(message)
        return 1
    }

    fun executeBalanceByName(executor: PlatformCommandSender, targetName: String, server: PlatformServer): Int {
        if (!requirePermission(executor, ChronoPermissions.BALANCE, PermissionDefault.EVERYONE)) return 0

        val (_, targetData) = dataManager.resolvePlayer(targetName, server)

        if (targetData == null) {
            executor.sendMessage("§cPlayer '$targetName' not found.")
            return 0
        }

        val displayName = if (targetData.username.isNotEmpty()) targetData.username else targetName
        executor.sendMessage("§f$displayName's remaining time: §a${targetData.formatRemainingTime()}")
        return 1
    }

    fun executeSet(executor: PlatformCommandSender, targetName: String, server: PlatformServer, amountSeconds: Long): Int {
        if (!requirePermission(executor, ChronoPermissions.SET, PermissionDefault.OPERATOR)) return 0

        if (amountSeconds < 0) {
            executor.sendMessage("§cTime must be 0 or greater seconds.")
            return 0
        }

        val (targetPlayer, targetData) = dataManager.resolvePlayer(targetName, server)
        if (targetData == null) {
            executor.sendMessage("§cPlayer '$targetName' not found.")
            return 0
        }

        val displayName = if (targetData.username.isNotEmpty()) targetData.username else targetName
        val before = targetData.formatRemainingTime()
        targetData.remainingTimeSeconds = amountSeconds
        val after = targetData.formatRemainingTime()

        executor.sendMessage("§aSet $displayName's quota to ${formatSeconds(amountSeconds)}. Before: $before → After: $after")

        if (targetPlayer != null) {
            targetPlayer.sendMessage("§aAn admin set your quota to $after")
        } else {
            targetData.pendingNotifications.add("§aAn admin set your quota to §e$after§a while you were offline.")
        }

        logger.info("Admin quota set: ${executor.name} set $displayName to ${formatSeconds(amountSeconds)} ($before → $after)")
        dataManager.save()
        return 1
    }

    fun executeAdd(executor: PlatformCommandSender, targetName: String, server: PlatformServer, amountSeconds: Long): Int {
        if (!requirePermission(executor, ChronoPermissions.ADD, PermissionDefault.OPERATOR)) return 0
        if (amountSeconds <= 0) {
            executor.sendMessage("§cTime amount must be greater than zero.")
            return 0
        }

        val (targetPlayer, targetData) = dataManager.resolvePlayer(targetName, server)

        if (targetData == null) {
            executor.sendMessage("§cPlayer '$targetName' not found.")
            return 0
        }

        val timeBefore = targetData.formatRemainingTime()
        val wasRevived = targetData.remainingTimeSeconds == 0L
        val displayName = if (targetData.username.isNotEmpty()) targetData.username else targetName

        targetData.addTime(amountSeconds)
        val timeAfter = targetData.formatRemainingTime()

        executor.sendMessage("§aAdded ${formatSeconds(amountSeconds)} to $displayName's quota. Before: $timeBefore → After: $timeAfter")

        if (targetPlayer != null) {
            targetPlayer.sendMessage("§aAn admin added ${formatSeconds(amountSeconds)} to your quota! Your new total: $timeAfter")
        } else {
            targetData.pendingNotifications.add(
                "§aAn admin added §e${formatSeconds(amountSeconds)}§a to your quota while you were offline. Your new total: §e$timeAfter"
            )
            if (wasRevived) {
                targetData.pendingNotifications.add("§aYou were revived! You can now rejoin the server.")
                executor.sendMessage("§a✦ $displayName has been revived and can now rejoin the server!")
            }
        }

        logger.info("Admin time grant: ${executor.name} added ${formatSeconds(amountSeconds)} to $displayName's quota ($timeBefore → $timeAfter)")
        dataManager.save()
        return 1
    }

    fun executeRemove(executor: PlatformCommandSender, targetName: String, server: PlatformServer, amountSeconds: Long): Int {
        if (!requirePermission(executor, ChronoPermissions.REMOVE, PermissionDefault.OPERATOR)) return 0
        if (amountSeconds <= 0) {
            executor.sendMessage("§cTime amount must be greater than zero.")
            return 0
        }

        val (targetPlayer, targetData) = dataManager.resolvePlayer(targetName, server)

        if (targetData == null) {
            executor.sendMessage("§cPlayer '$targetName' not found.")
            return 0
        }

        val displayName = if (targetData.username.isNotEmpty()) targetData.username else targetName
        val timeBefore = targetData.formatRemainingTime()

        if (targetData.remainingTimeSeconds < amountSeconds) {
            executor.sendMessage("§c$displayName doesn't have enough quota to remove. They have $timeBefore remaining, but you tried to remove ${formatSeconds(amountSeconds)}.")
            return 0
        }

        targetData.decrementQuota(amountSeconds)
        val timeAfter = targetData.formatRemainingTime()

        executor.sendMessage("§aRemoved ${formatSeconds(amountSeconds)} from $displayName's quota. Before: $timeBefore → After: $timeAfter")

        if (targetPlayer != null) {
            targetPlayer.sendMessage("§cAn admin removed ${formatSeconds(amountSeconds)} from your quota. Your new total: $timeAfter")
        } else {
            targetData.pendingNotifications.add(
                "§cAn admin removed §e${formatSeconds(amountSeconds)}§c from your quota while you were offline. Your new total: §e$timeAfter"
            )
        }

        logger.info("Admin time removal: ${executor.name} removed ${formatSeconds(amountSeconds)} from $displayName's quota ($timeBefore → $timeAfter)")
        dataManager.save()
        return 1
    }

    fun executeRushHourStart(executor: PlatformCommandSender, server: PlatformServer, minutes: Long): Int {
        if (!requirePermission(executor, ChronoPermissions.RUSHHOUR_START, PermissionDefault.OPERATOR)) return 0

        if (minutes < 1) {
            executor.sendMessage("§cDuration must be at least 1 minute.")
            return 0
        }

        val durationSeconds = minutes * 60
        happyHourManager.start(durationSeconds)
        bossbarTracker.createBossbar(durationSeconds, server)

        val chatMessage = "§6✦ Rush Hour started! Duration: $minutes minute(s). §aNo quota burn • §bPvP transfers are ${config.pvpTransferMultiplier}x!"
        for (player in server.players) {
            player.sendMessage(chatMessage)
        }

        logger.info("Rush Hour started by ${executor.name} for $minutes minute(s)")
        return 1
    }

    fun executeRushHourEnd(executor: PlatformCommandSender, server: PlatformServer): Int {
        if (!requirePermission(executor, ChronoPermissions.RUSHHOUR_END, PermissionDefault.OPERATOR)) return 0

        happyHourManager.end()
        bossbarTracker.removeBossbarForAll(server)

        val chatMessage = "§c✦ Rush Hour ended. Quota burning has resumed."
        for (player in server.players) {
            player.sendMessage(chatMessage)
        }

        logger.info("Rush Hour ended by ${executor.name}")
        return 1
    }
}
