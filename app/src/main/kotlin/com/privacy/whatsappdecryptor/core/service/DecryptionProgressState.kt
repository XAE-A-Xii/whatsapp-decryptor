package com.privacy.whatsappdecryptor.core.service

import java.io.File

sealed interface DecryptionProgressState {
    data object Idle : DecryptionProgressState
    data class CheckingStorage(val freeBytes: Long, val requiredBytes: Long) : DecryptionProgressState
    data object ReadingHeader : DecryptionProgressState
    data class Decrypting(val bytesProcessed: Long, val totalBytes: Long, val percent: Int) : DecryptionProgressState
    data object ValidatingDatabase : DecryptionProgressState
    data class Complete(val dbFile: File, val totalTimeMs: Long) : DecryptionProgressState
    data class Error(val message: String, val throwable: Throwable? = null) : DecryptionProgressState
}
