package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.db.SavedLocation
import com.example.db.WeatherDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WeatherWidgetProviderPro : AppWidgetProvider() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_REFRESH_PRO_WIDGET = "com.example.widget.REFRESH_PRO_WIDGET"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        ioScope.launch {
            try {
                val db = WeatherDatabase.getDatabase(context)
                val dao = db.locationDao()
                val list = dao.getAllLocations().first()

                val primaryCity = list.find { it.isPrimary } ?: list.firstOrNull() ?: SavedLocation(
                    cityName = "London",
                    country = "UK",
                    temperatureC = 14f,
                    highestTempC = 18f,
                    lowestTempC = 12f,
                    condition = "Partly Cloudy",
                    humidityPercent = 75,
                    windKmh = 14f,
                    windDirDegrees = 180,
                    uvIndex = 3,
                    aqi = 42,
                    pressureHpa = 1013,
                    rainChancePercent = 25,
                    isPrimary = true
                )

                for (appWidgetId in appWidgetIds) {
                    updateSingleAppWidget(context, appWidgetManager, appWidgetId, primaryCity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_PRO_WIDGET) {
            // Trigger a real background sync
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.action.SYNC_WEATHER"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mainIntent)

            ioScope.launch {
                try {
                    val db = WeatherDatabase.getDatabase(context)
                    val dao = db.locationDao()
                    val list = dao.getAllLocations().first()

                    if (list.isNotEmpty()) {
                        val primary = list.find { it.isPrimary } ?: list.first()
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisWidget = ComponentName(context, WeatherWidgetProviderPro::class.java)
                        val allIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        for (id in allIds) {
                            updateSingleAppWidget(context, appWidgetManager, id, primary)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateSingleAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        location: SavedLocation
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.weather_widget_pro)

            val prefs = context.getSharedPreferences("weather_prefs_v3", Context.MODE_PRIVATE)
            val selectedSkin = prefs.getInt("widget_selected_skin", 0)
            val glassOpacity = prefs.getFloat("widget_glass_opacity", 0.18f)

            val alphaInt = (glassOpacity * 255).toInt().coerceIn(10, 255)
            views.setInt(R.id.widget_bg_view, "setImageAlpha", alphaInt)

            views.setTextViewText(R.id.widget_city, location.cityName)
            
            // Fix night display
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val isNight = currentHour >= 18 || currentHour < 6
            val displayCondition = if (isNight && (location.condition.contains("Clear", true) || location.condition.contains("Sunny", true))) {
                "CLEAR NIGHT"
            } else {
                location.condition.uppercase(Locale.getDefault())
            }
            
            views.setTextViewText(R.id.widget_condition, displayCondition)
            views.setTextViewText(R.id.widget_temp, "${location.temperatureC.toInt()}°C")
            
            views.setTextViewText(R.id.widget_wind, "${location.windKmh.toInt()} km/h")
            views.setTextViewText(R.id.widget_uv, "${location.uvIndex}")
            views.setTextViewText(R.id.widget_humidity, "${location.humidityPercent}%")
            
            views.setTextViewText(
                R.id.widget_high_low,
                "H: ${location.highestTempC.toInt()}°  L: ${location.lowestTempC.toInt()}°"
            )

            val timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_updated, "Sync: $timestamp")

            val (accentColor, bgColor) = when (selectedSkin) {
                1 -> Pair("#00FF66", "#141F30") // Compact Pill
                2 -> Pair("#FFFF9E00", "#0F1218") // Pro Wide-Deck
                3 -> Pair("#FFFFD54F", "#231A10") // Space Slate
                else -> Pair("#00FF66", "#FFFFFF") // Calm Twilight
            }
            
            views.setTextColor(R.id.widget_condition, android.graphics.Color.parseColor(accentColor))
            views.setInt(R.id.widget_bg_view, "setColorFilter", android.graphics.Color.parseColor(bgColor))

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val activityPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 30000,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.text_container, activityPendingIntent)

            val refreshIntent = Intent(context, WeatherWidgetProviderPro::class.java).apply {
                action = ACTION_REFRESH_PRO_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 30000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, activityPendingIntent)

            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_widget_studio", true)
            }
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 39999,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_settings_button, settingsPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
