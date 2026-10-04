package com.chronosmp

import com.chronosmp.data.PlayerDataManager
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

class ChronoExpansion(
    private val dataManager: PlayerDataManager
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "chrono"

    override fun getAuthor(): String = "YourName"

    override fun getVersion(): String = "1.0.0"

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""

        val playerData = dataManager.get(player.uniqueId) ?: return "00:00:00"

        return when (params) {
            "balance" -> playerData.remainingTimeSeconds.toString()
            "balance_formatted" -> playerData.formatRemainingTime()
            else -> null
        }
    }
}