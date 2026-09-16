package com.privacy.whatsappdecryptor.core.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import com.privacy.whatsappdecryptor.WhatsAppDecryptorApp
import com.privacy.whatsappdecryptor.core.crypto.Crypt15StreamingDecryptor
import com.privacy.whatsappdecryptor.core.database.AndroidWhatsAppDatabaseReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.measureTimeMillis

class DecryptionForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var decryptionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uriString = intent.getStringExtra(EXTRA_URI)
                val hexKey = intent.getStringExtra(EXTRA_KEY)
                if (uriString != null && hexKey != null) {
                    startDecryption(Uri.parse(uriString), hexKey)
                }
            }
            ACTION_CANCEL -> {
                cancelDecryption()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDecryption(sourceUri: Uri, hexKey: String) {
        val initialNotification = buildNotification("Preparing decryption...", 0, 0, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                WhatsAppDecryptorApp.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(WhatsAppDecryptorApp.NOTIFICATION_ID, initialNotification)
        }

        decryptionJob = serviceScope.launch {
            val stagingFile = File(noBackupFilesDir, "msgstore_staging.db")
            val targetFile = File(noBackupFilesDir, "msgstore_decrypted.db")

            var lastNotificationPercent = -1

            try {
                // Determine file size from ContentResolver
                var fileSize = -1L
                contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }

                // Storage pre-flight check
                val storageCheck = StoragePreflight.checkStorage(noBackupFilesDir, fileSize)
                if (!storageCheck.hasEnoughSpace) {
                    val msg = "Insufficient storage space: need ~${StoragePreflight.formatBytes(storageCheck.requiredBytes)}, " +
                            "only ${StoragePreflight.formatBytes(storageCheck.availableBytes)} available."
                    _progressState.value = DecryptionProgressState.Error(msg)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                _progressState.value = DecryptionProgressState.ReadingHeader

                val elapsed = measureTimeMillis {
                    contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        Crypt15StreamingDecryptor.decrypt(
                            inputStream = inputStream,
                            outputFile = stagingFile,
                            hexKey = hexKey,
                            totalBytes = fileSize,
                            onProgress = { processed, total ->
                                val percent = if (total > 0) ((processed * 100) / total).toInt() else 0
                                _progressState.value = DecryptionProgressState.Decrypting(processed, total, percent)

                                // Update notification periodically to avoid system throttle
                                if (percent != lastNotificationPercent && (percent % 2 == 0 || percent == 100)) {
                                    val mbProcessed = processed / (1024 * 1024)
                                    val mbTotal = total / (1024 * 1024)
                                    updateNotification("Decrypting: $percent% ($mbProcessed MB / $mbTotal MB)", percent, 100, false)
                                    lastNotificationPercent = percent
                                }
                            }
                        )
                    } ?: throw IllegalStateException("Unable to open input stream for selected backup file")
                }

                // Validate SQLite output with fast sanity check
                _progressState.value = DecryptionProgressState.ValidatingDatabase
                updateNotification("Validating database schema...", 0, 0, true)

                AndroidWhatsAppDatabaseReader.open(stagingFile).use { reader ->
                    // Verified: rename staging to target
                    if (targetFile.exists()) targetFile.delete()
                    stagingFile.renameTo(targetFile)
                }

                _progressState.value = DecryptionProgressState.Complete(targetFile, elapsed)
                updateNotification("Decryption complete!", 100, 100, false)

            } catch (e: Exception) {
                if (stagingFile.exists()) stagingFile.delete()
                _progressState.value = DecryptionProgressState.Error(
                    message = e.localizedMessage ?: "Decryption failed",
                    throwable = e
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancelDecryption() {
        decryptionJob?.cancel()
        val stagingFile = File(noBackupFilesDir, "msgstore_staging.db")
        if (stagingFile.exists()) stagingFile.delete()
        _progressState.value = DecryptionProgressState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(text: String, progress: Int, max: Int, indeterminate: Boolean) {
        val notification = buildNotification(text, progress, max, indeterminate)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(WhatsAppDecryptorApp.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, progress: Int, max: Int, indeterminate: Boolean): Notification {
        val cancelIntent = Intent(this, DecryptionForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WhatsAppDecryptorApp.DECRYPTION_CHANNEL_ID)
            .setContentTitle("WhatsApp Backup Decryption")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        decryptionJob?.cancel()
    }

    companion object {
        const val ACTION_START = "com.privacy.whatsappdecryptor.action.START"
        const val ACTION_CANCEL = "com.privacy.whatsappdecryptor.action.CANCEL"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_KEY = "extra_key"

        private val _progressState = MutableStateFlow<DecryptionProgressState>(DecryptionProgressState.Idle)
        val progressState: StateFlow<DecryptionProgressState> = _progressState.asStateFlow()

        fun start(context: Context, sourceUri: Uri, hexKey: String) {
            val intent = Intent(context, DecryptionForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URI, sourceUri.toString())
                putExtra(EXTRA_KEY, hexKey)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, DecryptionForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        fun resetState() {
            _progressState.value = DecryptionProgressState.Idle
        }
    }
}
