package com.chronosmp.commands

import com.chronosmp.config.ModConfig
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.platform.PermissionDefault
import com.chronosmp.platform.PlatformCommandSender
import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformServer
import com.chronosmp.systems.BossbarTracker
import com.chronosmp.systems.RushHourManager
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory

class ChronoCommandPermissionsTest {
    @Test
    fun publicCommandsDefaultToEveryoneAndCanBeDenied() {
        val command = createCommand()
        val publicSender = TestSender()
        val deniedSender = TestSender(denied = setOf(ChronoPermissions.LIST))

        assertEquals(1, command.executeList(publicSender, EmptyServer))
        assertTrue(publicSender.messages.contains("§cNo player data available."))

        assertEquals(0, command.executeList(deniedSender, EmptyServer))
        assertTrue(deniedSender.messages.last().contains("don't have permission"))
    }

    @Test
    fun adminCommandDefaultsToOperatorsAndAllowsIndividualGrants() {
        val command = createCommand()
        val operator = TestSender(operator = true)
        val individuallyGranted = TestSender(granted = setOf(ChronoPermissions.SET))

        assertEquals(0, command.executeSet(operator, "Missing", EmptyServer, 1))
        assertEquals(0, command.executeSet(individuallyGranted, "Missing", EmptyServer, 1))
        assertTrue(operator.messages.last().contains("not found"))
        assertTrue(individuallyGranted.messages.last().contains("not found"))
    }

    @Test
    fun adminPermissionGrantsAdminCommandsButNotPublicCommands() {
        val command = createCommand()
        val administrator = TestSender(
            granted = setOf(ChronoPermissions.ADMIN),
            denied = setOf(ChronoPermissions.BALANCE, ChronoPermissions.LIST, ChronoPermissions.TRANSFER)
        )

        assertEquals(1, command.executeRushHourEnd(administrator, EmptyServer))
        assertEquals(0, command.executeList(administrator, EmptyServer))
        assertEquals(0, command.executeBalanceByName(administrator, "Missing", EmptyServer))
        assertEquals(1, command.executeHelp(administrator))
        assertTrue(administrator.messages.any { it.contains("/chrono add") })
        assertTrue(administrator.messages.none {
            it.contains("/chrono balance") || it.contains("/chrono list") || it.contains("/chrono transfer")
        })
    }

    @Test
    fun helpShowsOnlyCommandsTheSenderCanUse() {
        val command = createCommand()
        val regular = TestSender()
        val admin = TestSender(operator = true)

        assertEquals(1, command.executeHelp(regular))
        assertTrue(regular.messages.any { it.contains("/chrono balance") })
        assertTrue(regular.messages.any { it.contains("/chrono list") })
        assertTrue(regular.messages.any { it.contains("/chrono transfer") })
        assertTrue(regular.messages.any { it.contains("/chrono help") })
        assertTrue(regular.messages.none { it.contains("/chrono add") || it.contains("/chrono reload") })

        assertEquals(1, command.executeHelp(admin))
        assertTrue(admin.messages.any { it.contains("/chrono add") })
        assertTrue(admin.messages.any { it.contains("/chrono reload") })
    }

    @Test
    fun reloadRequiresOperatorPermissionAndRunsCallback() {
        val command = createCommand()
        val regular = TestSender()
        var reloadCount = 0

        assertEquals(0, command.executeReload(regular) { reloadCount++ })
        assertEquals(0, reloadCount)

        assertEquals(1, command.executeReload(TestSender(operator = true)) { reloadCount++ })
        assertEquals(1, reloadCount)
    }

    @Test
    fun individuallyGrantedAdminPermissionAppearsInHelp() {
        val command = createCommand()
        val sender = TestSender(granted = setOf(ChronoPermissions.RELOAD))

        assertEquals(1, command.executeHelp(sender))
        assertTrue(sender.messages.any { it.contains("/chrono reload") })
        assertTrue(sender.messages.none { it.contains("/chrono add") })
    }

    private fun createCommand(): ChronoCommand {
        val logger = LoggerFactory.getLogger("permission-test")
        val dataManager = PlayerDataManager(
            Files.createTempDirectory("chrono-permission-test").resolve("player-data.json"),
            logger,
            ModConfig()
        )
        val rushHourManager = RushHourManager()
        return ChronoCommand(
            dataManager,
            rushHourManager,
            BossbarTracker(rushHourManager, logger),
            ModConfig(),
            logger
        )
    }

    private class TestSender(
        private val operator: Boolean = false,
        private val granted: Set<String> = emptySet(),
        private val denied: Set<String> = emptySet()
    ) : PlatformCommandSender {
        override val name: String = "tester"
        val messages = mutableListOf<String>()

        override fun sendMessage(message: String) {
            messages.add(message)
        }

        override fun hasPermission(permission: String, default: PermissionDefault): Boolean = when {
            permission in denied -> false
            permission in granted -> true
            default == PermissionDefault.EVERYONE -> true
            else -> operator
        }
    }

    private object EmptyServer : PlatformServer {
        override val players: List<PlatformPlayer> = emptyList()

        override fun getPlayer(uuid: UUID): PlatformPlayer? = null

        override fun getPlayerByName(name: String): PlatformPlayer? = null
    }
}