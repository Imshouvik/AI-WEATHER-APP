package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey val cityName: String,
    val country: String,
    val temperatureC: Float,
    val highestTempC: Float,
    val lowestTempC: Float,
    val condition: String,
    val humidityPercent: Int,
    val windKmh: Float,
    val windDirDegrees: Int,
    val uvIndex: Int,
    val aqi: Int,
    val pressureHpa: Int,
    val rainChancePercent: Int,
    val isPrimary: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Float = 0f,
    val longitude: Float = 0f,
    val feelsLikeC: Float = 0f
)

@Dao
interface LocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY isPrimary DESC, timestamp DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT COUNT(*) FROM saved_locations")
    suspend fun getLocationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)

    @Query("UPDATE saved_locations SET isPrimary = 0")
    suspend fun clearPrimaryStatus()

    @Query("UPDATE saved_locations SET isPrimary = 1 WHERE cityName = :cityName")
    suspend fun setPrimaryLocation(cityName: String)
}
