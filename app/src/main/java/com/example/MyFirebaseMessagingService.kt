package com.example

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_SERVICE", "From: ${remoteMessage.from}")

        // Resolve notification content (check notification first, then data payload fallback)
        var title = remoteMessage.notification?.title
        var body = remoteMessage.notification?.body
        var channelId = WeatherApplication.CHANNEL_WEATHER_ALERTS // Default channel
        var imageUrl: String? = null

        // If data payload exists, override or fetch keys
        if (remoteMessage.data.isNotEmpty()) {
            title = remoteMessage.data["title"] ?: title
            body = remoteMessage.data["body"] ?: body
            channelId = remoteMessage.data["channel_id"] ?: channelId
            imageUrl = remoteMessage.data["image_url"]
        }

        if (title.isNullOrEmpty() || body.isNullOrEmpty()) {
            return
        }

        val verifiedChannelId = when (channelId.lowercase(Locale.ROOT)) {
            "severe_alerts", "severe" -> WeatherApplication.CHANNEL_SEVERE_ALERTS
            "daily_briefings", "briefing", "daily" -> WeatherApplication.CHANNEL_DAILY_BRIEFINGS
            "app_updates", "update", "updates" -> WeatherApplication.CHANNEL_APP_UPDATES
            else -> WeatherApplication.CHANNEL_WEATHER_ALERTS
        }

        showSmartNotification(title, body, verifiedChannelId, imageUrl)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Refreshed FCM registration token: $token")
    }

    private fun showSmartNotification(
        title: String,
        body: String,
        channelId: String,
        imageUrl: String?
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("fcm_notification_clicked", true)
            putExtra("notification_channel", channelId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val smallIconRes = android.R.drawable.ic_dialog_info
        val iconColor = when (channelId) {
            WeatherApplication.CHANNEL_SEVERE_ALERTS -> 0xFFFF453A.toInt() // Severe Red
            WeatherApplication.CHANNEL_WEATHER_ALERTS -> 0xFF0A84FF.toInt() // Weather Blue
            WeatherApplication.CHANNEL_DAILY_BRIEFINGS -> 0xFF30D158.toInt() // Briefing Green
            else -> 0xFFBF5AF2.toInt() // Update Purple
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(smallIconRes)
            .setTicker(title)
            .setColor(iconColor)
            .setPriority(
                if (channelId == WeatherApplication.CHANNEL_SEVERE_ALERTS) 
                    NotificationCompat.PRIORITY_HIGH 
                else 
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(
                if (channelId == WeatherApplication.CHANNEL_SEVERE_ALERTS)
                    NotificationCompat.CATEGORY_ALARM
                else
                    NotificationCompat.CATEGORY_STATUS
            )
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        if (!imageUrl.isNullOrEmpty()) {
            val bitmap = getBitmapFromUrl(imageUrl)
            if (bitmap != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setSummaryText(body)
                )
            }
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun getBitmapFromUrl(src: String): android.graphics.Bitmap? {
        return try {
            val url = URL(src)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e("FCM_SERVICE", "Failed to resolve image URL: ${e.message}")
            null
        }
    }
}
