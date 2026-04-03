package com.glassvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat

object VpnNotification {

    const val CHANNEL_ID = "glassvpn_service"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "GlassVPN active connection status"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            lightColor = Color.parseColor("#00D4FF")
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        isConnected: Boolean,
        pingMs: Long,
        downloadSpeed: Long,
        uploadSpeed: Long
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isConnected) "Connected" else "Disconnected"
        val pingText = if (pingMs > 0) "${pingMs}ms" else "--"
        val contentText = if (isConnected) {
            "Ping: $pingText  ↓ ${downloadSpeed.formatSpeed()}  ↑ ${uploadSpeed.formatSpeed()}"
        } else {
            "Tap to connect"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("GlassVPN • $statusText")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(isConnected)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(if (isConnected) Color.parseColor("#00D4FF") else Color.parseColor("#FF4B6E"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun update(
        context: Context,
        isConnected: Boolean,
        pingMs: Long,
        downloadSpeed: Long,
        uploadSpeed: Long
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, build(context, isConnected, pingMs, downloadSpeed, uploadSpeed))
    }
}
