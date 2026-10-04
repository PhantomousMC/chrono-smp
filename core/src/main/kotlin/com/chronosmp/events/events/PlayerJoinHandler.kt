package com.chronosmp.events

import com.chronosmp.ChronoSMP
import com.chronosmp.data.AllotmentResult
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformServer
import com.chronosmp.systems.BossbarTracker
import org.slf4j.Logger

/** Handles player join events for quota management */
class PlayerJoinHandler(private val dataManager: PlayerDataManager, private val bossbarTracker: BossbarTracker, private val logger: Logger) {
    /** Handle player join - create initial data or grant allotment if eligible */
    fun onPlayerJoin(player: PlatformPlayer) {
        val uuid = player.uuid
        val isNewPlayer = !dataManager.exists(uuid)

        if (isNewPlayer) {
            // New player - get initial quota
            val playerData = dataManager.getOrCreate(uuid)
            val nextAllotment = dataManager.formatDurationHuman(dataManager.getSecondsUntilNextAllotment(uuid))
            player.sendMessage("§aWelcome! You have been granted ${playerData.formatRemainingTime()} of playtime. Next allotment in $nextAllotment.")
            logger.info("New player ${player.name} ($uuid) joined with initial quota")
        } else {
            // Existing player - check for allotment
            when (val result = dataManager.checkAndGrantAllotment(uuid)) {
                is AllotmentResult.Granted -> {
                    val nextAllotment = dataManager.formatDurationHuman(dataManager.getSecondsUntilNextAllotment(uuid))
                    player.sendMessage("§aAllotment granted! You now have ${result.newTotal} of playtime. Next allotment in $nextAllotment.")
                    logger.info("Granted allotment to ${player.name} ($uuid)")
                }
                is AllotmentResult.NotEligible -> {
                    val nextAllotment = dataManager.formatDurationHuman(dataManager.getSecondsUntilNextAllotment(uuid))
                    player.sendMessage("§7Welcome back! You have ${result.currentTotal} of playtime remaining. Next allotment in $nextAllotment.")
                }
            }
        }

        // Save immediately after any quota changes
        dataManager.save()

        // Update stored username (handles new players and name changes)
        val playerData = dataManager.get(uuid)!!
        playerData.username = player.name

        // Deliver any pending notifications queued while the player was offline
        if (playerData.pendingNotifications.isNotEmpty()) {
            for (msg in playerData.pendingNotifications) {
                player.sendMessage(msg)
            }
            playerData.pendingNotifications.clear()
            dataManager.save()
        }

        // Show bossbar if rush hour is active
        val server = ChronoSMP.currentServer
        if (server != null) {
            bossbarTracker.showToPlayer(uuid, server)
        }
    }

    /** Handle player disconnect - save data and clean up bossbar */
    fun onPlayerDisconnect(player: PlatformPlayer) {
        logger.info("Player ${player.name} (${player.uuid}) disconnected")
        // Clear awarded advancements to free memory
        dataManager.get(player.uuid)?.clearAwardedAdvancements()
        dataManager.save()
        // Remove bossbar from player
        val server = ChronoSMP.currentServer
        if (server != null) {
            bossbarTracker.removeFromPlayer(player.uuid, server)
        }
    }
}
