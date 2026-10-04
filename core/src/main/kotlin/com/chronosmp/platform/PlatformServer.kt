package com.chronosmp.platform

import java.util.UUID

interface PlatformServer {
    val players: List<PlatformPlayer>
    fun getPlayer(uuid: UUID): PlatformPlayer?
    fun getPlayerByName(name: String): PlatformPlayer?
}
