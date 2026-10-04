package com.chronosmp.platform

import java.util.UUID

interface PlatformPlayer : PlatformCommandSender {
    val uuid: UUID

    fun sendMessage(message: String, actionBar: Boolean)
    fun disconnect(message: String)
    fun applyEffect(effectName: String, durationTicks: Int, amplifier: Int = 0)
}
