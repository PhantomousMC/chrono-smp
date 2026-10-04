package com.chronosmp

import com.chronosmp.commands.ChronoCommand
import com.chronosmp.config.ModConfigManager
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.events.AdvancementHandler
import com.chronosmp.events.PlayerJoinHandler
import com.chronosmp.events.PvPTransferHandler
import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformServer
import com.chronosmp.systems.BossbarTracker
import com.chronosmp.systems.RushHourManager
import com.chronosmp.systems.QuotaTracker
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ChronoSMP {
    const val MOD_ID = "chrono-smp"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    var currentServer: PlatformServer? = null

    private lateinit var configManager: ModConfigManager
    lateinit var dataManager: PlayerDataManager
    private lateinit var happyHourManager: RushHourManager
    private lateinit var bossbarTracker: BossbarTracker
    private lateinit var quotaTracker: QuotaTracker
    private lateinit var playerJoinHandler: PlayerJoinHandler
    lateinit var pvpTransferHandler: PvPTransferHandler
    private lateinit var advancementHandler: AdvancementHandler
    lateinit var chronoCommand: ChronoCommand
    lateinit var happyHourManagerRef: RushHourManager
    lateinit var bossbarTrackerRef: BossbarTracker

    private val autoSaveTickCounter = AtomicInteger(0)
    private const val AUTO_SAVE_INTERVAL_TICKS = 20 * 60 * 5

    fun initialize(baseDir: Path? = null) {
        LOGGER.info("Initializing ChronoSMP - Time Quota System")

        val configDir = (baseDir ?: Paths.get("plugins", MOD_ID).normalize()).normalize()
        val configFile = configDir.resolve("config.yml")
        Files.createDirectories(configDir)
        configManager = ModConfigManager(configFile, LOGGER)
        configManager.load()

        val dataFile = configDir.resolve("player-data.json")
        dataManager = PlayerDataManager(dataFile, LOGGER, configManager.config)

        happyHourManager = RushHourManager()
        happyHourManagerRef = happyHourManager
        bossbarTracker = BossbarTracker(happyHourManager, LOGGER, configManager.config.rushHourGlowing)
        bossbarTrackerRef = bossbarTracker
        quotaTracker = QuotaTracker(dataManager, LOGGER, happyHourManager)
        playerJoinHandler = PlayerJoinHandler(dataManager, bossbarTracker, LOGGER)
        pvpTransferHandler = PvPTransferHandler(dataManager, happyHourManager, configManager.config, LOGGER)
        advancementHandler = AdvancementHandler(dataManager, LOGGER)
        chronoCommand = ChronoCommand(dataManager, happyHourManager, bossbarTracker, configManager.config, LOGGER)

        LOGGER.info("ChronoSMP initialized successfully!")
    }

    /** Reload config.yml and apply the new values to all active runtime components. */
    fun reloadConfiguration() {
        configManager.load()
        val reloadedConfig = configManager.config
        dataManager.config = reloadedConfig
        pvpTransferHandler.config = reloadedConfig
        bossbarTracker.glowingEnabled = reloadedConfig.rushHourGlowing
        chronoCommand.updateConfig(reloadedConfig)
        LOGGER.info("ChronoSMP configuration reloaded")
    }

    fun onServerStarted(server: PlatformServer) {
        currentServer = server
        LOGGER.info("Server started - loading player data")
        dataManager.load()
    }

    fun onServerStopping() {
        LOGGER.info("Server stopping - saving player data")
        dataManager.save()
        currentServer = null
    }

    fun onTick(server: PlatformServer) {
        quotaTracker.onServerTick(server)
        bossbarTracker.onServerTick(server)

        val ticks = autoSaveTickCounter.incrementAndGet()
        if (ticks >= AUTO_SAVE_INTERVAL_TICKS) {
            autoSaveTickCounter.set(0)
            LOGGER.info("Auto-saving player data")
            dataManager.save()
        }
    }

    fun onPlayerJoin(player: PlatformPlayer) {
        playerJoinHandler.onPlayerJoin(player)
    }

    fun canPlayerJoin(uuid: UUID): Boolean {
        return dataManager.canJoinWithQuota(uuid)
    }

    fun onPlayerDisconnect(player: PlatformPlayer) {
        playerJoinHandler.onPlayerDisconnect(player)
    }

    fun onPlayerKill(killer: PlatformPlayer, victim: PlatformPlayer) {
        pvpTransferHandler.onPlayerKill(killer, victim)
    }

    fun onAdvancementCompleted(player: PlatformPlayer, advancementId: String, advancementType: String?) {
        advancementHandler.onAdvancementCompleted(player, advancementId, advancementType)
    }
}
