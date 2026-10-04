package com.chronosmp.runtime

import com.chronosmp.config.ModConfig
import com.chronosmp.config.ModConfigManager
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.platform.PlatformServer
import com.chronosmp.systems.RushHourManager
import org.slf4j.Logger
import java.nio.file.Path

class ChronoRuntime(
    private val server: PlatformServer,
    private val dataFile: Path,
    private val configFile: Path,
    private val logger: Logger,
) {
    val configManager: ModConfigManager = ModConfigManager(configFile, logger)
    val config: ModConfig
        get() = configManager.config

    val dataManager: PlayerDataManager = PlayerDataManager(dataFile, logger, config)
    val happyHourManager: RushHourManager = RushHourManager()

    fun initialize() {
        configManager.load()
        dataManager.load()
    }

    fun shutdown() {
        dataManager.save()
    }
}
