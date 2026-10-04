package com.chronosmp.events

import com.chronosmp.config.ModConfig
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.data.PvPTransferResult
import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.systems.RushHourManager
import org.slf4j.Logger

/** Handles PvP kills and quota transfers */
class PvPTransferHandler(
        private val dataManager: PlayerDataManager,
        private val happyHourManager: RushHourManager,
        var config: ModConfig,
        private val logger: Logger
) {
        /** Register a kill listener from the runtime-specific bootstrap. */
        fun register(listener: (PlatformPlayer, PlatformPlayer) -> Unit) {
                logger.info("PvPTransferHandler registered (runtime-neutral bridge)")
                // The actual event binding remains in the runtime bootstrap layer.
                // The shared logic receives generic PlatformPlayer values here.
                // This keeps the gameplay logic free of Fabric/Minecraft APIs.
                _killHandler = listener
        }

        private var _killHandler: ((PlatformPlayer, PlatformPlayer) -> Unit)? = null

        fun onPlayerKill(killer: PlatformPlayer, victim: PlatformPlayer) {
                handlePlayerKill(killer, victim)
                _killHandler?.invoke(killer, victim)
        }

        /** Handle quota transfer when a player kills another player */
        private fun handlePlayerKill(killer: PlatformPlayer, victim: PlatformPlayer) {
                val multiplier = if (happyHourManager.isActive()) config.pvpTransferMultiplier else 1.0
                when (val result = dataManager.transferQuotaOnPvPKill(victim.uuid, killer.uuid, multiplier)) {
                        is PvPTransferResult.Success -> {
                                val transferredFormatted = formatSeconds(result.transferred)
                                val happyHourSuffix = if (happyHourManager.isActive()) " §b(Rush Hour 2x!)" else ""

                                killer.sendMessage("§a+$transferredFormatted from killing ${victim.name}! Total: ${result.killerRemaining}$happyHourSuffix")
                                victim.sendMessage("§c-$transferredFormatted lost to ${killer.name}! Remaining: ${result.victimRemaining}$happyHourSuffix")

                                logger.info(
                                        "PvP transfer: ${killer.name} killed ${victim.name}, " +
                                                "transferred $transferredFormatted (${result.transferred}s)" +
                                                (if (multiplier > 1.0) " [multiplied by $multiplier]" else "")
                                )

                                dataManager.save()
                        }
                        is PvPTransferResult.NoTimeAvailable -> {
                                logger.debug(
                                        "No time available to transfer from ${victim.name} to ${killer.name}"
                                )
                        }
                        is PvPTransferResult.NoData -> {
                                logger.warn(
                                        "Missing player data for PvP kill: ${killer.name} killed ${victim.name}"
                                )
                        }
                }
        }

        /** Format seconds to readable time string */
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
}
