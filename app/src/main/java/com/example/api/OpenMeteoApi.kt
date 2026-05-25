package com.example.api

import com.squareup.moshi.Json
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- Geocoding Response Models ---
data class GeocodingResponse(
    @Json(name = "results") val results: List<GeocodingResult>?
)

data class GeocodingResult(
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Float,
    @Json(name = "longitude") val longitude: Float,
    @Json(name = "country") val country: String?,
    @Json(name = "country_code") val countryCode: String?
)

// --- Weather Forecast Response Models ---
data class WeatherForecastResponse(
    @Json(name = "latitude") val latitude: Float,
    @Json(name = "longitude") val longitude: Float,
    @Json(name = "current") val current: CurrentWeather?,
    @Json(name = "hourly") val hourly: HourlyData?,
    @Json(name = "daily") val daily: DailyData?
)

data class CurrentWeather(
    @Json(name = "temperature_2m") val temperatureC: Float,
    @Json(name = "relative_humidity_2m") val humidityPercent: Int,
    @Json(name = "apparent_temperature") val apparentTemperature: Float,
    @Json(name = "precipitation") val precipitation: Float,
    @Json(name = "weather_code") val weatherCode: Int,
    @Json(name = "pressure_msl") val pressureHpa: Float,
    @Json(name = "wind_speed_10m") val windKmh: Float,
    @Json(name = "wind_direction_10m") val windDirectionDegrees: Float
)

data class HourlyData(
    @Json(name = "time") val time: List<String>?,
    @Json(name = "temperature_2m") val temperatureC: List<Float>?,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>?,
    @Json(name = "weather_code") val weatherCode: List<Int>?
)

data class DailyData(
    @Json(name = "time") val time: List<String>?,
    @Json(name = "weather_code") val weatherCode: List<Int>?,
    @Json(name = "temperature_2m_max") val temperatureMaxC: List<Float>?,
    @Json(name = "temperature_2m_min") val temperatureMinC: List<Float>?,
    @Json(name = "uv_index_max") val uvIndexMax: List<Float>?,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>?
)

// --- API interfaces ---
interface OpenMeteoGeocodingService {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") cityName: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

interface OpenMeteoWeatherService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Float,
        @Query("longitude") longitude: Float,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,weather_code",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,uv_index_max,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto"
    ): WeatherForecastResponse
}

// --- Companion Retrofit Clinical Providers ---
object OpenMeteoClient {
    private val moshiConverterFactory = MoshiConverterFactory.create(
        com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
    )

    val geocodingService: OpenMeteoGeocodingService by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .addConverterFactory(moshiConverterFactory)
            .build()
            .create(OpenMeteoGeocodingService::class.java)
    }

    val weatherService: OpenMeteoWeatherService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(moshiConverterFactory)
            .build()
            .create(OpenMeteoWeatherService::class.java)
    }

    // Map weather code to readable string condition
    fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear Sunny"
            1, 2 -> "Partly Cloudy"
            3 -> "Heavy Overcast"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Light Rain"
            56, 57 -> "Freezing Drizzle"
            61, 63 -> "Showers"
            65 -> "Heavy Rain"
            66, 67 -> "Freezing Rain"
            71, 73 -> "Snow Flurries"
            75 -> "Heavy Snowfall"
            77 -> "Snow Grains"
            80, 81 -> "Light Showers"
            82 -> "Violent Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Severe Thunderstorm"
            else -> "Partly Cloudy"
        }
    }
}
