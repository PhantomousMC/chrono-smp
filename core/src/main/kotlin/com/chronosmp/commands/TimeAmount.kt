package com.chronosmp.commands

object TimeAmount {
    fun parseSeconds(value: String): Long? {
        val input = value.trim().lowercase()
        if (input.isEmpty()) return null

        val suffix = input.last()
        val (number, multiplier) = when (suffix) {
            's' -> input.dropLast(1) to 1L
            'm' -> input.dropLast(1) to 60L
            'h' -> input.dropLast(1) to 3_600L
            'd' -> input.dropLast(1) to 86_400L
            else -> input to 60L
        }
        val amount = number.toLongOrNull()?.takeIf { it >= 0 } ?: return null

        return try {
            Math.multiplyExact(amount, multiplier)
        } catch (_: ArithmeticException) {
            null
        }
    }
}