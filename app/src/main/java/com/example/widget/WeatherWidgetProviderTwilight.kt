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

class WeatherWidgetProviderTwilight : AppWidgetProvider() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_REFRESH_TWILIGHT_WIDGET = "com.example.widget.REFRESH_TWILIGHT_WIDGET"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ioScope.launch {
            try {
                val db = WeatherDatabase.getDatabase(context)
                val dao = db.locationDao()
                val list = dao.getAllLocations().first()
                val primary = list.find { it.isPrimary } ?: list.firstOrNull() ?: return@launch
                for (id in appWidgetIds) updateSingleAppWidget(context, appWidgetManager, id, primary)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_TWILIGHT_WIDGET) {
            ioScope.launch {
                try {
                    val db = WeatherDatabase.getDatabase(context)
                    val dao = db.locationDao()
                    val list = dao.getAllLocations().first()
                    if (list.isNotEmpty()) {
                        val primary = list.find { it.isPrimary } ?: list.first()
                        val awm = AppWidgetManager.getInstance(context)
                        val ids = awm.getAppWidgetIds(ComponentName(context, WeatherWidgetProviderTwilight::class.java))
                        for (id in ids) updateSingleAppWidget(context, awm, id, primary)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun updateSingleAppWidget(context: Context, awm: AppWidgetManager, id: Int, loc: SavedLocation) {
        try {
            val views = RemoteViews(context.packageName, R.layout.weather_widget)
            val prefs = context.getSharedPreferences("weather_prefs_v3", Context.MODE_PRIVATE)
            val opacity = prefs.getFloat("widget_glass_opacity", 0.18f)
            val alpha = (opacity * 255).toInt().coerceIn(10, 255)

            // Calm Twilight Theme: White glass, Green accents
            views.setInt(R.id.widget_bg_view, "setImageAlpha", alpha)
            views.setInt(R.id.widget_bg_view, "setColorFilter", android.graphics.Color.parseColor("#FFFFFF"))
            views.setTextColor(R.id.widget_condition, android.graphics.Color.parseColor("#00FF66"))

            views.setTextViewText(R.id.widget_city, loc.cityName)
            views.setTextViewText(R.id.widget_temp, "${loc.temperatureC.toInt()}°C")
            views.setTextViewText(R.id.widget_high_low, "H: ${loc.highestTempC.toInt()}°  L: ${loc.lowestTempC.toInt()}°")

            val calendar = Calendar.getInstance()
            val isNight = calendar.get(Calendar.HOUR_OF_DAY) !in 6..18
            val cond = if (isNight && loc.condition.contains("Clear", true)) "CLEAR NIGHT" else loc.condition.uppercase()
            views.setTextViewText(R.id.widget_condition, cond)
            views.setTextViewText(R.id.widget_updated, "Sync: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))

            val pi = PendingIntent.getActivity(context, id + 70000, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            views.setOnClickPendingIntent(R.id.text_container, pi)

            val refreshIntent = Intent(context, WeatherWidgetProviderTwilight::class.java).apply {
                action = ACTION_REFRESH_TWILIGHT_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                id + 70000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            awm.updateAppWidget(id, views)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
