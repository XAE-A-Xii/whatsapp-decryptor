package com.privacy.whatsappdecryptor.core.service

import android.content.Context
import java.io.File

data class StorageCheckResult(
    val hasEnoughSpace: Boolean,
    val availableBytes: Long,
    val requiredBytes: Long
)

object StoragePreflight {

    /**
     * Checks if [targetDirectory] has at least 1.5x the space of [encryptedFileSize].
     * (A 2 GB backup uncompresses to ~4-5 GB of SQLite database).
     */
    fun checkStorage(targetDirectory: File, encryptedFileSize: Long): StorageCheckResult {
        val usableBytes = targetDirectory.usableSpace
        val requiredBytes = if (encryptedFileSize > 0) (encryptedFileSize * 2.5).toLong() else 4L * 1024 * 1024 * 1024 // default 4GB

        return StorageCheckResult(
            hasEnoughSpace = usableBytes >= requiredBytes,
            availableBytes = usableBytes,
            requiredBytes = requiredBytes
        )
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "%.2f GB".format(mb / 1024.0)
        } else {
            "%.0f MB".format(mb)
        }
    }
}
