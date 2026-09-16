package com.privacy.whatsappdecryptor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class WhatsAppDecryptorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DECRYPTION_CHANNEL_ID,
                "WhatsApp Backup Decryption",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress during WhatsApp backup decryption"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DECRYPTION_CHANNEL_ID = "channel_decryption_progress"
        const val NOTIFICATION_ID = 1001
    }
}
