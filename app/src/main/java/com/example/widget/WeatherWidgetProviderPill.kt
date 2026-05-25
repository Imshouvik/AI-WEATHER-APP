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
import java.util.Locale

class WeatherWidgetProviderPill : AppWidgetProvider() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_REFRESH_PILL_WIDGET = "com.example.widget.REFRESH_PILL_WIDGET"
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
        if (intent.action == ACTION_REFRESH_PILL_WIDGET) {
            ioScope.launch {
                try {
                    val db = WeatherDatabase.getDatabase(context)
                    val dao = db.locationDao()
                    val list = dao.getAllLocations().first()

                    if (list.isNotEmpty()) {
                        val primary = list.find { it.isPrimary } ?: list.first()
                        val delta = (-1..1).random().toFloat()
                        val nextTemp = (primary.temperatureC + delta).coerceIn(-5f, 42f)
                        val updated = primary.copy(
                            temperatureC = nextTemp,
                            timestamp = System.currentTimeMillis()
                        )
                        dao.insertLocation(updated)

                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisWidget = ComponentName(context, WeatherWidgetProviderPill::class.java)
                        val allIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        for (id in allIds) {
                            updateSingleAppWidget(context, appWidgetManager, id, updated)
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
            val views = RemoteViews(context.packageName, R.layout.weather_widget_pill)

            val prefs = context.getSharedPreferences("weather_prefs_v3", Context.MODE_PRIVATE)
            val selectedSkin = prefs.getInt("widget_selected_skin", 0)
            val glassOpacity = prefs.getFloat("widget_glass_opacity", 0.18f)

            val alphaInt = (glassOpacity * 255).toInt().coerceIn(10, 255)
            views.setInt(R.id.widget_bg_view, "setImageAlpha", alphaInt)

            views.setTextViewText(R.id.widget_city, location.cityName)
            views.setTextViewText(R.id.widget_condition, location.condition.uppercase(Locale.getDefault()))
            views.setTextViewText(R.id.widget_temp, "${location.temperatureC.toInt()}°C")

            val accentColor = when (selectedSkin) {
                1 -> android.graphics.Color.parseColor("#FF9E00")
                2 -> android.graphics.Color.parseColor("#00E5FF")
                3 -> android.graphics.Color.parseColor("#E040FB")
                else -> android.graphics.Color.parseColor("#00FF66")
            }
            views.setTextColor(R.id.widget_condition, accentColor)

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val activityPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 20000,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.text_container, activityPendingIntent)

            val refreshIntent = Intent(context, WeatherWidgetProviderPill::class.java).apply {
                action = ACTION_REFRESH_PILL_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 20000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
