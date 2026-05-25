package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class WeatherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Dynamic automatic client initialization of Firebase
        initFirebaseSilently()
        
        // Create Required FCM Notification Channels
        createNotificationChannels()
    }

    private fun initFirebaseSilently() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyDummyKey_For_Initializing_FCM_Local")
                    .setApplicationId("1:74929500325:android:b220fb0d-e16f-4ced-8426-28bc321ac1a2")
                    .setProjectId("ai-weather-radar-applet")
                    .setGcmSenderId("74929500325")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.d("WeatherApplication", "Firebase initialized successfully in-app with dynamic fallback options!")
            } else {
                Log.d("WeatherApplication", "Firebase already initialized by system customizer.")
            }
        } catch (e: Exception) {
            Log.e("WeatherApplication", "Failed to initialize Firebase app: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel 1: Weather Alerts (Default Importance)
            val channelWeather = NotificationChannel(
                CHANNEL_WEATHER_ALERTS,
                "Weather Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Real-time updates about temperature, precipitation, and active local weather events."
                enableVibration(true)
            }

            // Channel 2: Severe Alerts (High Importance)
            val channelSevere = NotificationChannel(
                CHANNEL_SEVERE_ALERTS,
                "Severe Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical emergency notifications for extreme weather events such as storms, floods, and severe heatwaves."
                enableVibration(true)
                setShowBadge(true)
            }

            // Channel 3: Daily Briefings (Default Importance)
            val channelBriefings = NotificationChannel(
                CHANNEL_DAILY_BRIEFINGS,
                "Daily Briefings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your customized morning and evening weather overviews."
                setShowBadge(false)
            }

            // Channel 4: App Updates (Low Importance)
            val channelUpdates = NotificationChannel(
                CHANNEL_APP_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Feature announcements and system improvements."
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(channelWeather)
            notificationManager.createNotificationChannel(channelSevere)
            notificationManager.createNotificationChannel(channelBriefings)
            notificationManager.createNotificationChannel(channelUpdates)
            
            Log.d("WeatherApplication", "All requested Notification channels created successfully!")
        }
    }

    companion object {
        const val CHANNEL_WEATHER_ALERTS = "weather_alerts"
        const val CHANNEL_SEVERE_ALERTS = "severe_alerts"
        const val CHANNEL_DAILY_BRIEFINGS = "daily_briefings"
        const val CHANNEL_APP_UPDATES = "app_updates"
    }
}
