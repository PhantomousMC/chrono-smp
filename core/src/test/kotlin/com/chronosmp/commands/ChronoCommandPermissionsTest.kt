package com.chronosmp.commands

import com.chronosmp.config.ModConfig
import com.chronosmp.data.PlayerDataManager
import com.chronosmp.platform.PermissionDefault
import com.chronosmp.platform.PlatformCommandSender
import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformServer
import com.chronosmp.systems.BossbarTracker
import com.chronosmp.systems.QuotaSystemState
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
        val command = createContext().command
        val publicSender = TestSender()
        val deniedSender = TestSender(denied = setOf(ChronoPermissions.LIST))

        assertEquals(1, command.executeList(publicSender, EmptyServer))
        assertTrue(publicSender.messages.contains("§cNo player data available."))

        assertEquals(0, command.executeList(deniedSender, EmptyServer))
        assertTrue(deniedSender.messages.last().contains("don't have permission"))
    }

    @Test
    fun adminCommandDefaultsToOperatorsAndAllowsIndividualGrants() {
        val command = createContext().command
        val operator = TestSender(operator = true)
        val individuallyGranted = TestSender(granted = setOf(ChronoPermissions.SET))

        assertEquals(0, command.executeSet(operator, "Missing", EmptyServer, 1))
        assertEquals(0, command.executeSet(individuallyGranted, "Missing", EmptyServer, 1))
        assertTrue(operator.messages.last().contains("not found"))
        assertTrue(individuallyGranted.messages.last().contains("not found"))
    }

    @Test
    fun adminPermissionGrantsAdminCommandsButNotPublicCommands() {
        val command = createContext().command
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
        val command = createContext().command
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
        assertTrue(admin.messages.any { it.contains("/chrono start") })
        assertTrue(admin.messages.any { it.contains("/chrono stop") })
        assertTrue(admin.messages.any { it.contains("/chrono reset") })
    }

    @Test
    fun reloadRequiresOperatorPermissionAndRunsCallback() {
        val command = createContext().command
        val regular = TestSender()
        var reloadCount = 0

        assertEquals(0, command.executeReload(regular) { reloadCount++ })
        assertEquals(0, reloadCount)

        assertEquals(1, command.executeReload(TestSender(operator = true)) { reloadCount++ })
        assertEquals(1, reloadCount)
    }

    @Test
    fun individuallyGrantedAdminPermissionAppearsInHelp() {
        val command = createContext().command
        val sender = TestSender(granted = setOf(ChronoPermissions.RELOAD))

        assertEquals(1, command.executeHelp(sender))
        assertTrue(sender.messages.any { it.contains("/chrono reload") })
        assertTrue(sender.messages.none { it.contains("/chrono add") })
    }

    @Test
    fun quotaCommandsAreBlockedUntilStarted() {
        val context = createContext(started = false)
        val player = TestPlayer()
        val sender = TestSender()

        assertEquals(0, context.command.executeList(sender, EmptyServer))
        assertEquals(0, context.command.executeBalanceByName(sender, "Missing", EmptyServer))
        assertEquals(0, context.command.executeTransfer(player, "Missing", EmptyServer, 60))
        assertEquals(0, context.command.executeAdd(TestSender(operator = true), "Missing", EmptyServer, 60))
        assertTrue(sender.messages.all { it.contains("The SMP didn't start yet.") })
        assertTrue(player.messages.single().contains("The SMP didn't start yet."))
    }

    @Test
    fun startStopAndResetRequireAdminAndPersistLifecycle() {
        val context = createContext(started = false)
        val regular = TestSender()
        val adminsGroup = TestSender(granted = setOf(ChronoPermissions.ADMINS))

        assertEquals(0, context.command.executeStart(regular, EmptyServer))
        assertEquals(1, context.command.executeStart(adminsGroup, EmptyServer))
        assertTrue(context.state.isStarted())
        assertTrue(Files.exists(context.stateFile))
        val reloadedStartedState = QuotaSystemState(context.stateFile)
        reloadedStartedState.load()
        assertTrue(reloadedStartedState.isStarted())

        assertEquals(1, context.command.executeStop(adminsGroup))
        assertTrue(!context.state.isStarted())
        assertTrue(context.state.hasStarted())
        assertEquals(1, context.command.executeList(TestSender(), EmptyServer))
        val stoppedTransferSender = TestPlayer()
        assertEquals(0, context.command.executeTransfer(stoppedTransferSender, "Missing", EmptyServer, 60))
        assertTrue(stoppedTransferSender.messages.single().contains("The SMP is stopped."))
        val reloadedStoppedState = QuotaSystemState(context.stateFile)
        reloadedStoppedState.load()
        assertTrue(reloadedStoppedState.hasStarted())
        assertTrue(!reloadedStoppedState.isStarted())
        assertEquals(1, context.command.executeStart(TestSender(operator = true), EmptyServer))

        context.dataManager.getOrCreate(UUID.randomUUID()).username = "Steve"
        context.dataManager.save()
        assertEquals(1, context.command.executeReset(TestSender(operator = true)))
        assertTrue(!context.state.isStarted())
        assertEquals("{}", Files.readString(context.dataFile))

        val reloadedState = QuotaSystemState(context.stateFile)
        reloadedState.load()
        assertTrue(!reloadedState.isStarted())
        assertTrue(!reloadedState.hasStarted())
        assertTrue(!Files.exists(context.stateFile))
    }

    private fun createContext(started: Boolean = true): TestContext {
        val logger = LoggerFactory.getLogger("permission-test")
        val dataFile = Files.createTempDirectory("chrono-permission-test").resolve("player-data.json")
        val stateFile = dataFile.parent.resolve("quota-system.state")
        val dataManager = PlayerDataManager(
            dataFile,
            logger,
            ModConfig()
        )
        val state = QuotaSystemState(stateFile)
        if (started) state.start()
        val rushHourManager = RushHourManager()
        val command = ChronoCommand(
            dataManager,
            rushHourManager,
            BossbarTracker(rushHourManager, logger),
            ModConfig(),
            logger,
            state
        )
        return TestContext(command, dataManager, state, dataFile, stateFile)
    }

    private data class TestContext(
        val command: ChronoCommand,
        val dataManager: PlayerDataManager,
        val state: QuotaSystemState,
        val dataFile: java.nio.file.Path,
        val stateFile: java.nio.file.Path
    )

    private class TestPlayer : TestSender(), PlatformPlayer {
        override val uuid: UUID = UUID.randomUUID()

        override fun sendMessage(message: String, actionBar: Boolean) {
            sendMessage(message)
        }

        override fun disconnect(message: String) = Unit

        override fun applyEffect(effectName: String, durationTicks: Int, amplifier: Int) = Unit
    }

    private open class TestSender(
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