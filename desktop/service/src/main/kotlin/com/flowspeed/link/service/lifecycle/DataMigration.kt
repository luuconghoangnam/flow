package com.flowspeed.link.service.lifecycle

import java.io.File

/**
 * Handles migration from the single-process app data format
 * to the service-owned format on first startup.
 *
 * Detects legacy data by checking for the absence of a service lock file
 * and presence of download list files in the expected location.
 */
object DataMigration {

    /**
     * Checks if migration is needed and performs it.
     * @param serviceDataDir The service's data directory.
     * @return true if migration was performed, false if not needed.
     */
    fun migrateIfNeeded(serviceDataDir: File): Boolean {
        val migrationMarker = File(serviceDataDir, ".migrated")
        if (migrationMarker.exists()) return false

        val legacyDataDir = detectLegacyDataDir(serviceDataDir)
        if (legacyDataDir == null || !legacyDataDir.exists()) {
            // No legacy data found, mark as migrated (fresh install)
            serviceDataDir.mkdirs()
            migrationMarker.createNewFile()
            return false
        }

        println("Migrating data from: ${legacyDataDir.absolutePath}")

        try {
            // Backup original
            val backupDir = File(serviceDataDir, ".backup")
            backupDir.mkdirs()

            // Migrate each data directory
            migrateDirectory(legacyDataDir, "downloads", serviceDataDir, backupDir)
            migrateDirectory(legacyDataDir, "parts", serviceDataDir, backupDir)
            migrateDirectory(legacyDataDir, "queues", serviceDataDir, backupDir)
            migrateDirectory(legacyDataDir, "download_data", serviceDataDir, backupDir)
            migrateDirectory(legacyDataDir, "categories", serviceDataDir, backupDir)

            // Migrate settings files
            migrateFile(legacyDataDir, "settings.json", serviceDataDir, backupDir)
            migrateFile(legacyDataDir, "categories.json", serviceDataDir, backupDir)
            migrateFile(legacyDataDir, "proxy.json", serviceDataDir, backupDir)

            migrationMarker.createNewFile()
            println("Migration completed successfully")
            return true
        } catch (e: Exception) {
            System.err.println("Migration failed: ${e.message}")
            e.printStackTrace()
            // Still mark as migrated to avoid retry loops
            migrationMarker.createNewFile()
            return false
        }
    }

    /**
     * Detects the legacy single-process data directory.
     * The legacy app uses the same base directory but without a service.lock file.
     */
    private fun detectLegacyDataDir(serviceDataDir: File): File? {
        // Legacy data is in the same location (the app used the same data dir)
        // We detect it by checking if download list files exist but no service lock
        val lockFile = File(serviceDataDir, "service.lock")
        if (lockFile.exists()) return null // Already a service-managed dir

        val downloadsDir = File(serviceDataDir, "downloads")
        if (downloadsDir.exists() && downloadsDir.listFiles()?.isNotEmpty() == true) {
            return serviceDataDir
        }

        return null
    }

    private fun migrateDirectory(source: File, dirName: String, target: File, backup: File) {
        val sourceDir = File(source, dirName)
        if (!sourceDir.exists()) return

        val targetDir = File(target, dirName)
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            // Target already has data, skip
            return
        }

        // Copy to target
        sourceDir.copyRecursively(targetDir, overwrite = false)

        // Backup original
        val backupTarget = File(backup, dirName)
        sourceDir.copyRecursively(backupTarget, overwrite = true)

        println("  Migrated: $dirName (${sourceDir.listFiles()?.size ?: 0} files)")
    }

    private fun migrateFile(source: File, fileName: String, target: File, backup: File) {
        val sourceFile = File(source, fileName)
        if (!sourceFile.exists()) return

        val targetFile = File(target, fileName)
        if (targetFile.exists()) return // Don't overwrite

        sourceFile.copyTo(targetFile, overwrite = false)
        sourceFile.copyTo(File(backup, fileName), overwrite = true)

        println("  Migrated: $fileName")
    }
}
