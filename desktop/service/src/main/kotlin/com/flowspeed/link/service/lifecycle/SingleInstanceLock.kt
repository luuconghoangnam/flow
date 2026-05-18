package com.flowspeed.link.service.lifecycle

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * Cross-platform single-instance lock using file locks.
 *
 * Writes PID and IPC port to the lock file so other processes
 * can discover the running instance.
 */
class SingleInstanceLock(
    private val dataDir: File,
) {
    private val lockFile = File(dataDir, "service.lock")
    private var randomAccessFile: RandomAccessFile? = null
    private var fileLock: FileLock? = null

    /**
     * Attempts to acquire the exclusive lock.
     * @return true if lock acquired, false if another instance holds it.
     */
    fun tryAcquire(): Boolean {
        dataDir.mkdirs()
        return try {
            val raf = RandomAccessFile(lockFile, "rw")
            val lock = raf.channel.tryLock()
            if (lock != null) {
                randomAccessFile = raf
                fileLock = lock
                writeLockInfo()
                true
            } else {
                raf.close()
                false
            }
        } catch (e: Exception) {
            // If lock file exists but process is dead (stale lock), reclaim it
            if (isStale()) {
                lockFile.delete()
                return tryAcquire()
            }
            false
        }
    }

    /**
     * Writes the IPC port to the lock file after acquisition.
     * Called after IPC server starts and port is known.
     */
    fun writePort(ipcPort: Int) {
        randomAccessFile?.let { raf ->
            raf.seek(0)
            raf.setLength(0)
            raf.writeBytes("pid=${ProcessHandle.current().pid()}\n")
            raf.writeBytes("ipc_port=$ipcPort\n")
            raf.fd.sync()
        }
    }

    /** Reads the IPC port of the currently running instance (if any). */
    fun getRunningInstancePort(): Int? {
        if (!lockFile.exists()) return null
        return try {
            lockFile.readLines()
                .find { it.startsWith("ipc_port=") }
                ?.substringAfter("=")
                ?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /** Releases the lock and cleans up. */
    fun release() {
        try {
            fileLock?.release()
            randomAccessFile?.close()
            lockFile.delete()
        } catch (_: Exception) {
            // Best effort cleanup
        }
        fileLock = null
        randomAccessFile = null
    }

    private fun writeLockInfo() {
        randomAccessFile?.let { raf ->
            raf.seek(0)
            raf.setLength(0)
            raf.writeBytes("pid=${ProcessHandle.current().pid()}\n")
            raf.writeBytes("ipc_port=0\n") // Updated later when IPC server starts
            raf.fd.sync()
        }
    }

    /** Checks if the lock file references a process that no longer exists. */
    private fun isStale(): Boolean {
        if (!lockFile.exists()) return false
        return try {
            val pid = lockFile.readLines()
                .find { it.startsWith("pid=") }
                ?.substringAfter("=")
                ?.toLongOrNull()
                ?: return true
            !ProcessHandle.of(pid).isPresent
        } catch (_: Exception) {
            true
        }
    }
}
