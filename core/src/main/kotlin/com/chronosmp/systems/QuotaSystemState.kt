package com.chronosmp.systems

import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/** Persists whether quota gameplay has been enabled by an administrator. */
class QuotaSystemState(private val stateFile: Path) {
    private enum class Status {
        NOT_STARTED,
        STARTED,
        STOPPED
    }

    @Volatile
    private var status = Status.NOT_STARTED

    fun isStarted(): Boolean = status == Status.STARTED

    fun hasStarted(): Boolean = status != Status.NOT_STARTED

    @Synchronized
    fun load() {
        val state = try {
            Files.readString(stateFile).trim()
        } catch (_: NoSuchFileException) {
            status = Status.NOT_STARTED
            return
        }

        status = when (state) {
            STARTED_STATE -> Status.STARTED
            STOPPED_STATE -> Status.STOPPED
            else -> throw IOException("Invalid quota system state in $stateFile")
        }
    }

    @Synchronized
    fun start() {
        Files.createDirectories(stateFile.parent)
        Files.writeString(stateFile, "$STARTED_STATE\n")
        status = Status.STARTED
    }

    @Synchronized
    fun stop() {
        Files.createDirectories(stateFile.parent)
        Files.writeString(stateFile, "$STOPPED_STATE\n")
        status = Status.STOPPED
    }

    @Synchronized
    fun reset() {
        Files.deleteIfExists(stateFile)
        status = Status.NOT_STARTED
    }

    private companion object {
        const val STARTED_STATE = "started"
        const val STOPPED_STATE = "stopped"
    }
}
