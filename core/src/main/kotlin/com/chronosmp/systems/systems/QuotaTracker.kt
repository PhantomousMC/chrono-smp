package com.chronosmp.systems

import com.chronosmp.data.PlayerDataManager
import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformServer
import org.slf4j.Logger

/** Tracks and decrements player time quotas while they are online */
class QuotaTracker(
        private val dataManager: PlayerDataManager,
        private val logger: Logger,
        private val happyHourManager: RushHourManager,
        /**
         * Optional callback invoked after each per-second quota burn, before any kick.
         * Use this to update dependent systems (e.g. the scoreboard display) in sync
         * with the burn cycle without requiring those systems to run their own tick loop.
         */
        private val onQuotaBurned: ((PlatformPlayer) -> Unit)? = null
) {
    private var tickCounter = 0

    fun resetTickCounter() {
        tickCounter = 0
    }

    /**
     * Called on every server tick; burn quota once every 20 ticks (1 second).
     */
    fun onServerTick(server: PlatformServer) {
        tickCounter++
        if (tickCounter < 20) return

        tickCounter = 0
        processQuotaBurn(server)
    }

    /** Process quota burn for all online players */
    private fun processQuotaBurn(server: PlatformServer) {
        for (player in server.players) {
            burnQuotaForPlayer(player)
        }
    }

    /** Burn 1 second of quota for a specific player */
    private fun burnQuotaForPlayer(player: PlatformPlayer) {
        val uuid = player.uuid
        val playerData = dataManager.get(uuid) ?: return

        // Skip quota burn during rush hour
        if (happyHourManager.isActive()) {
            return
        }

        // Decrement quota by 1 second
        val stillHasTime = playerData.decrementQuota(1)
        onQuotaBurned?.invoke(player)

        // If quota depleted, kick the player
        if (!stillHasTime) {
            kickPlayerForNoQuota(player)
        }
    }

    /** Kick a player when their quota is depleted */
    private fun kickPlayerForNoQuota(player: PlatformPlayer) {
        val kickMessage = "Your time quota has been depleted! Come back later."
        player.disconnect(kickMessage)
        logger.info("Kicked player ${player.name} (${player.uuid}) - quota depleted")
    }
}
