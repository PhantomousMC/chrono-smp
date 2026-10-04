package com.chronosmp.platform.fabric

import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformCommandSender
import com.chronosmp.platform.PlatformServer
import com.chronosmp.platform.PermissionDefault
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.network.chat.Component
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class FabricPlatformPlayer(private val delegate: ServerPlayer) : PlatformPlayer {
    override val uuid: UUID
        get() = delegate.uuid

    override val name: String
        get() = delegate.name.string

    override fun sendMessage(message: String) {
        delegate.sendSystemMessage(Component.literal(message))
    }

    override fun sendMessage(message: String, actionBar: Boolean) {
        delegate.sendSystemMessage(Component.literal(message), actionBar)
    }

    override fun disconnect(message: String) {
        delegate.connection.disconnect(Component.literal(message))
    }

    override fun hasPermission(permission: String, default: PermissionDefault): Boolean = when (default) {
        PermissionDefault.EVERYONE -> Permissions.check(delegate, permission, true)
        PermissionDefault.OPERATOR -> Permissions.check(delegate, permission, PermissionLevel.ADMINS)
    }

    override fun applyEffect(effectName: String, durationTicks: Int, amplifier: Int) {
        val effect = when (effectName.lowercase()) {
            "glowing" -> net.minecraft.world.effect.MobEffects.GLOWING
            else -> null
        } ?: return

        delegate.addEffect(
            net.minecraft.world.effect.MobEffectInstance(
                effect,
                durationTicks,
                amplifier,
                false,
                false
            )
        )
    }
}

class FabricPlatformCommandSender(
    private val delegate: CommandSourceStack,
    private val bypassPermissions: Boolean
) : PlatformCommandSender {
    override val name: String = if (bypassPermissions) "Server console" else delegate.textName

    override fun sendMessage(message: String) {
        delegate.sendSuccess({ Component.literal(message) }, false)
    }

    override fun hasPermission(permission: String, default: PermissionDefault): Boolean {
        if (bypassPermissions) return true
        return when (default) {
            PermissionDefault.EVERYONE -> Permissions.check(delegate, permission, true)
            PermissionDefault.OPERATOR -> Permissions.check(delegate, permission, PermissionLevel.ADMINS)
        }
    }
}

class FabricPlatformServer(private val delegate: MinecraftServer) : PlatformServer {
    override val players: List<PlatformPlayer>
        get() = delegate.playerList.players.map(::FabricPlatformPlayer)

    override fun getPlayer(uuid: UUID): PlatformPlayer? {
        return delegate.playerList.getPlayer(uuid)?.let(::FabricPlatformPlayer)
    }

    override fun getPlayerByName(name: String): PlatformPlayer? {
        return delegate.playerList.players
            .firstOrNull { it.name.string.equals(name, ignoreCase = true) }
            ?.let(::FabricPlatformPlayer)
    }

}
