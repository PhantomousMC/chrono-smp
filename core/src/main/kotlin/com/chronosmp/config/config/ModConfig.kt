package com.chronosmp.config

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.slf4j.Logger

/**
 * Mod configuration loaded from config.yml.
 *
 * @property initialQuotaSeconds Quota granted to new players on first join (default: 8 hours)
 * @property periodicAllotmentSeconds Quota granted at the start of each allotment period (default:
 *   2 hours)
 * @property pvpTransferSeconds Quota transferred from victim to killer on a PvP kill (default: 1
 *   hour)
 * @property allotmentPeriodLength How many seconds must pass before the next allotment is granted
 *   (default: 1 day)
 */
@Serializable
data class ModConfig(
        val initialQuotaSeconds: Long = 8L * 60 * 60,
        val periodicAllotmentSeconds: Long = 2L * 60 * 60,
        val pvpTransferSeconds: Long = 1L * 60 * 60,
        val allotmentPeriodLength: Long = 24L * 60 * 60,
        val advancementTaskSeconds: Long = 15L * 60,
        val advancementGoalSeconds: Long = 30L * 60,
        val advancementChallengeSeconds: Long = 60L * 60,
        val pvpTransferMultiplier: Double = 2.0,
        val rushHourGlowing: Boolean = true
)

/** Loads and saves [ModConfig] to/from YAML, creating defaults when absent. */
class ModConfigManager(private val configFile: Path, private val logger: Logger) {
    private val legacyJsonFile = configFile.resolveSibling("config.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val yamlReader = Yaml(SafeConstructor(LoaderOptions()))
    private val yamlWriter = Yaml(DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        indent = 2
    })

    var config: ModConfig = ModConfig()
        private set

    /** Load config from disk, or create a default config file if none exists. */
    fun load() {
        try {
            val parent = configFile.parent
            if (parent != null) {
                Files.createDirectories(parent)
            }

            if (Files.exists(configFile)) {
                val content = Files.readString(configFile)
                if (content.isBlank()) {
                    logger.warn("Config file is blank at $configFile; recreating with defaults")
                    config = ModConfig()
                    save()
                    return
                }

                try {
                    config = decodeYaml(content)
                } catch (decodeException: Exception) {
                    logger.warn("Invalid config at $configFile; recreating with defaults", decodeException)
                    config = ModConfig()
                    save()
                    return
                }

                logger.info(
                        "Loaded config: STARTING_TIME=${config.initialQuotaSeconds}, " +
                            "RECEIVED_TIME=${config.periodicAllotmentSeconds}, " +
                            "KILL_TRANSFER_AMOUNT=${config.pvpTransferSeconds}, " +
                            "TIME_RECEIVE_DURATION=${config.allotmentPeriodLength}, " +
                            "ADVANCEMENT_TASK=${config.advancementTaskSeconds}, " +
                            "ADVANCEMENT_GOAL=${config.advancementGoalSeconds}, " +
                            "ADVANCEMENT_CHALLENGE=${config.advancementChallengeSeconds}, " +
                                "RUSHHOUR_KILL_MULTIPLIER=${config.pvpTransferMultiplier}, " +
                                "RUSHHOUR_GLOWING=${config.rushHourGlowing}"
                )
            } else if (Files.exists(legacyJsonFile)) {
                val content = Files.readString(legacyJsonFile)
                config = try {
                    json.decodeFromString<ModConfig>(content)
                } catch (decodeException: Exception) {
                    logger.warn("Invalid legacy config at $legacyJsonFile; creating YAML with defaults", decodeException)
                    ModConfig()
                }
                logger.info("Imported legacy JSON config from $legacyJsonFile; writing $configFile")
                save()
            } else {
                logger.info(
                        "No config file found at $configFile, creating with defaults"
                )
                config = ModConfig()
                save()
            }
        } catch (e: Exception) {
            logger.error("Failed to load config, using defaults", e)
            config = ModConfig()
            try {
                save()
            } catch (_: Exception) {
            }
        }
    }

    /** Save the current config to disk. */
    fun save() {
        try {
            Files.createDirectories(configFile.parent)
            Files.writeString(configFile, encodeYaml(config))
        } catch (e: Exception) {
            logger.error("Failed to save config", e)
        }
    }

    private fun decodeYaml(content: String): ModConfig {
        val values = yamlReader.load<Any?>(content) as? Map<*, *>
            ?: throw IllegalArgumentException("Config must be a YAML mapping")
        val defaults = ModConfig()

        return ModConfig(
            initialQuotaSeconds = values.longValue("STARTING_TIME", defaults.initialQuotaSeconds),
            periodicAllotmentSeconds = values.longValue("RECEIVED_TIME", defaults.periodicAllotmentSeconds),
            pvpTransferSeconds = values.longValue("KILL_TRANSFER_AMOUNT", defaults.pvpTransferSeconds),
            allotmentPeriodLength = values.longValue("TIME_RECEIVE_DURATION", defaults.allotmentPeriodLength),
            advancementTaskSeconds = values.longValue("ADVANCEMENT_TASK", defaults.advancementTaskSeconds),
            advancementGoalSeconds = values.longValue("ADVANCEMENT_GOAL", defaults.advancementGoalSeconds),
            advancementChallengeSeconds = values.longValue("ADVANCEMENT_CHALLENGE", defaults.advancementChallengeSeconds),
            pvpTransferMultiplier = values.doubleValue("RUSHHOUR_KILL_MULTIPLIER", defaults.pvpTransferMultiplier),
            rushHourGlowing = values.booleanValue("RUSHHOUR_GLOWING", defaults.rushHourGlowing)
        )
    }

    private fun encodeYaml(config: ModConfig): String = yamlWriter.dump(
        linkedMapOf(
            "STARTING_TIME" to config.initialQuotaSeconds,
            "RECEIVED_TIME" to config.periodicAllotmentSeconds,
            "TIME_RECEIVE_DURATION" to config.allotmentPeriodLength,
            "KILL_TRANSFER_AMOUNT" to config.pvpTransferSeconds,
            "RUSHHOUR_KILL_MULTIPLIER" to config.pvpTransferMultiplier,
            "ADVANCEMENT_TASK" to config.advancementTaskSeconds,
            "ADVANCEMENT_GOAL" to config.advancementGoalSeconds,
            "ADVANCEMENT_CHALLENGE" to config.advancementChallengeSeconds,
            "RUSHHOUR_GLOWING" to config.rushHourGlowing
        )
    )

    private fun Map<*, *>.longValue(key: String, default: Long): Long {
        if (!containsKey(key)) return default
        return (this[key] as? Number)?.toLong()
            ?: throw IllegalArgumentException("Config value '$key' must be a number")
    }

    private fun Map<*, *>.doubleValue(key: String, default: Double): Double {
        if (!containsKey(key)) return default
        return (this[key] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("Config value '$key' must be a number")
    }

    private fun Map<*, *>.booleanValue(key: String, default: Boolean): Boolean {
        if (!containsKey(key)) return default
        return this[key] as? Boolean
            ?: throw IllegalArgumentException("Config value '$key' must be true or false")
    }
}
