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

class WeatherWidgetProviderSpace : AppWidgetProvider() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_REFRESH_SPACE_WIDGET = "com.example.widget.REFRESH_SPACE_WIDGET"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ioScope.launch {
            try {
                val db = WeatherDatabase.getDatabase(context)
                val dao = db.locationDao()
                val list = dao.getAllLocations().first()
                val primaryCity = list.find { it.isPrimary } ?: list.firstOrNull() ?: return@launch

                for (appWidgetId in appWidgetIds) {
                    updateSingleAppWidget(context, appWidgetManager, appWidgetId, primaryCity)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_SPACE_WIDGET) {
            ioScope.launch {
                try {
                    val db = WeatherDatabase.getDatabase(context)
                    val dao = db.locationDao()
                    val list = dao.getAllLocations().first()
                    if (list.isNotEmpty()) {
                        val primary = list.find { it.isPrimary } ?: list.first()
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisWidget = ComponentName(context, WeatherWidgetProviderSpace::class.java)
                        val allIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        for (id in allIds) {
                            updateSingleAppWidget(context, appWidgetManager, id, primary)
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun updateSingleAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, location: SavedLocation) {
        try {
            val views = RemoteViews(context.packageName, R.layout.weather_widget_space)
            val prefs = context.getSharedPreferences("weather_prefs_v3", Context.MODE_PRIVATE)
            val glassOpacity = prefs.getFloat("widget_glass_opacity", 0.18f)

            val alphaInt = (glassOpacity * 255).toInt().coerceIn(10, 255)
            views.setInt(R.id.widget_bg_view, "setImageAlpha", alphaInt)
            views.setInt(R.id.widget_bg_view, "setColorFilter", android.graphics.Color.parseColor("#231A10"))

            views.setTextViewText(R.id.widget_city, location.cityName)
            views.setTextViewText(R.id.widget_temp, "${location.temperatureC.toInt()}°C")
            
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val isNight = currentHour >= 18 || currentHour < 6
            val displayCondition = if (isNight && (location.condition.contains("Clear", true) || location.condition.contains("Sunny", true))) {
                "CLEAR NIGHT"
            } else {
                location.condition.uppercase(Locale.getDefault())
            }
            views.setTextViewText(R.id.widget_condition, displayCondition)

            val highStr = "H: ${location.highestTempC.toInt()}°"
            val lowStr = "L: ${location.lowestTempC.toInt()}°"
            views.setTextViewText(R.id.widget_high_low, "High: $highStr  •  Low: $lowStr")

            val timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_updated, "Sync: $timestamp")

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val activityPendingIntent = PendingIntent.getActivity(context, appWidgetId + 50000, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, activityPendingIntent)

            val refreshIntent = Intent(context, WeatherWidgetProviderSpace::class.java).apply {
                action = ACTION_REFRESH_SPACE_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 50000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
