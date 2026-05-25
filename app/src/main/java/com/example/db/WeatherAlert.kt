package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "weather_alerts")
data class WeatherAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityName: String,
    val type: String, // "THUNDERSTORM", "SNOW", "HIGH_WINDS", "TEMP_HIGH", "TEMP_LOW"
    val threshold: Float, // Threshold or limit checked
    val isEnabled: Boolean = true,
    val isTriggered: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AlertDao {
    @Query("SELECT * FROM weather_alerts WHERE cityName = :cityName ORDER BY timestamp DESC")
    fun getAlertsForCity(cityName: String): Flow<List<WeatherAlert>>

    @Query("SELECT * FROM weather_alerts WHERE isEnabled = 1")
    suspend fun getActiveAlerts(): List<WeatherAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: WeatherAlert)

    @Update
    suspend fun updateAlert(alert: WeatherAlert)

    @Delete
    suspend fun deleteAlert(alert: WeatherAlert)
}
