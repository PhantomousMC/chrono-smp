package com.chronosmp.platform.paper

import com.chronosmp.platform.PlatformPlayer
import com.chronosmp.platform.PlatformCommandSender
import com.chronosmp.platform.PlatformServer
import com.chronosmp.platform.PermissionDefault
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

class PaperPlatformPlayer(private val delegate: Player) : PlatformPlayer {
    override val uuid: UUID
        get() = delegate.uniqueId

    override val name: String
        get() = delegate.name

    override fun sendMessage(message: String) {
        delegate.sendMessage(message)
    }

    override fun sendMessage(message: String, actionBar: Boolean) {
        if (actionBar) {
            delegate.sendActionBar(message)
        } else {
            delegate.sendMessage(message)
        }
    }

    override fun disconnect(message: String) {
        delegate.kickPlayer(message)
    }

    override fun hasPermission(permission: String, default: PermissionDefault): Boolean =
        delegate.hasPermission(permission)

    override fun applyEffect(effectName: String, durationTicks: Int, amplifier: Int) {
        val effectType = when (effectName.lowercase()) {
            "glowing" -> PotionEffectType.GLOWING
            else -> null
        } ?: return

        delegate.addPotionEffect(
            PotionEffect(effectType, durationTicks, amplifier, false, false)
        )
    }
}

class PaperPlatformCommandSender(private val delegate: CommandSender) : PlatformCommandSender {
    override val name: String
        get() = delegate.name

    override fun sendMessage(message: String) {
        delegate.sendMessage(message)
    }

    override fun hasPermission(permission: String, default: PermissionDefault): Boolean =
        delegate.hasPermission(permission)
}

class PaperPlatformServer(private val delegate: Server) : PlatformServer {
    override val players: List<PlatformPlayer>
        get() = delegate.onlinePlayers.map(::PaperPlatformPlayer)

    override fun getPlayer(uuid: UUID): PlatformPlayer? {
        return delegate.getPlayer(uuid)?.let(::PaperPlatformPlayer)
    }

    override fun getPlayerByName(name: String): PlatformPlayer? {
        return delegate.getPlayerExact(name)
            ?.let(::PaperPlatformPlayer)
            ?: delegate.onlinePlayers.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let(::PaperPlatformPlayer)
    }

}
