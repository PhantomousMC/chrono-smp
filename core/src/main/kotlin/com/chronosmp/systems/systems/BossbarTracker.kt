package com.chronosmp.systems

import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformServer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger

/**
 * Manages the rush hour countdown display via action bar messages.
 *
 * - Displays countdown via persistent action bar messages every 1 second
 * - Shows remaining time in minutes and seconds format with visual progress ▓
 * - Automatically shows players who join during rush hour
 * - Updates frequently (1 second) for smooth countdown experience
 */
class BossbarTracker(
    private val happyHourManager: RushHourManager,
    private val logger: Logger,
    var glowingEnabled: Boolean = true,
) {
    private var tickCounter: Int = 0
    private var lastDisplayedTime: Long = -1L
    // Counts seconds while rush hour is active; used to schedule glowing effect every 10s
    private var glowSecondCounter: Int = 0
    private val playerUuidsWithBossbar = ConcurrentHashMap.newKeySet<UUID>()

    /** Update the countdown display every 1 second (20 ticks). */
    fun onServerTick(server: PlatformServer) {
        tickCounter++
        if (tickCounter < 20) return
        tickCounter = 0

        update(server)

        // Happy hour glowing effect: every 10 seconds, give all online players the glowing effect
        if (glowingEnabled && happyHourManager.isActive()) {
            glowSecondCounter++
            if (glowSecondCounter % 10 == 0) {
                giveGlowingToAll(server)
            }
        } else {
            // reset counter when not active
            glowSecondCounter = 0
        }
    }

    /** Create a bossbar for the rush hour and send to all online players. */
    fun createBossbar(durationSeconds: Long, server: PlatformServer) {
        lastDisplayedTime = durationSeconds

        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        val timeStr = if (minutes > 0) {
            "$minutes:${String.format("%02d", seconds)}"
        } else {
            "${seconds}s"
        }

        val progressBar = "§6" + "▓".repeat((durationSeconds / 6).toInt().coerceAtMost(20))
        val message = "§6▶ Rush Hour: §e$timeStr§6 $progressBar"

        for (player in server.players) {
            player.sendMessage(message, actionBar = true)
            playerUuidsWithBossbar.add(player.uuid)
        }

        logger.info("Created rush hour countdown for $durationSeconds seconds")
    }

    /** Update countdown display on all action bar-enabled players. */
    private fun update(server: PlatformServer) {
        if (!happyHourManager.isActive() || playerUuidsWithBossbar.isEmpty()) {
            if (playerUuidsWithBossbar.isNotEmpty()) {
                removeBossbarForAll(server)
            }
            return
        }

        val remaining = happyHourManager.getSafeRemainingSeconds()

        if (remaining == lastDisplayedTime) {
            return
        }

        lastDisplayedTime = remaining

        val minutes = remaining / 60
        val seconds = remaining % 60

        val progressChars = when {
            remaining > 10 -> "▓▓▓▓▓▓▓▓▓▓"
            remaining > 8 -> "▓▓▓▓▓▓▓▓▒░"
            remaining > 6 -> "▓▓▓▓▓▓▒░░░"
            remaining > 4 -> "▓▓▓▓▒░░░░░"
            remaining > 2 -> "▓▓▒░░░░░░░"
            else -> "§c▓▒░░░░░░░░"
        }

        val timeStr = if (minutes > 0) {
            "$minutes:${String.format("%02d", seconds)}"
        } else {
            "${seconds}s"
        }

        val message = if (remaining <= 5) {
            "§c▶ Rush Hour: §e$timeStr§c $progressChars"
        } else {
            "§6▶ Rush Hour: §e$timeStr§6 $progressChars"
        }

        for (player in server.players) {
            if (playerUuidsWithBossbar.contains(player.uuid)) {
                player.sendMessage(message, actionBar = true)
            }
        }
    }

    /** Apply the glowing effect to all online players for 10 seconds (200 ticks). */
    private fun giveGlowingToAll(server: PlatformServer) {
        val durationTicks = 10 * 20
        val amplifier = 0
        for (player in server.players) {
            try {
                player.applyEffect("glowing", durationTicks, amplifier)
            } catch (e: Exception) {
                logger.warn("Failed to apply glowing to player ${player.name}: ${e.message}")
            }
        }
        logger.info("Applied glowing effect to ${server.players.size} players for ${durationTicks} ticks")
    }

    /** Remove bossbar from all players. */
    fun removeBossbarForAll(server: PlatformServer) {
        for (player in server.players) {
            if (playerUuidsWithBossbar.contains(player.uuid)) {
                player.sendMessage("", actionBar = true)
            }
        }

        playerUuidsWithBossbar.clear()
        lastDisplayedTime = -1L
        logger.info("Removed rush hour countdown from all players")
    }

    /** Send bossbar to a player who just joined during rush hour. */
    fun showToPlayer(playerUuid: UUID, server: PlatformServer) {
        if (!happyHourManager.isActive() || playerUuidsWithBossbar.isEmpty()) {
            return
        }

        val player = server.getPlayer(playerUuid) ?: return
        val remaining = happyHourManager.getSafeRemainingSeconds()
        val minutes = remaining / 60
        val seconds = remaining % 60

        val timeStr = if (minutes > 0) {
            "$minutes:${String.format("%02d", seconds)}"
        } else {
            "${seconds}s"
        }

        val message = "§6▶ Rush Hour: §e$timeStr§6 remaining"
        player.sendMessage(message, actionBar = true)
        playerUuidsWithBossbar.add(playerUuid)
        logger.info("Showed rush hour countdown to joining player: $playerUuid")
    }

    /** Remove bossbar from a specific player on disconnect. */
    fun removeFromPlayer(playerUuid: UUID, server: PlatformServer) {
        if (!playerUuidsWithBossbar.contains(playerUuid)) {
            return
        }

        val player = server.getPlayer(playerUuid)
        player?.sendMessage("", actionBar = true)
        playerUuidsWithBossbar.remove(playerUuid)
    }
}


