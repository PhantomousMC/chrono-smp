package com.chronosmp.platform

enum class PermissionDefault {
    EVERYONE,
    OPERATOR
}

interface PlatformCommandSender {
    val name: String

    fun sendMessage(message: String)
    fun hasPermission(permission: String, default: PermissionDefault): Boolean
}