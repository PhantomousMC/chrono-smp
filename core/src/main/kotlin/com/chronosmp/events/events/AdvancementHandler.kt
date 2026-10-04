package com.chronosmp.events

import com.chronosmp.data.AdvancementGrantResult
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.platform.PlatformPlayer
import org.slf4j.Logger

/** Handles advancement completion events and grants quota time to the player */
class AdvancementHandler(private val dataManager: PlayerDataManager, private val logger: Logger) {

    fun onAdvancementCompleted(player: PlatformPlayer, advancementId: String, advancementType: String?) {
        if (advancementId.contains("/root")) {
            logger.debug("Advancement $advancementId is a root advancement, skipping time grant")
            return
        }

        val playerData = dataManager.getOrCreate(player.uuid)
        if (playerData.hasAwardedAdvancement(advancementId)) {
            logger.debug("Advancement $advancementId already awarded to ${player.name} (${player.uuid}) this session, skipping")
            return
        }

        val seconds = when (advancementType?.lowercase()) {
            "task" -> dataManager.config.advancementTaskSeconds
            "goal" -> dataManager.config.advancementGoalSeconds
            "challenge" -> dataManager.config.advancementChallengeSeconds
            else -> dataManager.config.advancementTaskSeconds
        }

        when (val result = dataManager.grantAdvancementTime(player.uuid, seconds)) {
            is AdvancementGrantResult.Granted -> {
                playerData.markAdvancementAwarded(advancementId)
                player.sendMessage("§a+${result.amountFormatted} time granted! You now have ${result.newTotal} remaining.")
                dataManager.save()
                logger.info("Granted ${result.amountFormatted} to ${player.name} (${player.uuid}) for advancement $advancementId")
            }
            AdvancementGrantResult.NoData -> {
                logger.warn("No data found for ${player.name} (${player.uuid}) when granting advancement time")
            }
        }
    }
}
