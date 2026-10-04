package com.chronosmp.systems

import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the rush hour state machine.
 *
 * During rush hour:
 * - Players don't burn quota
 * - PvP transfers are multiplied (configurable)
 * - A countdown bossbar is displayed to all players
 */
class RushHourManager {
    /**
     * Sealed class representing rush hour state.
     *
     * @property Inactive No rush hour is currently active
     * @property Active(endTimeEpochMs) Happy hour is active until the specified epoch milliseconds
     */
    sealed class RushHourState {
        object Inactive : RushHourState()

        data class Active(val endTimeEpochMs: Long) : RushHourState()
    }

    private val state = AtomicReference<RushHourState>(RushHourState.Inactive)

    /**
     * Start a rush hour with the given duration.
     *
     * @param durationSeconds Duration of the rush hour in seconds
     * @return The new Active state
     */
    fun start(durationSeconds: Long): RushHourState.Active {
        val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
        val newState = RushHourState.Active(endTime)
        state.set(newState)
        return newState
    }

    /**
     * End the current rush hour.
     */
    fun end() {
        state.set(RushHourState.Inactive)
    }

    /**
     * Check if rush hour is currently active.
     *
     * @return true if rush hour is active and not yet expired
     */
    fun isActive(): Boolean {
        val currentState = state.get()
        if (currentState is RushHourState.Active) {
            if (System.currentTimeMillis() < currentState.endTimeEpochMs) {
                return true
            } else {
                // Expired, transition to Inactive
                state.compareAndSet(currentState, RushHourState.Inactive)
                return false
            }
        }
        return false
    }

    /**
     * Get remaining seconds of rush hour. Auto-expires if past end time.
     *
     * @return Remaining seconds, or 0 if inactive
     */
    fun getSafeRemainingSeconds(): Long {
        if (!isActive()) return 0
        val currentState = state.get()
        return if (currentState is RushHourState.Active) {
            maxOf(0, (currentState.endTimeEpochMs - System.currentTimeMillis()) / 1000)
        } else {
            0
        }
    }

    /**
     * Get the current state without auto-expiring.
     *
     * @return The current RushHourState
     */
    fun getState(): RushHourState {
        return state.get()
    }
}
