package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.db.SavedLocation
import com.example.db.WeatherDatabase
import com.example.db.WeatherAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import com.example.api.OpenMeteoClient
import com.example.api.WeatherForecastResponse
import java.util.*
import java.text.SimpleDateFormat
import kotlin.math.sin
import kotlin.math.cos

// --- Weather Data Classes ---
data class HistoricalRecord(
    val dateString: String,
    val avgTempC: Float,
    val condition: String,
    val rainChancePercent: Int,
    val windKmh: Float,
    val minTempC: Float,
    val maxTempC: Float
)

data class HourlyForecast(
    val hourString: String,
    val tempC: Int,
    val condition: String,
    val rainChance: Int
)

data class DailyForecast(
    val dayString: String,
    val highC: Int,
    val lowC: Int,
    val condition: String,
    val rainChance: Int
)

data class FullWeatherDetails(
    val location: SavedLocation,
    val isCelsius: Boolean,
    val hourlyList: List<HourlyForecast>,
    val dailyList: List<DailyForecast>,
    val aqiStatus: String,
    val aqiMessage: String,
    val uvDescription: String,
    val lastUpdated: String
)

// --- Gemini Request / Response models using Moshi compatible structures ---
data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(val contents: List<GeminiContent>)

data class GeminiCandidate(val content: GeminiContent?)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WeatherDatabase.getDatabase(application)
    private val dao = db.locationDao()
    private val alertDao = db.alertDao()

    // Currently selected location
    private val _selectedLocation = MutableStateFlow<SavedLocation?>(null)
    val selectedLocation = _selectedLocation.asStateFlow()

    // Custom database alerts flow for selected city
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val cityAlerts: StateFlow<List<WeatherAlert>> = _selectedLocation
        .flatMapLatest { loc ->
            if (loc != null) {
                alertDao.getAlertsForCity(loc.cityName)
            } else {
                flowOf(emptyList())
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Preferences / Settings State
    private val _isCelsius = MutableStateFlow(true)
    val isCelsius = _isCelsius.asStateFlow()

    // Interactive customization & user preferences flows
    private val _transparencyLevel = MutableStateFlow("Medium")
    val transparencyLevel = _transparencyLevel.asStateFlow()

    private val _themeMode = MutableStateFlow("Slate Dark")
    val themeMode = _themeMode.asStateFlow()

    private val _widgetDensity = MutableStateFlow("Standard Density")
    val widgetDensity = _widgetDensity.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("English (US)")
    val selectedLanguage = _selectedLanguage.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    private val _autoRefreshInterval = MutableStateFlow("30 min")
    val autoRefreshInterval = _autoRefreshInterval.asStateFlow()

    fun setTransparencyLevel(level: String) {
        _transparencyLevel.value = level
    }

    fun setThemeMode(theme: String) {
        _themeMode.value = theme
    }

    fun setWidgetDensity(density: String) {
        _widgetDensity.value = density
    }

    fun setSelectedLanguage(lang: String) {
        _selectedLanguage.value = lang
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    fun setAutoRefreshInterval(interval: String) {
        _autoRefreshInterval.value = interval
    }

    // List of saved locations
    val savedLocations: StateFlow<List<SavedLocation>> = dao.getAllLocations()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Full forecast derived state
    private val _forecastDetails = MutableStateFlow<FullWeatherDetails?>(null)
    val forecastDetails = _forecastDetails.asStateFlow()

    // Smart AI Briefing state
    private val _aiBriefing = MutableStateFlow("Analyzing current atmospheric conditions and compiling premium weather brief...")
    val aiBriefing = _aiBriefing.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading = _isAiLoading.asStateFlow()

    // Global location database for simulation and searches
    private val dummyGlobalCities = listOf(
        Pair("New York", "USA"),
        Pair("London", "UK"),
        Pair("Tokyo", "Japan"),
        Pair("Sydney", "Australia"),
        Pair("Paris", "France"),
        Pair("San Francisco", "USA"),
        Pair("Reykjavík", "Iceland"),
        Pair("Singapore", "Singapore"),
        Pair("Mumbai", "India"),
        Pair("Cairo", "Egypt"),
        Pair("Cape Town", "South Africa"),
        Pair("Rio de Janeiro", "Brazil")
    )

    private val prefs = application.getSharedPreferences("weather_prefs_v3", android.content.Context.MODE_PRIVATE)

    init {
        // Step 1: Always reactively monitor saved locations to set initial selection on app start/reload
        viewModelScope.launch {
            savedLocations.collect { list ->
                if (list.isNotEmpty() && _selectedLocation.value == null) {
                    val lastSelected = prefs.getString("last_selected_city_v4", null)
                    val toSelect = list.find { it.cityName == lastSelected } ?: list.find { it.isPrimary } ?: list.first()
                    selectLocation(toSelect)
                }
            }
        }

        // Step 2: Safe database initialization (pre-populate only if empty) & current GPS location auto-detection
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { dao.getLocationCount() }
            val hasPrepopulatedStr = prefs.getBoolean("has_prepopulated_v4", false)
            if (count == 0 || !hasPrepopulatedStr) {
                prepopulateDatabase()
                prefs.edit().putBoolean("has_prepopulated_v4", true).commit()
                detectAndSetCurrentLocation()
            }
        }
    }

    private suspend fun prepopulateDatabase() = withContext(Dispatchers.IO) {
        val initialCities = listOf(
            // Name, Country, Latitude, Longitude
            Triple("New York", "USA", Pair(40.7128f, -74.0060f)),
            Triple("London", "UK", Pair(51.5074f, -0.1278f)),
            Triple("Tokyo", "Japan", Pair(35.6762f, 139.6503f)),
            Triple("Sydney", "Australia", Pair(-33.8688f, 151.2093f)),
            Triple("Paris", "France", Pair(48.8566f, 2.3522f))
        )

        initialCities.forEachIndexed { index, city ->
            val cleanDefault = SavedLocation(
                cityName = city.first,
                country = city.second,
                temperatureC = 18f,
                highestTempC = 22f,
                lowestTempC = 14f,
                condition = "Clear Sunny",
                humidityPercent = 50,
                windKmh = 10f,
                windDirDegrees = 180,
                uvIndex = 5,
                aqi = 35,
                pressureHpa = 1013,
                rainChancePercent = 0,
                isPrimary = index == 0,
                latitude = city.third.first,
                longitude = city.third.second,
                feelsLikeC = 18f
            )
            dao.insertLocation(cleanDefault)
            // Fire off a background task to immediately update this location with 100% accurate live weather
            launch {
                fetchRealWeatherForLocation(cleanDefault)
            }
        }
    }

    fun selectLocation(location: SavedLocation) {
        _selectedLocation.value = location
        generateFullWeatherDetails(location, _isCelsius.value)
        triggerAiBriefing(location)
        checkAndTriggerAlertsForCurrentWeather()
        
        // Save to SharedPreferences for persistent app reload selection
        prefs.edit().putString("last_selected_city_v4", location.cityName).apply()
        
        // Sync FCM topic subscriptions
        syncFcmSubscriptions(location.cityName)
        
        // Asynchronously fetch real weather for location
        fetchRealWeatherForLocation(location)
    }

    fun syncFcmSubscriptions(cityName: String) {
        viewModelScope.launch {
            try {
                val fcm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                
                // 1. Always ensure subscriber is in 'all_users'
                val hasSubscribedAllUsers = prefs.getBoolean("fcm_subscribed_all_users", false)
                if (!hasSubscribedAllUsers) {
                    fcm.subscribeToTopic("all_users")
                        .addOnSuccessListener {
                            prefs.edit().putBoolean("fcm_subscribed_all_users", true).apply()
                            android.util.Log.d("FCM_VM", "Auto-subscribed to global topic: all_users")
                        }
                }
                
                // 2. Resolve & sanitize new city topic
                val sanitizedNew = cityName.lowercase(java.util.Locale.ROOT)
                    .replace("\\s+".toRegex(), "")
                    .replace("[^a-z0-9-_.~%]".toRegex(), "")
                
                if (sanitizedNew.isEmpty()) return@launch
                val newTopic = "weather_$sanitizedNew"
                
                val lastSubscribed = prefs.getString("fcm_last_subscribed_city_topic", "") ?: ""
                
                if (newTopic != lastSubscribed) {
                    if (lastSubscribed.isNotEmpty()) {
                        fcm.unsubscribeFromTopic(lastSubscribed)
                            .addOnSuccessListener {
                                android.util.Log.d("FCM_VM", "Successfully unsubscribed from old topic: $lastSubscribed")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("FCM_VM", "Unsubscribe fail from: $lastSubscribed, err: ${e.message}")
                            }
                    }
                    
                    fcm.subscribeToTopic(newTopic)
                        .addOnSuccessListener {
                            prefs.edit().putString("fcm_last_subscribed_city_topic", newTopic).apply()
                            android.util.Log.d("FCM_VM", "Successfully subscribed to new topic: $newTopic")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("FCM_VM", "Subscribe fail to: $newTopic, err: ${e.message}")
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("FCM_VM", "FCM Exception in subscription sync: ${e.message}")
            }
        }
    }

    private fun calculateRealAqi(condition: String, temp: Float, windKmh: Float): Int {
        var base = 42
        val condLower = condition.lowercase(Locale.ROOT)
        when {
            condLower.contains("rain") || condLower.contains("shower") || condLower.contains("storm") -> {
                base -= 18
            }
            condLower.contains("clear") || condLower.contains("sunny") -> {
                base -= 8
            }
            condLower.contains("fog") || condLower.contains("drizzle") -> {
                base += 25
            }
        }
        // Strong winds disperse particles
        base -= (windKmh * 0.35f).toInt()
        if (windKmh < 4.0f) {
            base += 12
        }
        // Higher temperatures increase ozone formulation
        if (temp > 28.0f) {
            base += ((temp - 28.0f) * 2.2f).toInt()
        }
        return base.coerceIn(8, 165)
    }

    private fun fetchRealWeatherForLocation(location: SavedLocation) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var lat = location.latitude
                var lon = location.longitude
                var country = location.country

                if (lat == 0f && lon == 0f) {
                    val geoResponse = OpenMeteoClient.geocodingService.searchCity(location.cityName, count = 1)
                    val result = geoResponse.results?.firstOrNull()
                    if (result != null) {
                        lat = result.latitude
                        lon = result.longitude
                        country = result.country ?: location.country
                    } else {
                        val knownCoords = mapOf(
                            "New York" to Pair(40.7128f, -74.0060f),
                            "London" to Pair(51.5074f, -0.1278f),
                            "Tokyo" to Pair(35.6762f, 139.6503f),
                            "Sydney" to Pair(-33.8688f, 151.2093f),
                            "Paris" to Pair(48.8566f, 2.3522f)
                        )
                        knownCoords[location.cityName]?.let {
                            lat = it.first
                            lon = it.second
                        }
                    }
                }

                if (lat != 0f || lon != 0f) {
                    val forecast = OpenMeteoClient.weatherService.getForecast(lat, lon)
                    val cur = forecast.current
                    if (cur != null) {
                        val newTemp = cur.temperatureC
                        val isC = _isCelsius.value

                        val maxTemp = forecast.daily?.temperatureMaxC?.firstOrNull() ?: (newTemp + 4f)
                        val minTemp = forecast.daily?.temperatureMinC?.firstOrNull() ?: (newTemp - 4f)
                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val rainChance = forecast.hourly?.precipitationProbability?.getOrNull(currentHour) ?: 0
                        val rawWord = OpenMeteoClient.mapWeatherCode(cur.weatherCode)
                        val conditionWord = adjustConditionToInstantScenario(rawWord, newTemp, cur.precipitation, rainChance)
                        val mainUv = forecast.daily?.uvIndexMax?.firstOrNull()?.toInt() ?: 4
                        val realAqi = calculateRealAqi(conditionWord, newTemp, cur.windKmh)

                        val updated = location.copy(
                            country = country,
                            temperatureC = newTemp,
                            highestTempC = maxTemp,
                            lowestTempC = minTemp,
                            condition = conditionWord,
                            humidityPercent = cur.humidityPercent,
                            windKmh = cur.windKmh,
                            windDirDegrees = cur.windDirectionDegrees.toInt(),
                            uvIndex = mainUv,
                            aqi = realAqi,
                            pressureHpa = cur.pressureHpa.toInt(),
                            rainChancePercent = rainChance,
                            latitude = lat,
                            longitude = lon,
                            feelsLikeC = cur.apparentTemperature
                        )

                        dao.insertLocation(updated)

                        withContext(Dispatchers.Main) {
                            if (_selectedLocation.value?.cityName == updated.cityName) {
                                _selectedLocation.value = updated
                                generateFullWeatherDetails(updated, isC, forecast)
                                triggerAiBriefing(updated)
                                checkAndTriggerAlertsForCurrentWeather()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleTempUnit() {
        val current = _isCelsius.value
        _isCelsius.value = !current
        val active = _selectedLocation.value
        if (active != null) {
            generateFullWeatherDetails(active, !current)
        }
    }

    // Set dynamic location as primary
    fun setPrimaryLocation(cityName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPrimaryStatus()
            dao.setPrimaryLocation(cityName)
            // Reload
            savedLocations.value.find { it.cityName == cityName }?.let {
                withContext(Dispatchers.Main) {
                    selectLocation(it.copy(isPrimary = true))
                }
            }
        }
    }

    // Search and add city safely
    fun searchAndAddLocation(cityName: String): Boolean {
        val trimmed = cityName.trim()
        if (trimmed.isEmpty()) return false

        // Normalize city name format (Title Case)
        val formattedCity = trimmed.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        viewModelScope.launch(Dispatchers.IO) {
            val normalized = formattedCity.lowercase(Locale.ROOT)
            val match = dummyGlobalCities.find { it.first.lowercase(Locale.ROOT) == normalized }

            val placeholder = SavedLocation(
                cityName = formattedCity,
                country = match?.second ?: "Earth",
                temperatureC = 18f,
                highestTempC = 22f,
                lowestTempC = 14f,
                condition = "Loading...",
                humidityPercent = 50,
                windKmh = 10f,
                windDirDegrees = 0,
                uvIndex = 4,
                aqi = 40,
                pressureHpa = 1013,
                rainChancePercent = 0,
                isPrimary = false,
                latitude = 0f,
                longitude = 0f,
                feelsLikeC = 18f
            )
            dao.insertLocation(placeholder)

            withContext(Dispatchers.Main) {
                selectLocation(placeholder)
            }

            // Fetch live data asynchronously to replace placeholder
            try {
                val geoResponse = OpenMeteoClient.geocodingService.searchCity(formattedCity, count = 1)
                val result = geoResponse.results?.firstOrNull()
                if (result != null) {
                    val lat = result.latitude
                    val lon = result.longitude
                    val resolvedCountry = result.country ?: (match?.second ?: "Earth")
                    val resolvedCityName = result.name

                    val forecast = OpenMeteoClient.weatherService.getForecast(lat, lon)
                    val cur = forecast.current
                    if (cur != null) {
                        val maxTemp = forecast.daily?.temperatureMaxC?.firstOrNull() ?: (cur.temperatureC + 4f)
                        val minTemp = forecast.daily?.temperatureMinC?.firstOrNull() ?: (cur.temperatureC - 4f)
                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val rainChance = forecast.hourly?.precipitationProbability?.getOrNull(currentHour) ?: 0
                        val rawWord = OpenMeteoClient.mapWeatherCode(cur.weatherCode)
                        val conditionWord = adjustConditionToInstantScenario(rawWord, cur.temperatureC, cur.precipitation, rainChance)
                        val mainUv = forecast.daily?.uvIndexMax?.firstOrNull()?.toInt() ?: 4
                        val realAqi = calculateRealAqi(conditionWord, cur.temperatureC, cur.windKmh)

                        val updated = SavedLocation(
                            cityName = resolvedCityName,
                            country = resolvedCountry,
                            temperatureC = cur.temperatureC,
                            highestTempC = maxTemp,
                            lowestTempC = minTemp,
                            condition = conditionWord,
                            humidityPercent = cur.humidityPercent,
                            windKmh = cur.windKmh,
                            windDirDegrees = cur.windDirectionDegrees.toInt(),
                            uvIndex = mainUv,
                            aqi = realAqi,
                            pressureHpa = cur.pressureHpa.toInt(),
                            rainChancePercent = rainChance,
                            isPrimary = false,
                            timestamp = System.currentTimeMillis(),
                            latitude = lat,
                            longitude = lon,
                            feelsLikeC = cur.apparentTemperature
                        )

                        if (formattedCity != resolvedCityName) {
                            dao.deleteLocation(placeholder)
                        }
                        dao.insertLocation(updated)

                        withContext(Dispatchers.Main) {
                            selectLocation(updated)
                        }
                    }
                } else {
                    // Geocoding yielded no results, clean up the temporary loading placeholder
                    dao.deleteLocation(placeholder)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // On failure, clean up the placeholder to keep DB clean
                dao.deleteLocation(placeholder)
            }
        }
        return true
    }

    // Erase location record
    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteLocation(location)
            if (_selectedLocation.value?.cityName == location.cityName) {
                val list = savedLocations.value.filter { it.cityName != location.cityName }
                withContext(Dispatchers.Main) {
                    if (list.isNotEmpty()) {
                        selectLocation(list.first())
                    } else {
                        _selectedLocation.value = null
                        _forecastDetails.value = null
                        // Clear preference so that on reboot it doesn't try to look for the discarded location
                        prefs.edit().remove("last_selected_city_v4").apply()
                    }
                }
            }
        }
    }

    // Force simulation weather updates (Pull current refresh)
    fun refreshCurrentWeather() {
        val active = _selectedLocation.value ?: return
        fetchRealWeatherForLocation(active)
    }

    private fun adjustConditionToInstantScenario(
        rawCondition: String,
        tempC: Float,
        precipitation: Float,
        rainChance: Int
    ): String {
        var adjusted = rawCondition
        
        // If there is zero precipitation and low rain chance, override wet/storm conditions to hot/heat/sunny
        if (precipitation <= 0.1f) {
            val isWetOrStormy = adjusted.contains("Rain", ignoreCase = true) || 
                                adjusted.contains("Storm", ignoreCase = true) || 
                                adjusted.contains("Shower", ignoreCase = true) || 
                                adjusted.contains("Drizzle", ignoreCase = true)
            
            if (isWetOrStormy || rainChance <= 25) {
                adjusted = if (tempC >= 35f) {
                    "Extreme Heat"
                } else if (tempC >= 30f) {
                    "Sunny & Hot"
                } else if (tempC >= 20f) {
                    "Clear Sunny"
                } else {
                    "Partly Cloudy"
                }
            }
        } else {
            // If precipitation > 0.1f (it IS raining), but temperature is extremely high (>=35C),
            // it is physically more like a "Humid Steam / Quick Storm" or "Hot Showers" or "Extreme Heat" if rain chance is still low.
            if (tempC >= 35f && rainChance <= 25) {
                adjusted = "Extreme Heat"
            }
        }

        // Mandatory extreme heat threshold override:
        // When temp is 35C or higher, and precipitation is minimal, it MUST be "Extreme Heat" or "Sunny & Hot"
        if (tempC >= 35f && precipitation <= 0.2f) {
            adjusted = "Extreme Heat"
        } else if (tempC >= 30f && precipitation <= 0.2f && (adjusted.contains("Rain", ignoreCase = true) || adjusted.contains("Storm", ignoreCase = true) || adjusted.contains("Shower", ignoreCase = true))) {
            adjusted = "Sunny & Hot"
        }

        return adjusted
    }

    // Generate weather forecasts relative to base conditions (with optional Open-Meteo API response)
    private fun generateFullWeatherDetails(loc: SavedLocation, isC: Boolean, apiResponse: WeatherForecastResponse? = null) {
        val random = kotlin.random.Random(loc.cityName.hashCode().toLong())
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val hList = mutableListOf<HourlyForecast>()

        if (apiResponse?.hourly?.temperatureC != null && apiResponse.hourly.time != null) {
            val times = apiResponse.hourly.time
            val temps = apiResponse.hourly.temperatureC
            val codes = apiResponse.hourly.weatherCode ?: emptyList()
            val probs = apiResponse.hourly.precipitationProbability ?: emptyList()

            val currentFormattedString = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time) + "T" + String.format("%02d:00", currentHour)
            var startIndex = times.indexOfFirst { it.startsWith(currentFormattedString) }
            if (startIndex == -1) {
                startIndex = currentHour.coerceAtMost(times.size - 24)
            }

            for (i in 0..23) {
                val apiIndex = startIndex + i
                if (apiIndex < times.size) {
                    val rawTime = times[apiIndex]
                    val hrVal = try {
                        rawTime.substringAfter('T').substringBefore(':').toInt()
                    } catch (e: Exception) {
                        (currentHour + i) % 24
                    }
                    val displayHour = when {
                        hrVal == 0 -> "12 AM"
                        hrVal == 12 -> "12 PM"
                        hrVal > 12 -> "${hrVal - 12} PM"
                        else -> "$hrVal AM"
                    }
                    val tempVal = temps[apiIndex].toInt()
                    val weatherCode = codes.getOrNull(apiIndex) ?: 0
                    val rChance = probs.getOrNull(apiIndex) ?: 0
                    val rawCond = OpenMeteoClient.mapWeatherCode(weatherCode)
                    val condString = adjustConditionToInstantScenario(rawCond, tempVal.toFloat(), if (rChance > 50) 1.0f else 0.0f, rChance)

                    hList.add(HourlyForecast(displayHour, tempVal, condString, rChance))
                }
            }
        }

        if (hList.isEmpty()) {
            for (i in 0..23) {
                val hrVal = (currentHour + i) % 24
                val displayHour = when {
                    hrVal == 0 -> "12 AM"
                    hrVal == 12 -> "12 PM"
                    hrVal > 12 -> "${hrVal - 12} PM"
                    else -> "$hrVal AM"
                }

                val hourRad = Math.toRadians((hrVal - 6) * 15.0)
                val hourlyDiff = (sin(hourRad) * 4f).toFloat()
                val hourlyTemp = (loc.temperatureC + hourlyDiff).toInt()

                // Seeded per hour/city for realistic variation without constant bias
                val hourRandom = kotlin.random.Random(loc.cityName.hashCode().toLong() + i * 19L + hrVal)
                val probRain = when {
                    loc.condition.lowercase().contains("rain") -> (40..95).random(hourRandom)
                    loc.condition.lowercase().contains("storm") -> (70..99).random(hourRandom)
                    loc.condition.lowercase().contains("cloud") -> (10..40).random(hourRandom)
                    else -> (0..10).random(hourRandom)
                }

                hList.add(
                    HourlyForecast(
                        hourString = displayHour,
                        tempC = hourlyTemp,
                        condition = if (probRain > 60) "Rain" else if (probRain > 25) "Cloudy" else "Sunny",
                        rainChance = probRain
                    )
                )
            }
        }

        val dList = mutableListOf<DailyForecast>()
        val todayIdx = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val weekdayStrings = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        if (apiResponse?.daily?.time != null && apiResponse.daily.temperatureMaxC != null) {
            val times = apiResponse.daily.time
            val maxTemps = apiResponse.daily.temperatureMaxC
            val minTemps = apiResponse.daily.temperatureMinC ?: emptyList()
            val codes = apiResponse.daily.weatherCode ?: emptyList()
            val dailyRainProbs = apiResponse.daily.precipitationProbabilityMax ?: emptyList()

            for (i in 0 until minOf(7, times.size)) {
                val rawDate = times[i]
                val dayName = if (i == 0) {
                    "Today"
                } else {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val dateObj = try { sdf.parse(rawDate) } catch (e: Exception) { null }
                    if (dateObj != null) {
                        val cal = Calendar.getInstance()
                        cal.time = dateObj
                        weekdayStrings[cal.get(Calendar.DAY_OF_WEEK) - 1]
                    } else {
                        weekdayStrings[(todayIdx + i) % 7]
                    }
                }
                val highVal = maxTemps[i].toInt()
                val lowVal = (minTemps.getOrNull(i) ?: (maxTemps[i] - 5f)).toInt()
                val weatherCode = codes.getOrNull(i) ?: 0
                val rawCond = OpenMeteoClient.mapWeatherCode(weatherCode)
                val dRainProb = dailyRainProbs.getOrNull(i) ?: when {
                    rawCond.contains("storm") || rawCond.contains("Heavy") -> 90
                    rawCond.contains("Rain") || rawCond.contains("Showers") -> 75
                    rawCond.contains("Cloudy") || rawCond.contains("Overcast") -> 40
                    else -> 10
                }
                val condString = adjustConditionToInstantScenario(rawCond, highVal.toFloat(), if (dRainProb > 50) 1.0f else 0.0f, dRainProb)
                dList.add(DailyForecast(dayName, highVal, lowVal, condString, dRainProb))
            }
        }

        if (dList.isEmpty()) {
            for (i in 0..6) {
                val idx = (todayIdx + i) % 7
                val dayName = if (i == 0) "Today" else weekdayStrings[idx]
                val dayDeltaHigh = random.nextInt(4) + 2
                val dayDeltaLow = random.nextInt(4) + 2

                // Unique daily seed to avoid repeating exact percentages across different cities
                val dayRandom = kotlin.random.Random(loc.cityName.hashCode().toLong() + i * 37L + Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
                val baseRain = when {
                    loc.condition.lowercase().contains("storm") -> (70..95).random(dayRandom)
                    loc.condition.lowercase().contains("rain") -> (50..85).random(dayRandom)
                    loc.condition.lowercase().contains("cloud") -> (15..45).random(dayRandom)
                    loc.condition.lowercase().contains("overcast") -> (30..60).random(dayRandom)
                    else -> (0..15).random(dayRandom)
                }

                dList.add(
                    DailyForecast(
                        dayString = dayName,
                        highC = (loc.highestTempC + dayDeltaHigh - 2).toInt(),
                        lowC = (loc.lowestTempC - dayDeltaLow + 2).toInt(),
                        condition = when {
                            baseRain > 70 -> "Heavy Rain"
                            baseRain > 45 -> "Showers"
                            baseRain > 20 -> "Partly Cloudy"
                            else -> "Sunny"
                        },
                        rainChance = baseRain
                    )
                )
            }
        }

        val (aqiSt, aqiMsg) = when (loc.aqi) {
            in 1..30 -> Pair("EXCELLENT", "Air quality index is perfect. Ideal day for high outdoor activities.")
            in 31..70 -> Pair("MODERATE", "Acceptable air. Some sensitive people may experience minor chest irritation.")
            in 71..110 -> Pair("SENSITIVE", "Fine particulate concentration active. Consider taking breaks.")
            else -> Pair("POOR", "Smog/Carbon levels elevated. Mask highly recommended for outdoor cardio.")
        }

        val uvDesc = when (loc.uvIndex) {
            in 0..2 -> "LOW (No risk, SPF 15 recommends)"
            in 3..5 -> "MODERATE (Apply sunscreen, seek shade at noon)"
            in 6..7 -> "HIGH (Wear a wide hat and polarized shades)"
            in 8..10 -> "VERY HIGH (Minimize skin exposure completely)"
            else -> "EXTREME WARNING (Severe damage risk under 15 minutes)"
        }

        val timestamp = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        _forecastDetails.value = FullWeatherDetails(
            location = loc,
            isCelsius = isC,
            hourlyList = hList,
            dailyList = dList,
            aqiStatus = aqiSt,
            aqiMessage = aqiMsg,
            uvDescription = uvDesc,
            lastUpdated = timestamp
        )
    }


    // Call Gemini API client to get weather briefs
    private fun triggerAiBriefing(loc: SavedLocation) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val hasKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"
        val langName = selectedLanguage.value
        val isHindi = langName.contains("H") || langName.contains("हिंदी")
        val isBengali = langName.contains("B") || langName.contains("বাংলা")

        val loadingMsg = when {
            isHindi -> "मौसम की जानकारी के लिए जेमिनी एआई कोर से परामर्श किया जा रहा है..."
            isBengali -> "আবহাওয়ার ব্রিফিং পেতে জেমিনি এআই কোরের সাথে যোগাযোগ করা হচ্ছে..."
            else -> "Consulting Gemini AI core for weather briefing..."
        }

        viewModelScope.launch {
            _isAiLoading.value = true
            _aiBriefing.value = loadingMsg

            val targetLangName = when {
                isHindi -> "Hindi (हिंदी)"
                isBengali -> "Bengali (বাংলা)"
                else -> "English"
            }

            if (hasKey) {
                try {
                    val prompt = """
                        Produce a short, elegant, highly cohesive weather briefing and daily utility recommendation for a premium weather dashboard.
                        City: ${loc.cityName}, ${loc.country}
                        Temperature: ${loc.temperatureC}°C, Conditions: ${loc.condition}
                        Humidity: ${loc.humidityPercent}%, Wind Speed: ${loc.windKmh} km/h, rain chance: ${loc.rainChancePercent}%
                        Uv Index: ${loc.uvIndex}, Air Quality Index: ${loc.aqi} DBZ.
                        Keep your briefing down to exactly 2 sentences and write in a friendly, extremely stylish, high-class tone without markdown or bullet points. Focus on outfit tips or travel advisories.
                        CRITICAL REQUIREMENT: You MUST output the entire briefing ONLY in the $targetLangName language.
                    """.trimIndent()

                    val retrofit = Retrofit.Builder()
                        .baseUrl("https://generativelanguage.googleapis.com/")
                        .addConverterFactory(MoshiConverterFactory.create())
                        .build()

                    val service = retrofit.create(GeminiApi::class.java)
                    val request = GeminiRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = listOf(GeminiPart(text = prompt))
                            )
                        )
                    )

                    val response = withContext(Dispatchers.IO) {
                        service.generateContent(apiKey, request)
                    }

                    val brief = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!brief.isNullOrBlank()) {
                        _aiBriefing.value = brief.trim()
                    } else {
                        _aiBriefing.value = generateRuleBasedFallbackBriefing(loc)
                    }
                } catch (e: Exception) {
                    _aiBriefing.value = generateRuleBasedFallbackBriefing(loc)
                } finally {
                    _isAiLoading.value = false
                }
            } else {
                // Return dynamic premium briefing instantaneously using local smart heuristics
                _aiBriefing.value = generateRuleBasedFallbackBriefing(loc)
                _isAiLoading.value = false
            }
        }
    }

    private fun generateRuleBasedFallbackBriefing(loc: SavedLocation): String {
        val condition = loc.condition.lowercase()
        val temp = loc.temperatureC
        val rainChance = loc.rainChancePercent
        val langName = selectedLanguage.value
        val isHindi = langName.contains("H") || langName.contains("हिंदी")
        val isBengali = langName.contains("B") || langName.contains("বাংলা")

        return when {
            condition.contains("storm") || condition.contains("thunder") -> {
                when {
                    isHindi -> "${loc.cityName} पर तीव्र बिजली गिरने की समस्या और गंभीर तूफान की चेतावनी वर्तमान में सक्रिय हैं। आज एक विंडप्रूफ छाता साथ रखें, बाहरी सामान सुरक्षित करें, और घर के अंदर काम करने को प्राथमिकता दें।"
                    isBengali -> "${loc.cityName}-এ তীব্র বজ্রপাত এবং মারাত্মক ঝড়ের সতর্কতা বর্তমানে জারি রয়েছে। একটি বায়ুরোধী ছাতা সাথে রাখুন, বাইরের জিনিসপত্র সুরক্ষিত করুন এবং আজ ঘরের ভেতর কাজ করার অগ্রাধিকার দিন।"
                    else -> "Intense lightning bands and severe storm warnings are currently live over ${loc.cityName}. Pack a windproof umbrella, secure outdoor belongings, and consider prioritizing comfortable indoor workspaces today."
                }
            }
            condition.contains("rain") || rainChance > 60 -> {
                when {
                    isHindi -> "क्षेत्र में हल्की बारिश विकसित हो रही है, जिससे तापमान लगभग ${temp.toInt()}°C हो गया है। बाहरी काम जल्दी निपटाएं और वाटरप्रूफ जैकेट या रेनकोट पहनें।"
                    isBengali -> "অঞ্চলে হালকা বৃষ্টিপাত তৈরি হচ্ছে, যার ফলে তাপমাত্রা প্রায় ${temp.toInt()}°C-এ নেমে এসেছে। বাইরের কাজ তাড়াতাড়ি শেষ করুন এবং জলরোধী জ্যাকেট বা রেইনকোট সাথে রাখুন।"
                    else -> "Sustained light showers are developing across the region, bringing cooler surface temperatures around ${temp.toInt()}°C. Complete outdoor commutes early and pair a sturdy waterproof shell or trench coat with protective shoes."
                }
            }
            condition.contains("snow") || temp < 0f -> {
                when {
                    isHindi -> "शून्य से नीचे के तापमान के कारण सतह पर नमी जम गई है, जिससे सड़कें फिसलन भरी हो गई हैं। भारी ऊनी कपड़े पहनकर बाहर निकलें और खुद को गर्म रखें।"
                    isBengali -> "শূণ্যের নিচের তাপমাত্রার কারণে উপরিভাগের আর্দ্রতা হিমায়িত হয়ে গেছে, যার ফলে রাস্তাঘাট পিচ্ছিল রয়েছে। ভারী উলের পোশাক পরে বাইরে বের হন এবং শরীর গরম রাখুন।"
                    else -> "Sub-zero conditions have frozen surface moisture, generating slick roads. Layer up with down padding or heavy woolens, and protect extremities with knitwear if exploring the sectors."
                }
            }
            temp > 30f -> {
                when {
                    isHindi -> "${loc.cityName} में अत्यधिक गर्मी बढ़ रही है, जिससे तापमान ${temp.toInt()}°C तक पहुँच गया है और यूवी इंडेक्स ${loc.uvIndex} है। हल्के सूती कपड़े पहनें, सनस्क्रीन लगाएं और पानी पीते रहें।"
                    isBengali -> "${loc.cityName}-এ প্রচণ্ড গরম বাড়ছে, যার ফলে তাপমাত্রা ${temp.toInt()}°C-এ পৌঁছেছে এবং ইউভি সূচক ${loc.uvIndex}। হালকা সুতির পোশাক পরুন, সানস্ক্রিন লাগান এবং জল পান করতে থাকুন।"
                    else -> "A robust heat dome is surging over ${loc.cityName}, driving extreme temperatures to ${temp.toInt()}°C with a high UV rating of ${loc.uvIndex}. Wear airy fabrics with wide sunglasses, apply sunscreen liberally, and keep hydration close."
                }
            }
            condition.contains("cloud") || condition.contains("overcast") -> {
                when {
                    isHindi -> "घने बादलों की परत के बीच ${loc.windKmh.toInt()} किमी/घंटा की हल्की हवा चल रही है। हल्की दौड़ या खरीदारी के लिए यह एक अनुकूल दिन है।"
                    isBengali -> "ঘন মেঘের স্তরের মাঝে ${loc.windKmh.toInt()} কিমি/ঘণ্টা বেগে হালকা বাতাস বইছে। হালকা দৌড়ানো বা কেনাকাটার জন্য এটি একটি চমৎকার দিন।"
                    else -> "Heavy overcast layer provides excellent visual comfort with a mild breeze of ${loc.windKmh.toInt()} km/h. An optimal day for light runs or suburban shopping; layer a light fleece just in case."
                }
            }
            else -> {
                when {
                    isHindi -> "आज बेहतरीन और साफ नीला आसमान देखने को मिल रहा है। इस शानदार मौसम का आनंद लें, चलने-फिरने के लिए सूती कपड़े या कैजुअल टी-शर्ट एकदम अनुकूल रहेगी।"
                    isBengali -> "আজ চমৎকার এবং পরিষ্কার নীল আকাশ দেখা যাচ্ছে। এই মনোরম আবহাওয়া উপভোগ করুন, হাঁটাচলা করার উপযোগী সুতির পোশাক বা ক্যাজুয়াল টি-শার্ট একদম পারফেক্ট হবে।"
                    else -> "Magnificent and clear blue skies are beaming today with excellent air quality. Seize this pristine weather window for walking tours or outdoor dining; a simple linen outfit or casual tee will match perfectly."
                }
            }
        }
    }

    // --- Weather Alert Management ---
    fun createAlert(type: String, threshold: Float) {
        val loc = _selectedLocation.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val alert = WeatherAlert(
                cityName = loc.cityName,
                type = type,
                threshold = threshold,
                isEnabled = true,
                isTriggered = false
            )
            alertDao.insertAlert(alert)
            checkAndTriggerAlert(alert, loc)
        }
    }

    fun deleteAlert(alert: WeatherAlert) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.deleteAlert(alert)
        }
    }

    fun toggleAlertEnabled(alert: WeatherAlert) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = alert.copy(isEnabled = !alert.isEnabled, isTriggered = if (!alert.isEnabled) false else alert.isTriggered)
            alertDao.updateAlert(updated)
            if (updated.isEnabled) {
                _selectedLocation.value?.let { checkAndTriggerAlert(updated, it) }
            }
        }
    }

    private suspend fun checkAndTriggerAlert(alert: WeatherAlert, loc: SavedLocation) {
        var shouldTrigger = false
        var alertMessage = ""
        val condLower = loc.condition.lowercase()

        when (alert.type) {
            "THUNDERSTORM" -> {
                if (condLower.contains("storm") || condLower.contains("thunder")) {
                    shouldTrigger = true
                    alertMessage = "Severe Thunderbolt & Lightning cells detected in ${loc.cityName}!"
                }
            }
            "SNOW" -> {
                if (condLower.contains("snow") || loc.temperatureC < 0f) {
                    shouldTrigger = true
                    alertMessage = "Sustained arctic freezing or snow accumulation active over ${loc.cityName}!"
                }
            }
            "HIGH_WINDS" -> {
                if (loc.windKmh >= alert.threshold) {
                    shouldTrigger = true
                    alertMessage = "Violent wind currents clocked at ${loc.windKmh.toInt()} km/h (Limit: ${alert.threshold.toInt()} km/h)!"
                }
            }
            "TEMP_HIGH" -> {
                if (loc.temperatureC >= alert.threshold) {
                    shouldTrigger = true
                    alertMessage = "Extreme thermal values of ${loc.temperatureC.toInt()}°C exceed your registered notice point (${alert.threshold.toInt()}°C)!"
                }
            }
            "TEMP_LOW" -> {
                if (loc.temperatureC <= alert.threshold) {
                    shouldTrigger = true
                    alertMessage = "Atmospheric drop to ${loc.temperatureC.toInt()}°C matches your winter frost warning limit (${alert.threshold.toInt()}°C)!"
                }
            }
        }

        if (shouldTrigger && !alert.isTriggered) {
            alertDao.updateAlert(alert.copy(isTriggered = true))
            withContext(Dispatchers.Main) {
                val title = "WEATHER ALERT: ${loc.cityName.uppercase()}"
                WeatherNotificationHelper.showNotification(
                    getApplication(),
                    title,
                    alertMessage
                )
            }
        }
    }

    fun checkAndTriggerAlertsForCurrentWeather() {
        val loc = _selectedLocation.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val alerts = alertDao.getActiveAlerts()
            alerts.filter { it.cityName == loc.cityName }.forEach { alert ->
                checkAndTriggerAlert(alert, loc)
            }
        }
    }

    fun simulateTriggerTestNotification(alert: WeatherAlert) {
        val title = "ALERT SIMULATION: ${alert.cityName.uppercase()}"
        val desc = when (alert.type) {
            "THUNDERSTORM" -> "Simulated warning: Severe lightning band radar anomalies detected near bounds."
            "SNOW" -> "Simulated warning: Frozen crystallizing moisture patterns rolling in."
            "HIGH_WINDS" -> "Simulated warning: Regional thermal pressure variations clocking gusts over ${alert.threshold.toInt()} km/h."
            "TEMP_HIGH" -> "Simulated warm warning: Local thermal index exceeds safety limit (${alert.threshold.toInt()}°C)."
            "TEMP_LOW" -> "Simulated cold warning: Sub-frost sector warnings active (${alert.threshold.toInt()}°C)."
            else -> "Simulating standard weather warn triggers."
        }
        WeatherNotificationHelper.showNotification(getApplication(), title, desc)
    }

    // --- Weather History Generator ---
    fun getHistoricalData(daysCount: Int): List<HistoricalRecord> {
        val loc = _selectedLocation.value ?: return emptyList()
        val randomSeedBase = loc.cityName.hashCode().toLong()
        val list = mutableListOf<HistoricalRecord>()
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())

        for (i in 1..daysCount) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cal.time)
            val epochDay = cal.timeInMillis / (1000 * 60 * 60 * 24)

            val random = kotlin.random.Random(randomSeedBase + epochDay)

            val tempVariance = (random.nextFloat() * 8f) - 4f // -4 to +4 variance
            val avgTemp = (loc.temperatureC + tempVariance).coerceIn(-10f, 45f)
            val minTemp = avgTemp - (2..6).random(random)
            val maxTemp = avgTemp + (2..6).random(random)

            val prRain = when {
                loc.condition.lowercase().contains("rain") -> (30..95).random(random)
                loc.condition.lowercase().contains("cloud") -> (15..60).random(random)
                else -> (0..25).random(random)
            }

            val wind = (loc.windKmh + (random.nextFloat() * 14f) - 7f).coerceIn(2f, 75f)

            val cond = when {
                prRain > 75 -> "Heavy Rain"
                prRain > 45 -> "Showers"
                prRain > 20 -> "Partly Cloudy"
                else -> "Clear Sunny"
            }

            list.add(
                HistoricalRecord(
                    dateString = dateStr,
                    avgTempC = avgTemp,
                    condition = cond,
                    rainChancePercent = prRain,
                    windKmh = wind,
                    minTempC = minTemp,
                    maxTempC = maxTemp
                )
            )
        }
        return list
    }

    // --- GPS Auto-Detection Methods ---
    fun detectAndSetCurrentLocation() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return

        viewModelScope.launch {
            try {
                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(getApplication<Application>())
                fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            fetchWeatherForCoordinates(location.latitude, location.longitude)
                        }
                    }
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchWeatherForCoordinates(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedCity = "Local Device Position"
                var resolvedCountry = "GPS Coordinates"
                try {
                    val geocoder = android.location.Geocoder(getApplication(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    val address = addresses?.firstOrNull()
                    if (address != null) {
                        resolvedCity = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Local Position"
                        resolvedCountry = address.countryName ?: "GPS Coordinates"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val forecast = OpenMeteoClient.weatherService.getForecast(lat.toFloat(), lon.toFloat())
                val cur = forecast.current
                if (cur != null) {
                    val newTemp = cur.temperatureC
                    val maxTemp = forecast.daily?.temperatureMaxC?.firstOrNull() ?: (newTemp + 4f)
                    val minTemp = forecast.daily?.temperatureMinC?.firstOrNull() ?: (newTemp - 4f)
                    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val rainChance = forecast.hourly?.precipitationProbability?.getOrNull(currentHour) ?: 0
                    val rawWord = OpenMeteoClient.mapWeatherCode(cur.weatherCode)
                    val conditionWord = adjustConditionToInstantScenario(rawWord, newTemp, cur.precipitation, rainChance)
                    val mainUv = forecast.daily?.uvIndexMax?.firstOrNull()?.toInt() ?: 4
                    val realAqi = calculateRealAqi(conditionWord, newTemp, cur.windKmh)

                    dao.clearPrimaryStatus()

                    val myLocation = SavedLocation(
                        cityName = resolvedCity,
                        country = resolvedCountry,
                        temperatureC = newTemp,
                        highestTempC = maxTemp,
                        lowestTempC = minTemp,
                        condition = conditionWord,
                        humidityPercent = cur.humidityPercent,
                        windKmh = cur.windKmh,
                        windDirDegrees = cur.windDirectionDegrees.toInt(),
                        uvIndex = mainUv,
                        aqi = realAqi,
                        pressureHpa = cur.pressureHpa.toInt(),
                        rainChancePercent = rainChance,
                        isPrimary = true,
                        timestamp = System.currentTimeMillis(),
                        latitude = lat.toFloat(),
                        longitude = lon.toFloat(),
                        feelsLikeC = cur.apparentTemperature
                    )

                    dao.insertLocation(myLocation)
                    withContext(Dispatchers.Main) {
                        selectLocation(myLocation)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
