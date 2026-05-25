package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.content.Intent
import com.example.R
import com.example.db.SavedLocation
import com.example.db.WeatherAlert
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDashboardScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
    showWidgetStudioOverride: Boolean = false,
    onWidgetStudioOverrideChange: ((Boolean) -> Unit)? = null
) {
    val selectedLoc by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val forecastDetails by viewModel.forecastDetails.collectAsStateWithLifecycle()
    val savedLocations by viewModel.savedLocations.collectAsStateWithLifecycle()
    val isCelsius by viewModel.isCelsius.collectAsStateWithLifecycle()
    val aiBriefing by viewModel.aiBriefing.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

    // Customize transparency level & visual themes observed from settings
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val transparencyLevel by viewModel.transparencyLevel.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    var showCitySearch by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showWidgetStudioLocal by remember { mutableStateOf(false) }
    val showWidgetStudio = if (onWidgetStudioOverrideChange != null) showWidgetStudioOverride else showWidgetStudioLocal
    val setShowWidgetStudio: (Boolean) -> Unit = { value ->
        if (onWidgetStudioOverrideChange != null) {
            onWidgetStudioOverrideChange(value)
        } else {
            showWidgetStudioLocal = value
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var isRadarExpanded by remember { mutableStateOf(false) }
    var activeRadarLayer by remember { mutableStateOf(RadarLayer.PRECIPITATION) }
    var isRadarPlaying by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showNotificationExplanationDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Smart alerts activated!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Alert notifications deactivated.", Toast.LENGTH_LONG).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.detectAndSetCurrentLocation()
            Toast.makeText(context, "Current location detection initialized", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission denied. Primary cities active.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasCoarse || hasFine) {
            viewModel.detectAndSetCurrentLocation()
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            val prefs = context.getSharedPreferences("weather_prefs_v3", android.content.Context.MODE_PRIVATE)
            val hasPrompted = prefs.getBoolean("fcm_has_prompted_notifications", false)
            
            if (!isGranted && !hasPrompted) {
                showNotificationExplanationDialog = true
            }
        }
    }

    if (showNotificationExplanationDialog) {
        AlertDialog(
            onDismissRequest = { 
                showNotificationExplanationDialog = false 
                context.getSharedPreferences("weather_prefs_v3", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("fcm_has_prompted_notifications", true).apply()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color(0xFF7491FF),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Activate Smart Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Enable notifications to stay informed with real-time weather changes and active radar updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE4E4E7)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                "Severe Storm & Heat Warnings",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                "Urgent announcements for floods, extreme rainfall, or high wind gusts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA1A1AA)
                            )
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = Color(0xFF00B0FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                "Daily Briefings & Rain Alerts",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                "Morning climate insights and outfit tips specifically tailored for you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA1A1AA)
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFF1E2229),
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationExplanationDialog = false
                        context.getSharedPreferences("weather_prefs_v3", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("fcm_has_prompted_notifications", true).apply()
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                        }
                    }
                ) {
                    Text("Enable Alerts", color = Color(0xFF7491FF), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationExplanationDialog = false
                        context.getSharedPreferences("weather_prefs_v3", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("fcm_has_prompted_notifications", true).apply()
                    }
                ) {
                    Text("Maybe Later", color = Color(0xFF71717A))
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val condition = selectedLoc?.condition?.lowercase() ?: "sunny"
                val baseColors = when (themeMode) {
                    "Amoled Black" -> listOf(Color(0xFF000000), Color(0xFF060606))
                    "Deep Cosmic" -> when {
                        condition.contains("storm") -> listOf(Color(0xFF0F0024), Color(0xFF16012F))
                        condition.contains("rain") || condition.contains("shower") -> listOf(Color(0xFF0A0214), Color(0xFF16062D))
                        condition.contains("cloud") || condition.contains("overcast") -> listOf(Color(0xFF09001C), Color(0xFF1B073B))
                        condition.contains("snow") -> listOf(Color(0xFF0A0216), Color(0xFF1F0B42))
                        else -> listOf(Color(0xFF060012), Color(0xFF15042E))
                    }
                    "Neon Cyan" -> when {
                        condition.contains("storm") -> listOf(Color(0xFF00121C), Color(0xFF012233))
                        condition.contains("rain") || condition.contains("shower") -> listOf(Color(0xFF010E17), Color(0xFF021C2F))
                        condition.contains("cloud") || condition.contains("overcast") -> listOf(Color(0xFF01121C), Color(0xFF042436))
                        condition.contains("snow") -> listOf(Color(0xFF011420), Color(0xFF032B40))
                        else -> listOf(Color(0xFF000A10), Color(0xFF011F30))
                    }
                    else -> { // Slate Dark
                        when {
                            condition.contains("storm") -> listOf(Color(0xFF07090C), Color(0xFF13171F))
                            condition.contains("rain") || condition.contains("shower") -> listOf(Color(0xFF0B0E11), Color(0xFF1B232D))
                            condition.contains("cloud") || condition.contains("overcast") -> listOf(Color(0xFF0D0F11), Color(0xFF1F2429))
                            condition.contains("snow") -> listOf(Color(0xFF0F1216), Color(0xFF222B36))
                            else -> listOf(Color(0xFF0F1113), Color(0xFF1B1E29))
                        }
                    }
                }
                drawRect(
                    brush = Brush.verticalGradient(colors = baseColors),
                    size = size
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Falling rain/clouds animation layer (Canvas drawing based on weather condition)
        WeatherAtmosphereAnimationLayer(condition = selectedLoc?.condition ?: "Sunny")

        // Main structural feed
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clickable { showCitySearch = true }
                        .testTag("location_button")
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Location Pin",
                        tint = Color(0xFF7491FF), // Sleek accent blue
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedLoc?.cityName ?: "Weather",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
                            color = Color(0xFFE2E2E6)
                        )
                        val formattedDate = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date())
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8C9199)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = Color(0xFFC4C6CF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Auto GPS Detection Trigger
                    IconButton(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF1F2429))
                            .testTag("location_detect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Detect My Location",
                            tint = Color(0xFF7491FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.refreshCurrentWeather() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF1F2429))
                            .testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Update Weather",
                            tint = Color(0xFFE2E2E6),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showSettingsScreen = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF1F2429))
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFE2E2E6),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Radar Expanded view fits inside single screen as a toggled layer
            AnimatedVisibility(
                visible = isRadarExpanded,
                enter = expandIn(expandFrom = Alignment.Center) + fadeIn(),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                FullRadarOverlayContainer(
                    location = selectedLoc,
                    activeLayer = activeRadarLayer,
                    onLayerChange = { activeRadarLayer = it },
                    isPlaying = isRadarPlaying,
                    onPlayChange = { isRadarPlaying = it },
                    onClose = { isRadarExpanded = false }
                )
            }

            // Standard dashboard view (when radar is not taking up the screen)
            if (!isRadarExpanded) {
                val themeCardBg = getThemeCardColor(themeMode = themeMode, transparencyLevel = transparencyLevel)
                val themeBorder = getThemeBorderColor(themeMode = themeMode, transparencyLevel = transparencyLevel)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("dashboard_scroller"),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // 1. Primary temperature readout
                    item {
                        PrimaryWeatherBanner(
                            location = selectedLoc,
                            isCelsius = isCelsius,
                            containerColor = themeCardBg,
                            borderColor = themeBorder
                        )
                    }

                    // 2. High fidelity Gemini Smart AI Briefing bubble
                    item {
                        AiBriefingCard(
                            briefingText = aiBriefing,
                            isLoading = isAiLoading,
                            onAiClick = { selectedLoc?.let { viewModel.selectLocation(it) } },
                            containerColor = themeCardBg,
                            borderColor = themeBorder
                        )
                    }

                    // 3. Mini Interactive Weather Radar teaser (Clicking it expands radar)
                    item {
                        MiniRadarTeaserCard(
                            location = selectedLoc,
                            onUnfoldClick = { isRadarExpanded = true },
                            containerColor = themeCardBg,
                            borderColor = themeBorder
                        )
                    }

                    // 4. 24-Hour Scrolling Forecast bar
                    item {
                        forecastDetails?.let { details ->
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = getTranslatedLabel("24-Hour Radar Timeline", selectedLanguage).uppercase(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                                    color = Color(0xFF7491FF),
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(details.hourlyList) { hour ->
                                        HourlyWidget(
                                            hour = hour,
                                            isCelsius = isCelsius,
                                            containerColor = themeCardBg,
                                            borderColor = themeBorder
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. High-precision dynamic weather gauges
                    item {
                        forecastDetails?.let { details ->
                            WeatherMetricsGrid(
                                details = details,
                                containerColor = themeCardBg,
                                borderColor = themeBorder,
                                selectedLanguage = selectedLanguage
                            )
                        }
                    }

                    // 6. 7-Day Forecast panels
                    item {
                        forecastDetails?.let { details ->
                            WeeklyForecastContainer(
                                details = details,
                                isCelsius = isCelsius,
                                containerColor = themeCardBg,
                                borderColor = themeBorder
                            )
                        }
                    }

                    // 7. Historical Weather Explorer
                    item {
                        forecastDetails?.let {
                            HistoricalWeatherCard(
                                viewModel = viewModel,
                                isCelsius = isCelsius,
                                containerColor = themeCardBg,
                                borderColor = themeBorder
                            )
                        }
                    }

                    // 8. Custom Weather alerts setup
                    item {
                        forecastDetails?.let {
                            WeatherAlertsCard(
                                viewModel = viewModel,
                                containerColor = themeCardBg,
                                borderColor = themeBorder
                            )
                        }
                    }
                }
            }
        }

        // Drawer / Dialog for managing target cities
        if (showCitySearch) {
            CitySelectionOverlay(
                savedLocations = savedLocations,
                activeCity = selectedLoc,
                onClose = { showCitySearch = false },
                onSelect = {
                    viewModel.selectLocation(it)
                    showCitySearch = false
                },
                onDelete = { viewModel.deleteLocation(it) },
                onAddCity = { query ->
                    val found = viewModel.searchAndAddLocation(query)
                    if (found) {
                        showCitySearch = false
                    }
                    found
                },
                onSetPrimary = { cityName ->
                    viewModel.setPrimaryLocation(cityName)
                }
            )
        }

        // Standalone Slide-Over setup page for Widget Customization Settings
        AnimatedVisibility(
            visible = showWidgetStudio,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            WidgetOverlayStudioPage(
                location = selectedLoc,
                forecastDetails = forecastDetails,
                isCelsius = isCelsius,
                onBack = { setShowWidgetStudio(false) }
            )
        }

        // Standalone Slide-Over setup page for Premium Custom Settings
        AnimatedVisibility(
            visible = showSettingsScreen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsPage(
                viewModel = viewModel,
                onBack = { showSettingsScreen = false },
                onShowWidgetStudio = {
                    showSettingsScreen = false
                    setShowWidgetStudio(true)
                }
            )
        }
    }
}

// Atmospheric simulation animations
@Composable
fun WeatherAtmosphereAnimationLayer(condition: String) {
    val condLower = condition.lowercase()

    val infiniteTransition = rememberInfiniteTransition(label = "AtmosphereAnimMaster")
    val timeFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AtmosphereTime"
    )

    // Lightning Flash effect for storms (Sydney style)
    var showLightningFlash by remember { mutableStateOf(false) }
    var lightningAlpha by remember { mutableFloatStateOf(0f) }
    var lightningBranchPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    if (condLower.contains("storm")) {
        // Trigger realistic lightning double-flashes
        LaunchedEffect(Unit) {
            val rand = java.util.Random()
            while (true) {
                // Wait between 4 and 9 seconds
                kotlinx.coroutines.delay(4000L + rand.nextInt(5000))
                
                // Construct a randomized jagged lightning bolt
                val branches = mutableListOf<Offset>()
                var currentX = 0.2f + rand.nextFloat() * 0.6f // randomized horizontal start
                var currentY = 0.05f
                branches.add(Offset(currentX, currentY))
                
                while (currentY < 0.85f) {
                    currentY += 0.05f + rand.nextFloat() * 0.1f
                    currentX += (rand.nextFloat() - 0.5f) * 0.15f
                    branches.add(Offset(currentX.coerceIn(0.05f, 0.95f), currentY))
                }
                lightningBranchPoints = branches
                
                // First flash
                showLightningFlash = true
                lightningAlpha = 0.4f
                kotlinx.coroutines.delay(70L)
                lightningAlpha = 0f
                kotlinx.coroutines.delay(80L)
                // Second rapid flash
                lightningAlpha = 0.6f
                kotlinx.coroutines.delay(120L)
                lightningAlpha = 0f
                showLightningFlash = false
            }
        }
    }

    when {
        condLower.contains("storm") -> {
            // Storm (Sydney style): Dark clouds with glowing lightning flashes and bolts
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rand = java.util.Random(42L)
                
                // Draw multiple layers of huge, rolling ominous clouds at the top
                repeat(4) { i ->
                    val driftOffset = sin(timeFactor * 0.04f + i) * 60f
                    val sizeFactor = 350f + rand.nextFloat() * 100f
                    val centerX = (size.width * (i * 0.25f + 0.1f)) + driftOffset
                    drawCircle(
                        color = Color(0x3B0C0E14),
                        radius = sizeFactor,
                        center = Offset(centerX, size.height * 0.1f + (rand.nextFloat() * 50f))
                    )
                }
                
                // Draw heavy vertical rain strands
                repeat(25) { i ->
                    val rx = (rand.nextFloat() * size.width + timeFactor * 10f) % size.width
                    val ry = (rand.nextFloat() * size.height + timeFactor * 800f) % size.height
                    drawLine(
                        color = Color(0x3A60A5FA),
                        start = Offset(rx, ry),
                        end = Offset(rx - 8f, ry + 40f),
                        strokeWidth = 2.5f
                    )
                }

                // If lightning is triggered, perform the electrical blast and draw the lightning bolt paths
                if (showLightningFlash && lightningAlpha > 0f && lightningBranchPoints.size > 1) {
                    // Sky glow overlay
                    drawRect(
                        color = Color(0xFFE0F2FE).copy(alpha = lightningAlpha * 0.4f),
                        size = size
                    )
                    
                    // Jagged lightning stroke paths
                    val boltPath = androidx.compose.ui.graphics.Path()
                    boltPath.moveTo(
                        lightningBranchPoints[0].x * size.width,
                        lightningBranchPoints[0].y * size.height
                    )
                    for (k in 1 until lightningBranchPoints.size) {
                        boltPath.lineTo(
                            lightningBranchPoints[k].x * size.width,
                            lightningBranchPoints[k].y * size.height
                        )
                    }
                    
                    // Draw outer neon glow (larger width, transparent)
                    drawPath(
                        path = boltPath,
                        color = Color(0xFF38BDF8).copy(alpha = lightningAlpha * 0.8f),
                        style = Stroke(width = 16f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                    // Draw inner bright core (white/sky-blue)
                    drawPath(
                        path = boltPath,
                        color = Color.White.copy(alpha = lightningAlpha),
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                }
            }
        }
        condLower.contains("rain") || condLower.contains("shower") || condLower.contains("drizzle") -> {
            // Tokyo low rain: Glass Water Droplets sliding down + background falling rain
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rand = java.util.Random(101L)
                val width = size.width
                val height = size.height

                // Draw falling rain streaks in background to give depth
                repeat(20) { i ->
                    val rx = (rand.nextFloat() * width + timeFactor * 4f) % width
                    val ry = (rand.nextFloat() * height + timeFactor * 500f) % height
                    drawLine(
                        color = Color(0x247491FF),
                        start = Offset(rx, ry),
                        end = Offset(rx - 3f, ry + 30f),
                        strokeWidth = 2f
                    )
                }

                // Draw tiny physical static condensation water beads on glass first (Reference Image feel)
                repeat(40) { i ->
                    val bx = rand.nextFloat() * width
                    val by = rand.nextFloat() * height
                    val bradius = 2f + rand.nextFloat() * 2f
                    // Draw droplet highlight and shadow to give 3D bubble refraction
                    drawCircle(
                        color = Color(0x22FFFFFF),
                        radius = bradius,
                        center = Offset(bx, by)
                    )
                    drawCircle(
                        color = Color(0x35FFFFFF),
                        radius = bradius * 0.7f,
                        center = Offset(bx - 0.5f, by - 0.5f)
                    )
                }

                // Draw 12 gorgeous sliding 3D water droplets running down glass with trails
                repeat(12) { i ->
                    val slideSpeed = 80f + (i % 3) * 40f
                    val delayVal = i * 200f
                    val rx = (i * 0.08f + 0.06f) * width
                    // Sliding vertical translation with modular wrap-around
                    val ry = ((timeFactor * slideSpeed) + delayVal) % height
                    
                    val dropletWidth = 6.dp.toPx()
                    
                    // Draw simple fading water trail behind the slider
                    drawLine(
                        color = Color(0x18FFFFFF),
                        start = Offset(rx, (ry - 40f).coerceAtLeast(0f)),
                        end = Offset(rx, ry),
                        strokeWidth = 4f
                    )

                    // Draw 3D teardrop shape
                    // Teardrop body
                    drawCircle(
                        color = Color(0x1EFFFFFF),
                        radius = dropletWidth * 0.8f,
                        center = Offset(rx, ry)
                    )
                    // Draw outline
                    drawCircle(
                        color = Color(0x2C000000),
                        radius = dropletWidth * 0.8f,
                        center = Offset(rx + 1f, ry + 1f),
                        style = Stroke(width = 1.2f)
                    )
                    // High glare white reflections (top left)
                    drawCircle(
                        color = Color(0x88FFFFFF),
                        radius = dropletWidth * 0.35f,
                        center = Offset(rx - 1.2f, ry - 1.5f)
                    )
                }
            }
        }
        condLower.contains("snow") -> {
            // New York Rain-Snow: Floating layered snow flakes with wind sway (Reference Image feel)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rand = java.util.Random(2026L)
                val width = size.width
                val height = size.height

                // Render 45 distinct layered snowflakes
                repeat(45) { i ->
                    val flakeInitialX = (i * 0.022f) * width
                    val flakeYOffset = (i * 50f)
                    val fallSpeed = 60f + (i % 4) * 20f
                    
                    // Vertical position
                    val ry = (timeFactor * fallSpeed + flakeYOffset) % height
                    
                    // Gentle wind sway using sine/cosine curves
                    val xSway = sin(timeFactor * 0.05f + i) * 25f
                    val rx = (flakeInitialX + xSway) % width

                    val sizeRadius = 2.5f + (i % 5) * 1.5f // size variation
                    val alpha = if (sizeRadius > 5f) 0.85f else 0.45f // large flakes are in foreground and brighter

                    // Draw glowing snow flake
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = sizeRadius,
                        center = Offset(rx, ry)
                    )
                    // Draw outer subtle blur aura for large flakes
                    if (sizeRadius > 5f) {
                        drawCircle(
                            color = Color.White.copy(alpha = alpha * 0.3f),
                            radius = sizeRadius * 1.8f,
                            center = Offset(rx, ry)
                        )
                    }
                }
            }
        }
        condLower.contains("cloud") || condLower.contains("overcast") -> {
            // Premium drifting cloud cards
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rand = java.util.Random(99L)
                repeat(4) { i ->
                    val cloudCenterX = (size.width * (i * 0.28f + 0.05f) + timeFactor * 2f) % (size.width + 400f) - 200f
                    val cloudCenterY = size.height * (0.15f + (i % 3) * 0.07f)
                    val cloudRad = 160f + (i % 3) * 60f
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = cloudRad,
                        center = Offset(cloudCenterX, cloudCenterY)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = cloudRad * 1.4f,
                        center = Offset(cloudCenterX + 50f, cloudCenterY + 20f)
                    )
                }
            }
        }
        else -> {
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val isNight = currentHour >= 18 || currentHour < 6

            if (isNight) {
                // Clear Night sky: Silent beautiful ambient twilight with silver-blue crescent moon + twinkling stars
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw 30 twinkling stars
                    val rand = java.util.Random(777L)
                    repeat(30) { s ->
                        val starX = rand.nextFloat() * size.width
                        val starY = rand.nextFloat() * (size.height * 0.6f)
                        val starAlpha = (kotlin.math.sin(timeFactor * 0.1f + s) + 1f) / 2f
                        val starRadius = 1.5f + rand.nextFloat() * 2f
                        drawCircle(
                            color = Color.White.copy(alpha = starAlpha * 0.8f),
                            radius = starRadius,
                            center = Offset(starX, starY)
                        )
                    }

                    // Draw moon
                    val pulse = kotlin.math.sin(timeFactor * 0.08f) * 15f
                    val moonCenter = Offset(size.width * 0.85f, size.height * 0.12f)
                    val baseRadius = 200f + pulse

                    // Night/Moon silver ambient aura glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF82B1FF).copy(0.3f), Color(0x11B3E5FC), Color.Transparent),
                            center = moonCenter,
                            radius = baseRadius * 2.0f
                        ),
                        radius = baseRadius * 2.0f,
                        center = moonCenter
                    )

                    // Draw crescent moon using path operations
                    val moonPath = androidx.compose.ui.graphics.Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(
                            moonCenter.x - baseRadius * 0.6f,
                            moonCenter.y - baseRadius * 0.6f,
                            moonCenter.x + baseRadius * 0.6f,
                            moonCenter.y + baseRadius * 0.6f
                        ))
                    }
                    val maskOffset = Offset(baseRadius * 0.25f, -baseRadius * 0.12f)
                    val maskPath = androidx.compose.ui.graphics.Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(
                            moonCenter.x - baseRadius * 0.6f + maskOffset.x,
                            moonCenter.y - baseRadius * 0.6f + maskOffset.y,
                            moonCenter.x + baseRadius * 0.6f + maskOffset.x,
                            moonCenter.y + baseRadius * 0.6f + maskOffset.y
                        ))
                    }
                    val crescentPath = androidx.compose.ui.graphics.Path.combine(
                        androidx.compose.ui.graphics.PathOperation.Difference,
                        moonPath,
                        maskPath
                    )

                    drawPath(
                        path = crescentPath,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFECEFF1), Color(0xFFCFD8DC), Color(0x80B0BEC5)),
                            start = moonCenter - Offset(baseRadius, baseRadius),
                            end = moonCenter + Offset(baseRadius, baseRadius)
                        )
                    )
                }
            } else {
                // Sunny / Clear: Glowing pulsating solar corona, gold beams rotating slowly
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pulse = kotlin.math.sin(timeFactor * 0.08f) * 15f
                    val sunCenter = Offset(size.width * 0.85f, size.height * 0.12f)
                    val baseRadius = 260f + pulse

                    // Draw grandfather solar aura gradients
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x3FFFB300), Color(0x11FFA000), Color.Transparent),
                            center = sunCenter,
                            radius = baseRadius * 1.6f
                        ),
                        radius = baseRadius * 1.6f,
                        center = sunCenter
                    )

                    // Sunbeams: Draw thin, rotating golden lines projecting outward
                    val beamCount = 6
                    rotate(degrees = timeFactor * 2f, pivot = sunCenter) {
                        repeat(beamCount) { b ->
                            val angle = (b * (180f / beamCount))
                            val rad = Math.toRadians(angle.toDouble()).toFloat()
                            val beamLen = baseRadius * 1.3f
                            drawLine(
                                color = Color(0x16FFA000),
                                start = sunCenter - Offset(cos(rad) * beamLen, sin(rad) * beamLen),
                                end = sunCenter + Offset(cos(rad) * beamLen, sin(rad) * beamLen),
                                strokeWidth = 6f
                            )
                        }
                    }
                }
            }
        }
    }
}

// 1. Current central banner display
@Composable
fun PrimaryWeatherBanner(
    location: SavedLocation?,
    isCelsius: Boolean,
    containerColor: Color = Color(0xFF1F2429),
    borderColor: Color = Color(0xFF2D3135)
) {
    if (location == null) return

    val displayTemp = if (isCelsius) {
        "${location.temperatureC.toInt()}°"
    } else {
        "${(location.temperatureC * 9/5 + 32).toInt()}°"
    }

    val feelsLikeText = if (isCelsius) {
        "${location.feelsLikeC.toInt()}°C"
    } else {
        "${(location.feelsLikeC * 9/5 + 32).toInt()}°F"
    }

    val highText = if (isCelsius) "${location.highestTempC.toInt()}°" else "${(location.highestTempC * 9/5 + 32).toInt()}°"
    val lowText = if (isCelsius) "${location.lowestTempC.toInt()}°" else "${(location.lowestTempC * 9/5 + 32).toInt()}°"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("primary_weather_banner"),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp)
        ) {
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val isNight = currentHour >= 18 || currentHour < 6
            val displayCondition = getDisplayCondition(location.condition)

            val icon = when {
                displayCondition.contains("Sunny", ignoreCase = true) || displayCondition.contains("Clear", ignoreCase = true) || displayCondition.contains("Heat", ignoreCase = true) || displayCondition.contains("Night", ignoreCase = true) -> {
                    if (isNight) Icons.Filled.Bedtime else Icons.Filled.WbSunny
                }
                displayCondition.contains("Rain", ignoreCase = true) || displayCondition.contains("Shower", ignoreCase = true) || displayCondition.contains("Drizzle", ignoreCase = true) -> Icons.Filled.BeachAccess
                displayCondition.contains("Storm", ignoreCase = true) || displayCondition.contains("Thunder", ignoreCase = true) -> Icons.Filled.Thunderstorm
                displayCondition.contains("Snow", ignoreCase = true) -> Icons.Filled.AcUnit
                else -> Icons.Filled.Cloud
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (displayCondition.contains("Sunny", ignoreCase = true) || displayCondition.contains("Clear", ignoreCase = true) || displayCondition.contains("Heat", ignoreCase = true) || displayCondition.contains("Night", ignoreCase = true)) {
                        if (isNight) Color(0xFFECEFF1) else Color(0xFFFFB800)
                    } else {
                        Color(0xFF7491FF)
                    },
                    modifier = Modifier.size(52.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayTemp,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-4).sp
                    ),
                    color = Color(0xFFE2E2E6)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = displayCondition.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        fontSize = 15.sp
                    ),
                    color = Color(0xFFC4C6CF)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "H: $highText",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF7491FF)
                    )
                    Text(
                        text = "L: $lowText",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF8C9199)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "FEELS LIKE: $feelsLikeText",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                    color = Color(0xFF00FF66)
                )
            }
        }
    }
}

// 2. Intelligent Briefing Card
@Composable
fun AiBriefingCard(
    briefingText: String,
    isLoading: Boolean,
    onAiClick: () -> Unit,
    containerColor: Color = Color(0xFF161B1F),
    borderColor: Color = Color(0xFF2D3135)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onAiClick() }
            .testTag("ai_briefing_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isLoading) Color(0xFFFFB800) else Color(0xFF4CAF50),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GEMINI METEOROLOGICAL BRIEFING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF7491FF)
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color(0xFF7491FF),
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Smart AI",
                        tint = Color(0xFF7491FF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = briefingText,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = Color(0xFFE2E2E6)
            )
        }
    }
}

// 3. Mini interactive Radar card
@Composable
fun MiniRadarTeaserCard(
    location: SavedLocation?,
    onUnfoldClick: () -> Unit,
    containerColor: Color = Color(0xFF1F2429),
    borderColor: Color = Color(0xFF2D3135)
) {
    val cityName = location?.cityName ?: "London"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onUnfoldClick() }
            .testTag("mini_radar_card"),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            InteractiveRadarView(
                cityName = cityName,
                latitude = location?.latitude ?: 0f,
                longitude = location?.longitude ?: 0f,
                currentTempC = location?.temperatureC ?: 20f,
                currentHumidity = location?.humidityPercent ?: 50,
                isPlaying = true,
                activeLayer = RadarLayer.PRECIPITATION,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xE0161B1F))
                    .padding(vertical = 12.dp, horizontal = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFFF4444), androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE SATELLITE RADAR",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = Color(0xFFE2E2E6)
                            )
                        }
                        Text(
                            text = "Drag grid to pan, pinch to zoom",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8C9199)
                        )
                    }

                    FilledTonalButton(
                        onClick = onUnfoldClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF7491FF),
                            contentColor = Color(0xFF001D4D)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(text = "EXPAND MAP", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Filled.Fullscreen, contentDescription = "Expand", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// Animated layer controls full screen overlay inside single view
@Composable
fun FullRadarOverlayContainer(
    location: SavedLocation?,
    activeLayer: RadarLayer,
    onLayerChange: (RadarLayer) -> Unit,
    isPlaying: Boolean,
    onPlayChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val cityName = location?.cityName ?: "Local Region"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1113))
            .testTag("full_radar_screen")
    ) {
        // Core interactive canvas
        InteractiveRadarView(
            cityName = cityName,
            latitude = location?.latitude ?: 0f,
            longitude = location?.longitude ?: 0f,
            currentTempC = location?.temperatureC ?: 20f,
            currentHumidity = location?.humidityPercent ?: 50,
            activeLayer = activeLayer,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay controller header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xEE0F1113), Color.Transparent)))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "RADAR: ${cityName.uppercase()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                    color = Color(0xFFE2E2E6)
                )
                Text(
                    text = "Live Meteorological Satellite Feed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8C9199)
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1F2429)),
                modifier = Modifier.testTag("radar_close_button")
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Satellite", tint = Color(0xFFE2E2E6))
            }
        }

        // Radar layers toggles and controllers
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF2161B1F)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF2D3135))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Play / Pause bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onPlayChange(!isPlaying) }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF7491FF)
                            )
                        }
                        Text(
                            text = if (isPlaying) "SWEEP ACTIVE (LOOP)" else "FREEZE RADAR FRAME",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE2E2E6)
                        )
                    }

                    Text(
                        text = "LAST UPDATE: LIVE FEED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8C9199)
                    )
                }

                HorizontalDivider(color = Color(0xFF2D3135), modifier = Modifier.padding(vertical = 12.dp))

                // Layers tabs selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val steps = listOf(
                        Triple(RadarLayer.PRECIPITATION, "PRECIP DBZ", Icons.Filled.Grain),
                        Triple(RadarLayer.TEMPERATURE, "THERMAL MAP", Icons.Filled.FilterDrama),
                        Triple(RadarLayer.WIND, "WIND FLOW", Icons.Filled.Air)
                    )

                    steps.forEach { step ->
                        val selected = activeLayer == step.first
                        FilledTonalButton(
                            onClick = { onLayerChange(step.first) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) Color(0xFF7491FF) else Color(0xFF1F2429),
                                contentColor = if (selected) Color(0xFF001D4D) else Color(0xFFE2E2E6)
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = step.third, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = step.second, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color Spectrum Map Legend indicator
                RadarLegend(
                    activeLayer = activeLayer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// 4. Hourly Forecast Widget Composable
@Composable
fun HourlyWidget(
    hour: HourlyForecast,
    isCelsius: Boolean,
    containerColor: Color = Color(0xFF1F2429),
    borderColor: Color = Color(0xFF2D3135)
) {
    val displayTemp = if (isCelsius) "${hour.tempC}°" else "${(hour.tempC * 9/5 + 32).toInt()}°"

    val isImgNight = run {
        val clean = hour.hourString.uppercase().trim()
        if (clean.contains("PM")) {
            val num = clean.replace("PM", "").trim().toIntOrNull() ?: 12
            num >= 6 && num != 12
        } else if (clean.contains("AM")) {
            val num = clean.replace("AM", "").trim().toIntOrNull() ?: 12
            num < 6 || num == 12
        } else {
            false
        }
    }

    val icon = when {
        hour.condition.contains("Sunny", ignoreCase = true) || hour.condition.contains("Clear", ignoreCase = true) || hour.condition.contains("Heat", ignoreCase = true) -> {
            if (isImgNight) Icons.Filled.Bedtime else Icons.Filled.WbSunny
        }
        hour.condition.contains("Rain", ignoreCase = true) -> Icons.Filled.Grain
        hour.condition.contains("Cloudy", ignoreCase = true) -> Icons.Filled.Cloud
        else -> Icons.Filled.WbCloudy
    }

    Card(
        modifier = Modifier
            .width(80.dp)
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = hour.hourString,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF8C9199)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (hour.condition.contains("Sunny", ignoreCase = true) || hour.condition.contains("Clear", ignoreCase = true) || hour.condition.contains("Heat", ignoreCase = true)) (if (isImgNight) Color(0xFF90A4AE) else Color(0xFFFFB800)) else Color(0xFF7491FF),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = displayTemp,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFE2E2E6)
            )

            // Percent of Rain indicators
            if (hour.rainChance > 20) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.BeachAccess,
                        contentDescription = "rain",
                        tint = Color(0xFF7491FF),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${hour.rainChance}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7491FF)
                    )
                }
            }
        }
    }
}

// 5. Gauges Metric Grid widgets
@Composable
fun WeatherMetricsGrid(
    details: FullWeatherDetails,
    containerColor: Color = Color(0xFF161B1F),
    borderColor: Color = Color(0xFF2D3135),
    selectedLanguage: String = "English (US)"
) {
    val loc = details.location

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = getTranslatedLabel("Atmospheric Details", selectedLanguage).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
            color = Color(0xFF7491FF),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // A. Humidity Card
                MetricArcGaugeCard(
                    title = getTranslatedLabel("Humidity", selectedLanguage).uppercase(),
                    value = "${loc.humidityPercent}%",
                    subtext = "Dew point index is active",
                    progress = loc.humidityPercent / 100f,
                    icon = Icons.Outlined.Opacity,
                    tint = Color(0xFF00B0FF),
                    containerColor = containerColor,
                    borderColor = borderColor
                )

                // B. UV Index Gauge
                MetricScaleGaugeCard(
                    title = getTranslatedLabel("UV Index", selectedLanguage).uppercase(),
                    value = "${loc.uvIndex}",
                    message = details.uvDescription,
                    fraction = loc.uvIndex / 12f,
                    icon = Icons.Outlined.WbSunny,
                    tint = Color(0xFFFF9100),
                    containerColor = containerColor,
                    borderColor = borderColor
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // C. Wind Vector Card
                WindVectorCard(
                    windSpeed = loc.windKmh,
                    directionDeg = loc.windDirDegrees,
                    containerColor = containerColor,
                    borderColor = borderColor
                )

                // D. Barometer pressure & AQI combo Card
                AtmosphericPressureCard(
                    pressure = loc.pressureHpa,
                    aqi = loc.aqi,
                    aqiStatus = details.aqiStatus,
                    containerColor = containerColor,
                    borderColor = borderColor
                )
            }
        }
    }
}

// Gauge A: Humidity Card with Circular Progress Drawing
@Composable
fun MetricArcGaugeCard(
    title: String,
    value: String,
    subtext: String,
    progress: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    containerColor: Color = Color(0xFF161B1F),
    borderColor: Color = Color(0xFF2D3135)
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 10.sp, color = Color(0xFF8C9199), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF7491FF), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Background circle arc
                        drawArc(
                            color = Color.White.copy(alpha = 0.05f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Active colored progress arc
                        drawArc(
                            color = Color(0xFF7491FF),
                            startAngle = 135f,
                            sweepAngle = 270f * progress,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(text = value, fontSize = 14.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.ExtraBold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = subtext,
                    fontSize = 11.sp,
                    color = Color(0xFFC4C6CF),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Gauge B: UV Slider Card
@Composable
fun MetricScaleGaugeCard(
    title: String,
    value: String,
    message: String,
    fraction: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    containerColor: Color = Color(0xFF161B1F),
    borderColor: Color = Color(0xFF2D3135)
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 10.sp, color = Color(0xFF8C9199), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF7491FF), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = value, fontSize = 24.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.ExtraBold)

            Spacer(modifier = Modifier.height(10.dp))

            // slider track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(Color(0xFF7491FF), RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = message, fontSize = 10.sp, color = Color(0xFF8C9199))
        }
    }
}

// Gauge C: Wind Compass vector pointer card
@Composable
fun WindVectorCard(
    windSpeed: Float,
    directionDeg: Int,
    containerColor: Color = Color(0xFF161B1F),
    borderColor: Color = Color(0xFF2D3135)
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "WIND SPEED", fontSize = 10.sp, color = Color(0xFF8C9199), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Icon(imageVector = Icons.Outlined.Air, contentDescription = null, tint = Color(0xFF7491FF), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Drawing dynamic compass container pointing direction
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color.White.copy(alpha = 0.03f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        // Arrow vector pointing degree direction
                        val rad = Math.toRadians((directionDeg - 90).toDouble())
                        val endX = center.x + cos(rad).toFloat() * 18f
                        val endY = center.y + sin(rad).toFloat() * 18f

                        drawLine(
                            color = Color(0xFF7491FF),
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(color = Color(0xFF0F1113), radius = 4f, center = center)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(text = "${windSpeed.toInt()} km/h", fontSize = 14.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.ExtraBold)
                    Text(text = "${directionDeg}° Compass", fontSize = 10.sp, color = Color(0xFF8C9199))
                }
            }
        }
    }
}

// Gauge D: Barometer Pressure Gauge
@Composable
fun AtmosphericPressureCard(
    pressure: Int,
    aqi: Int,
    aqiStatus: String,
    containerColor: Color = Color(0xFF161B1F),
    borderColor: Color = Color(0xFF2D3135)
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "BAROMETRY", fontSize = 10.sp, color = Color(0xFF8C9199), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Icon(imageVector = Icons.Outlined.FilterHdr, contentDescription = null, tint = Color(0xFF7491FF), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = "$pressure hPa", fontSize = 18.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.ExtraBold)
            Text(text = "Stable atmospheric layer", fontSize = 10.sp, color = Color(0xFF8C9199))

            Spacer(modifier = Modifier.height(10.dp))

            // Embedded aqi marker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "AIR QUALITY: $aqi", fontSize = 9.sp, color = Color(0xFFC4C6CF), fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .background(
                            color = when (aqiStatus) {
                                "EXCELLENT" -> Color(0xFF4CAF50)
                                "MODERATE" -> Color(0xFFFFB800)
                                else -> Color(0xFFFF4444)
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = aqiStatus, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F1113))
                }
            }
        }
    }
}

// 6. Weekly Forecast Lists Composable
@Composable
fun WeeklyForecastContainer(
    details: FullWeatherDetails,
    isCelsius: Boolean,
    containerColor: Color = Color(0xFF1F2429),
    borderColor: Color = Color(0xFF2D3135)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "7-DAY SATELLITE FORECAST",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                color = Color(0xFF7491FF),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                details.dailyList.forEach { forecast ->
                    DailyForecastRow(forecast = forecast, isCelsius = isCelsius)
                }
            }
        }
    }
}

@Composable
fun DailyForecastRow(forecast: DailyForecast, isCelsius: Boolean) {
    val displayHigh = if (isCelsius) "${forecast.highC}°" else "${(forecast.highC * 9/5 + 32).toInt()}°"
    val displayLow = if (isCelsius) "${forecast.lowC}°" else "${(forecast.lowC * 9/5 + 32).toInt()}°"

    val icon = when {
        forecast.condition.contains("Heavy Rain", ignoreCase = true) -> Icons.Filled.Cloud
        forecast.condition.contains("Rain", ignoreCase = true) || forecast.condition.contains("Shower", ignoreCase = true) || forecast.condition.contains("Drizzle", ignoreCase = true) -> Icons.Filled.BeachAccess
        forecast.condition.contains("Storm", ignoreCase = true) || forecast.condition.contains("Thunder", ignoreCase = true) -> Icons.Filled.Thunderstorm
        forecast.condition.contains("Cloudy", ignoreCase = true) || forecast.condition.contains("Overcast", ignoreCase = true) -> Icons.Filled.WbCloudy
        forecast.condition.contains("Snow", ignoreCase = true) -> Icons.Filled.AcUnit
        else -> Icons.Filled.WbSunny
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Weekday Name
        Text(
            text = forecast.dayString,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFFE2E2E6),
            modifier = Modifier.width(90.dp)
        )

        // Weather icon & precipitation percentage
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (forecast.condition.contains("Sunny", ignoreCase = true) || forecast.condition.contains("Heat", ignoreCase = true) || forecast.condition.contains("Clear", ignoreCase = true)) Color(0xFFFFB800) else Color(0xFF7491FF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (forecast.rainChance > 25) {
                Text(
                    text = "${forecast.rainChance}% " + forecast.condition.lowercase(Locale.ROOT),
                    fontSize = 11.sp,
                    color = Color(0xFF7491FF),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = forecast.condition.lowercase(Locale.ROOT),
                    fontSize = 11.sp,
                    color = Color(0xFF8C9199)
                )
            }
        }

        // Low / High bar
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayLow,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8C9199),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Premium visual mini slider indicator
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = 10.dp)
                        .width(24.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF7491FF), RoundedCornerShape(2.dp))
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = displayHigh,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFE2E2E6),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

// City Search management dialog overlay with slide animations
@Composable
fun CitySelectionOverlay(
    savedLocations: List<SavedLocation>,
    activeCity: SavedLocation?,
    onClose: () -> Unit,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit,
    onAddCity: (String) -> Boolean,
    onSetPrimary: (String) -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE0F1113))
            .clickable { onClose() }
            .testTag("location_search_panel"),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(enabled = false) {}, // prevent click-through closes
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B1F)),
            border = BorderStroke(1.dp, Color(0xFF2D3135))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "METEOR REGIONAL SELECTOR",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = Color(0xFF7491FF)
                    )

                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Selector", tint = Color(0xFFE2E2E6))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input box
                OutlinedTextField(
                    value = queryText,
                    onValueChange = {
                        queryText = it
                        searchError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("city_search_input"),
                    placeholder = { Text(text = "Search global sectors (e.g. Tokyo)...", color = Color(0xFF8C9199)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE2E2E6),
                        unfocusedTextColor = Color(0xFFE2E2E6),
                        focusedBorderColor = Color(0xFF7491FF),
                        unfocusedBorderColor = Color(0xFF2D3135),
                        cursorColor = Color(0xFF7491FF)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (queryText.isNotBlank()) {
                                    val success = onAddCity(queryText)
                                    if (!success) {
                                        searchError = "Aero Sector match not found. Try Tokyo, New York, Reykjavík, Sydney."
                                    } else {
                                        queryText = ""
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = "Search icon", tint = Color(0xFF7491FF))
                        }
                    }
                )

                if (searchError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = searchError!!, color = Color(0xFFFF4444), fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "MONITORED REGION SAVES",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = Color(0xFF8C9199),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.maxHeight(300.dp)
                ) {
                    items(savedLocations) { location ->
                        val isCurrentSelected = location.cityName == activeCity?.cityName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isCurrentSelected) Color(0xFF1F2429) else Color(0xFF0F1113),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isCurrentSelected) Color(0xFF7491FF) else Color(0xFF2D3135)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelect(location) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = location.cityName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFE2E2E6)
                                    )
                                    if (location.isPrimary) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF7491FF), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = "PRIMARY", fontSize = 7.sp, color = Color(0xFF001D4D), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = "${location.country} • ${location.condition}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8C9199)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${location.temperatureC.toInt()}°C",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFE2E2E6),
                                    modifier = Modifier.padding(end = 12.dp)
                                )

                                if (!location.isPrimary) {
                                    IconButton(
                                        onClick = { onSetPrimary(location.cityName) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.StarBorder,
                                            contentDescription = "Set Primary Icon",
                                            tint = Color(0xFF8C9199),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { onDelete(location) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete Location Icon",
                                        tint = Color(0xFFFF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Compose MaxHeight box constraint helper
@Composable
fun Modifier.maxHeight(max: androidx.compose.ui.unit.Dp): Modifier {
    return this.heightIn(max = max)
}

// 7. Historical Sector Record Component
@Composable
fun HistoricalWeatherCard(
    viewModel: WeatherViewModel,
    isCelsius: Boolean,
    containerColor: Color = Color(0xFF1F2429),
    borderColor: Color = Color(0xFF2D3135)
) {
    var selectedDays by remember { mutableStateOf(7) }
    val historyData = remember(selectedDays, viewModel.selectedLocation.value) {
        viewModel.getHistoricalData(selectedDays)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("historical_weather_card"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HISTORICAL SECTOR RECORD",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                        color = Color(0xFF7491FF)
                    )
                    Text(
                        text = "Atmospheric trends for chosen epoch",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8C9199)
                    )
                }

                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "History Icon",
                    tint = Color(0xFF7491FF),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Range buttons (7, 14, 30 days)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair(7, "7 DAYS"),
                    Pair(14, "14 DAYS"),
                    Pair(30, "30 DAYS")
                ).forEach { option ->
                    val isSelected = selectedDays == option.first
                    FilledTonalButton(
                        onClick = { selectedDays = option.first },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("history_range_${option.first}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSelected) Color(0xFF7491FF) else Color(0xFF161B1F),
                            contentColor = if (isSelected) Color(0xFF001D4D) else Color(0xFFE2E2E6)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = option.second, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (historyData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))

                // Summary Dashboard Header
                val avgTemp = historyData.map { it.avgTempC }.average().toFloat()
                val avgPrecip = historyData.map { it.rainChancePercent }.average().toInt()
                val peakWind = historyData.maxOfOrNull { it.windKmh } ?: 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B1F), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "HISTORIC AVG", fontSize = 8.sp, color = Color(0xFF8C9199), fontWeight = FontWeight.Bold)
                        val dispAvg = if (isCelsius) "${avgTemp.toInt()}°C" else "${(avgTemp * 9/5 + 32).toInt()}°F"
                        Text(text = dispAvg, fontSize = 14.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF2D3135)).align(Alignment.CenterVertically))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "RAIN CHANCE", fontSize = 8.sp, color = Color(0xFF8C9199), fontWeight = FontWeight.Bold)
                        Text(text = "$avgPrecip%", fontSize = 14.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF2D3135)).align(Alignment.CenterVertically))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "PEAK WIND", fontSize = 8.sp, color = Color(0xFF8C9199), fontWeight = FontWeight.Bold)
                        Text(text = "${peakWind.toInt()} km/h", fontSize = 14.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas line graph
                Text(
                    text = "TEMPERATURE WAVEFORM (°C)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color(0xFF8C9199),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .background(Color(0xFF161B1F), RoundedCornerShape(20.dp))
                        .padding(top = 16.dp, bottom = 10.dp, start = 14.dp, end = 14.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val points = historyData.map { it.avgTempC }
                        val minVal = points.minOrNull() ?: 0f
                        val maxVal = points.maxOrNull() ?: 100f
                        val deltaVal = if (maxVal == minVal) 1f else (maxVal - minVal)

                        val widthStep = size.width / (points.size - 1).coerceAtLeast(1)
                        val pathPoints = points.mapIndexed { idx, temp ->
                            val x = idx * widthStep
                            val fraction = (temp - minVal) / deltaVal
                            val y = size.height - (fraction * size.height)
                            Offset(x, y)
                        }

                        // Grid lines
                        val horizontalLines = 3
                        for (i in 0..horizontalLines) {
                            val gridY = (size.height / horizontalLines) * i
                            drawLine(
                                color = Color(0x1A7491FF),
                                start = Offset(0f, gridY),
                                end = Offset(size.width, gridY),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Gradient fill
                        val filledPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height)
                            pathPoints.forEach { point ->
                                lineTo(point.x, point.y)
                            }
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(
                            path = filledPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0x337491FF), Color.Transparent)
                            )
                        )

                        // Draw lines
                        for (i in 0 until pathPoints.size - 1) {
                            drawLine(
                                color = Color(0xFF7491FF),
                                start = pathPoints[i],
                                end = pathPoints[i + 1],
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Draw circles over points
                        pathPoints.forEach { point ->
                            drawCircle(
                                color = Color(0xFF7491FF),
                                radius = 3.dp.toPx(),
                                center = point
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Log details table
                Text(
                    text = "HISTORICAL UTILITY LOGS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color(0xFF8C9199),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    historyData.take(5).forEach { record ->
                        HistoricalRowItem(record = record, isCelsius = isCelsius)
                    }
                    if (historyData.size > 5) {
                        Text(
                            text = "+ ${historyData.size - 5} more historical records simulated",
                            fontSize = 10.sp,
                            color = Color(0xFF7491FF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricalRowItem(record: HistoricalRecord, isCelsius: Boolean) {
    val tempDisp = if (isCelsius) "${record.avgTempC.toInt()}°C" else "${(record.avgTempC * 9/5 + 32).toInt()}°F"
    val highDisp = if (isCelsius) "${record.maxTempC.toInt()}°" else "${(record.maxTempC * 9/5 + 32).toInt()}°"
    val lowDisp = if (isCelsius) "${record.minTempC.toInt()}°" else "${(record.minTempC * 9/5 + 32).toInt()}°"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B1F), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = record.dateString, fontSize = 12.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.Bold)
            Text(text = record.condition, fontSize = 10.sp, color = Color(0xFF8C9199))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Precipitation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Grain, contentDescription = null, tint = Color(0xFF7491FF), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = "${record.rainChancePercent}%", fontSize = 10.sp, color = Color(0xFFE2E2E6))
            }

            // Wind Speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Air, contentDescription = null, tint = Color(0xFF8C9199), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = "${record.windKmh.toInt()} km", fontSize = 10.sp, color = Color(0xFF8C9199))
            }

            // Temperature readings
            Column(horizontalAlignment = Alignment.End) {
                Text(text = tempDisp, fontSize = 12.sp, color = Color(0xFFE2E2E6), fontWeight = FontWeight.ExtraBold)
                Text(text = "$lowDisp / $highDisp", fontSize = 9.sp, color = Color(0xFF8C9199))
            }
        }
    }
}

// 8. Custom Weather Alerts Component
@Composable
fun WeatherAlertsCard(
    viewModel: WeatherViewModel,
    containerColor: Color = Color(0xFF1F2429),
    borderColor: Color = Color(0xFF2D3135)
) {
    val alerts by viewModel.cityAlerts.collectAsStateWithLifecycle()
    var isAddingAlert by remember { mutableStateOf(false) }

    var alertType by remember { mutableStateOf("TEMP_HIGH") }
    var thresholdValue by remember { mutableStateOf(30f) }

    val alertTypes = listOf(
        Triple("TEMP_HIGH", "HEAT", Icons.Filled.WbSunny),
        Triple("TEMP_LOW", "FROST", Icons.Filled.AcUnit),
        Triple("HIGH_WINDS", "WIND", Icons.Filled.Air),
        Triple("SNOW", "SNOW", Icons.Filled.FilterDrama),
        Triple("THUNDERSTORM", "STORM", Icons.Filled.Thunderstorm)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("weather_alerts_card"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "REGIONAL WEATHER ALERTS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                        color = Color(0xFF7491FF)
                    )
                    Text(
                        text = "Configure user notice-points & warnings",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8C9199)
                    )
                }

                IconButton(
                    onClick = { isAddingAlert = !isAddingAlert },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isAddingAlert) Color(0xFF7491FF) else Color(0xFF161B1F)
                    ),
                    modifier = Modifier.size(36.dp).testTag("toggle_add_alert_btn")
                ) {
                    Icon(
                        imageVector = if (isAddingAlert) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = "Add alert rule icon",
                        tint = if (isAddingAlert) Color(0xFF001D4D) else Color(0xFF7491FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Expandable configuration form
            AnimatedVisibility(
                visible = isAddingAlert,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B1F), RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "CHOOSE SATELLITE FOCUS RULE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8C9199),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(alertTypes) { type ->
                            val isSelected = alertType == type.first
                            FilledTonalButton(
                                onClick = {
                                    alertType = type.first
                                    thresholdValue = when (type.first) {
                                        "TEMP_HIGH" -> 32f
                                        "TEMP_LOW" -> 4f
                                        "HIGH_WINDS" -> 25f
                                        else -> 0f
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSelected) Color(0xFF7491FF) else Color(0xFF1F2429),
                                    contentColor = if (isSelected) Color(0xFF001D4D) else Color(0xFFE2E2E6)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("alert_type_select_${type.first}")
                            ) {
                                Icon(imageVector = type.third, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = type.second, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Slider (Only shown if metric rules TEMP_HIGH, TEMP_LOW, or HIGH_WINDS apply)
                    if (alertType == "TEMP_HIGH" || alertType == "TEMP_LOW" || alertType == "HIGH_WINDS") {
                        val minVal = if (alertType == "HIGH_WINDS") 5f else -10f
                        val maxVal = if (alertType == "HIGH_WINDS") 80f else 48f
                        val suffix = if (alertType == "HIGH_WINDS") "km/h" else "°C"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "THRESHOLD TRIGGER POINT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8C9199),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${thresholdValue.toInt()} $suffix",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7491FF)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = thresholdValue,
                            onValueChange = { thresholdValue = it },
                            valueRange = minVal..maxVal,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF7491FF),
                                activeTrackColor = Color(0xFF7491FF),
                                inactiveTrackColor = Color(0xFF1F2429)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("alert_threshold_slider")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        // Event boundaries description
                        Text(
                            text = "AUTOMATIC SATELLITE TRIGGER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7491FF)
                        )
                        Text(
                            text = "Triggers systemic warnings immediately on radar-detected snowfall or severe lightning systems.",
                            fontSize = 10.sp,
                            color = Color(0xFF8C9199)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.createAlert(alertType, thresholdValue)
                            isAddingAlert = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("save_alert_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF7491FF),
                            contentColor = Color(0xFF001D4D)
                        )
                    ) {
                        Text(text = "ACTIVATE WEATHER WARNING LIMIT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            if (isAddingAlert) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Alerts lists
            if (alerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B1F), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = "No Alerts Indicator",
                            tint = Color(0xFF8C9199),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No custom warning triggers currently active.",
                            fontSize = 12.sp,
                            color = Color(0xFF8C9199),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap the '+' operator above to arm indicators.",
                            fontSize = 10.sp,
                            color = Color(0xFF8C9199)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.forEach { alert ->
                        AlertRuleRowItem(
                            alert = alert,
                            onToggle = { viewModel.toggleAlertEnabled(alert) },
                            onDelete = { viewModel.deleteAlert(alert) },
                            onSimulateTest = { viewModel.simulateTriggerTestNotification(alert) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertRuleRowItem(
    alert: WeatherAlert,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSimulateTest: () -> Unit
) {
    val meta = when (alert.type) {
        "TEMP_HIGH" -> Triple("THERMAL HEAT ALERT", Icons.Filled.WbSunny, "°C")
        "TEMP_LOW" -> Triple("FROST FREEZE ALERT", Icons.Filled.AcUnit, "°C")
        "HIGH_WINDS" -> Triple("SEVERE WIND ALERT", Icons.Filled.Air, " km/h")
        "SNOW" -> Triple("ARCTIC SNOW ALERT", Icons.Filled.FilterDrama, "")
        else -> Triple("SEVERE SYSTEM STORM ALERT", Icons.Filled.Thunderstorm, "")
    }

    val ruleText = when (alert.type) {
        "TEMP_HIGH" -> "Triggers over ${alert.threshold.toInt()}${meta.third}"
        "TEMP_LOW" -> "Triggers under ${alert.threshold.toInt()}${meta.third}"
        "HIGH_WINDS" -> "Triggers over ${alert.threshold.toInt()}${meta.third}"
        "SNOW" -> "Triggers immediate on snowfall"
        else -> "Triggers immediate on storm"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B1F), RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF1F2429), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = meta.second,
                    contentDescription = null,
                    tint = Color(0xFF7491FF),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = meta.first,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E2E6)
                )
                Text(
                    text = ruleText,
                    fontSize = 10.sp,
                    color = Color(0xFF8C9199)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Simulation check
            IconButton(
                onClick = onSimulateTest,
                modifier = Modifier.size(28.dp).testTag("alert_test_simulate_${alert.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Trigger test warning system",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Power state
            Switch(
                checked = alert.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF7491FF),
                    checkedTrackColor = Color(0x337491FF),
                    uncheckedThumbColor = Color(0xFF8C9199),
                    uncheckedTrackColor = Color(0xFF1F2429)
                ),
                modifier = Modifier.scale(0.7f).testTag("alert_toggle_status_${alert.id}")
            )

            // Delete trigger limits
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp).testTag("alert_delete_btn_${alert.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete warning limit",
                    tint = Color(0xFFFF4444),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ==========================================
// PREMIUM WALLPAPER WIDGET OVERLAY STUDIO
// ==========================================

@Composable
fun WidgetOverlayStudioPage(
    location: SavedLocation?,
    forecastDetails: com.example.ui.FullWeatherDetails?,
    isCelsius: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weather_prefs_v3", android.content.Context.MODE_PRIVATE) }
    var selectedSkin by remember { mutableIntStateOf(prefs.getInt("widget_selected_skin", 0)) }
    var glassOpacity by remember { mutableFloatStateOf(prefs.getFloat("widget_glass_opacity", 0.18f)) }
    var borderGlowEnabled by remember { mutableStateOf(prefs.getBoolean("widget_border_glow", true)) }
    var twinkleStarsEnabled by remember { mutableStateOf(prefs.getBoolean("widget_twinkle_stars", true)) }
    var scaleFactor by remember { mutableFloatStateOf(0.95f) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("premium_widgets_studio_page"),
        color = Color(0xFF0C1015) // Clean premium dark slate backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Screen app bar with comfortable padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F2429))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Page",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "WIDGET OVERLAY STUDIO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Zero-delay lag-free glassmorphic designs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E95A2)
                    )
                }
            }

            // Scrollable content area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tap any design skin below to configure beautiful, transparent weather overlay templates. Syncs 100% dynamically with your geolocated sensors.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFADB5C2),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic Pill design tabs
            ScrollableTabRow(
                selectedTabIndex = selectedSkin,
                containerColor = Color(0xFF1A1F26),
                contentColor = Color(0xFFFF9E00),
                edgePadding = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                val skins = listOf("Calm Twilight", "Compact Pill", "Pro Wide-Deck", "Space Slate")
                skins.forEachIndexed { idx, sName ->
                    Tab(
                        selected = selectedSkin == idx,
                        onClick = { selectedSkin = idx },
                        text = {
                            Text(
                                text = sName,
                                fontSize = 12.sp,
                                fontWeight = if (selectedSkin == idx) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Aesthetic Phone Wallpaper Simulation Backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .drawBehind {
                        // High-end wallpaper simulation: deep nocturnal space gradient
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF0C091A), Color(0xFF1A142D), Color(0xFF090A0D))
                            ),
                            size = size
                        )
                        // Neon radial nebula horizon
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF8338EC).copy(0.24f), Color.Transparent),
                                radius = size.minDimension * 0.9f
                            ),
                            center = Offset(size.width * 0.35f, size.height * 0.45f),
                            radius = size.minDimension * 0.9f
                        )
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Twinkling stars drawing layer
                if (twinkleStarsEnabled) {
                    WidgetSparkleCanvas(modifier = Modifier.fillMaxSize())
                }

                if (location != null) {
                    Box(modifier = Modifier.scale(scaleFactor)) {
                        when (selectedSkin) {
                            0 -> CalmTwilightWidgetSkin(
                                location = location,
                                isCelsius = isCelsius,
                                opacity = glassOpacity,
                                glow = borderGlowEnabled
                            )
                            1 -> CompactPillWidgetSkin(
                                location = location,
                                isCelsius = isCelsius,
                                opacity = glassOpacity,
                                glow = borderGlowEnabled
                            )
                            2 -> ProWideDeckWidgetSkin(
                                location = location,
                                forecastDetails = forecastDetails,
                                isCelsius = isCelsius,
                                opacity = glassOpacity,
                                glow = borderGlowEnabled
                            )
                            3 -> SpaceSlateWidgetSkin(
                                location = location,
                                isCelsius = isCelsius,
                                opacity = glassOpacity,
                                glow = borderGlowEnabled
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Acquiring live GPS metrics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Studio Dial / Sliders section
            Text(
                text = "SATELLITE GLASS PREFERENCES",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = Color(0xFFFF9E00)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Opacity Config slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Glass Transparency Level",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFADB5C2)
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%% Translucent", glassOpacity * 100),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF9E00)
                    )
                }
                Slider(
                    value = glassOpacity,
                    onValueChange = { glassOpacity = it },
                    valueRange = 0.08f..0.45f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF9E00),
                        activeTrackColor = Color(0xFFFF9E00).copy(0.4f),
                        inactiveTrackColor = Color(0xFF222831)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Toggle selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFFF9E00),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Cosmic Diamond Star Sparks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                Switch(
                    checked = twinkleStarsEnabled,
                    onCheckedChange = { twinkleStarsEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF9E00),
                        checkedTrackColor = Color(0x33FF9E00),
                        uncheckedThumbColor = Color(0xFF8C9199),
                        uncheckedTrackColor = Color(0xFF1F2429)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color(0xFFFF9E00),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "High-Chrome Translucent Stroke",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                Switch(
                    checked = borderGlowEnabled,
                    onCheckedChange = { borderGlowEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF9E00),
                        checkedTrackColor = Color(0x33FF9E00),
                        uncheckedThumbColor = Color(0xFF8C9199),
                        uncheckedTrackColor = Color(0xFF1F2429)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Premium Section: 1-Click launcher instantiator
            Text(
                text = "DIRECT 1-CLICK LAUNCHER PINNING",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFFFF9E00)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap any size format below to programmatically add and pair beautiful glass overlays directly to your home screen launcher in 1-Click.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8E95A2)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Widget Size 1: Glass Pill (2x1 Compact)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282E3B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Weather Glass Pill (2x1)",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Ultra minimalist horizontal layout. Minimal home screen footprint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E95A2),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val appWidgetManager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                    val myProvider = android.content.ComponentName(context, com.example.widget.WeatherWidgetProviderPill::class.java)
                                    val successCallback = android.app.PendingIntent.getBroadcast(
                                        context,
                                        com.example.widget.WeatherWidgetProviderPill::class.java.name.hashCode(),
                                        android.content.Intent(context, com.example.widget.WeatherWidgetProviderPill::class.java).apply {
                                            action = "PIN_WIDGET_CALLBACK"
                                        },
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                    )
                                    appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                                    Toast.makeText(context, "Adding Glass Pill layout to launcher...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "One-click pinning is not supported by your launcher.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Required Android 8.0+", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E00), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("PIN", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Widget Size 2: Mini Radar (3x2 Standard)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282E3B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Weather Satellite Mini (3x2)",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Our classic weather layout. Time sync, high/low columns, sleek controls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E95A2),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val appWidgetManager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                    val myProvider = android.content.ComponentName(context, com.example.widget.WeatherWidgetProvider::class.java)
                                    val successCallback = android.app.PendingIntent.getBroadcast(
                                        context,
                                        com.example.widget.WeatherWidgetProvider::class.java.name.hashCode(),
                                        android.content.Intent(context, com.example.widget.WeatherWidgetProvider::class.java).apply {
                                            action = "PIN_WIDGET_CALLBACK"
                                        },
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                    )
                                    appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                                    Toast.makeText(context, "Adding Mini Radar layout to launcher...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "One-click pinning is not supported by your launcher.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Required Android 8.0+", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E00), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("PIN", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Widget Size 3: Pro Max Dashboard Deck (4x3 Full Screen Deck)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282E3B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dashboard Pro Max Deck (4x3)",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Premium deck statistics including UV index, Wind speed, and Relative humidity levels.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E95A2),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val appWidgetManager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                    val myProvider = android.content.ComponentName(context, com.example.widget.WeatherWidgetProviderPro::class.java)
                                    val successCallback = android.app.PendingIntent.getBroadcast(
                                        context,
                                        com.example.widget.WeatherWidgetProviderPro::class.java.name.hashCode(),
                                        android.content.Intent(context, com.example.widget.WeatherWidgetProviderPro::class.java).apply {
                                            action = "PIN_WIDGET_CALLBACK"
                                        },
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                    )
                                    appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                                    Toast.makeText(context, "Adding Pro Max Deck layout to launcher...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "One-click pinning is not supported by your launcher.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Required Android 8.0+", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E00), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("PIN", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Home Widget force reload button
            Button(
                onClick = {
                    // Meticulously apply and save the customization preferences for the widget provider
                    prefs.edit()
                        .putInt("widget_selected_skin", selectedSkin)
                        .putFloat("widget_glass_opacity", glassOpacity)
                        .putBoolean("widget_border_glow", borderGlowEnabled)
                        .putBoolean("widget_twinkle_stars", twinkleStarsEnabled)
                        .apply()

                    // Update standard mini widget
                    val intent = Intent(context, com.example.widget.WeatherWidgetProvider::class.java).apply {
                        action = com.example.widget.WeatherWidgetProvider.ACTION_REFRESH_WIDGET
                    }
                    context.sendBroadcast(intent)

                    // Update compact pill widget
                    val intentPill = Intent(context, com.example.widget.WeatherWidgetProviderPill::class.java).apply {
                        action = com.example.widget.WeatherWidgetProviderPill.ACTION_REFRESH_PILL_WIDGET
                    }
                    context.sendBroadcast(intentPill)

                    // Update pro deck widget
                    val intentPro = Intent(context, com.example.widget.WeatherWidgetProviderPro::class.java).apply {
                        action = com.example.widget.WeatherWidgetProviderPro.ACTION_REFRESH_PRO_WIDGET
                    }
                    context.sendBroadcast(intentPro)

                    Toast.makeText(context, "All launcher widgets updated & synced!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9E00),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_widget_overlay_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SYNC ALL HOME LAUNCHER WIDGETS",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
}

@Composable
fun WidgetSparkleCanvas(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "stars_overlay")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starAlpha"
    )

    Canvas(modifier = modifier) {
        val random = java.util.Random(1337L) // Kept stable across frames
        repeat(14) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val starSize = 2.dp.toPx() + random.nextFloat() * 3.5.dp.toPx()

            // Draw diamond star vectors
            val starPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(x, y - starSize)
                quadraticTo(x, y, x + starSize, y)
                quadraticTo(x, y, x, y + starSize)
                quadraticTo(x, y, x - starSize, y)
                quadraticTo(x, y, x, y - starSize)
                close()
            }
            drawPath(
                path = starPath,
                color = Color(0xFFFFD54F).copy(alpha = alphaAnim * (0.35f + random.nextFloat() * 0.65f))
            )
        }
    }
}

// ----------------------------------------------------
// THE 4 PREMIUM TRANSPARENT GLASS WIDGET SKIN LAYOUTS
// ----------------------------------------------------

@Composable
fun CalmTwilightWidgetSkin(
    location: SavedLocation,
    isCelsius: Boolean,
    opacity: Float,
    glow: Boolean
) {
    val displayTemp = if (isCelsius) "${location.temperatureC.toInt()}°" else "${(location.temperatureC * 9/5 + 32).toInt()}°"

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFFFFFFFF).copy(alpha = opacity))
            .then(
                if (glow) Modifier.border(1.2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                else Modifier
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Location labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = location.cityName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = location.country.uppercase(Locale.ROOT),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(0.6f),
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = if (isCelsius) {
                        "H: ${location.highestTempC.toInt()}°  L: ${location.lowestTempC.toInt()}°"
                    } else {
                        "H: ${(location.highestTempC * 9/5 + 32).toInt()}°  L: ${(location.lowestTempC * 9/5 + 32).toInt()}°"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Temperature & floating 3D Cloud element
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val displayCond = getDisplayCondition(location.condition)
                Premium3DWeatherVisual(
                    condition = displayCond,
                    modifier = Modifier.size(90.dp)
                )

                Column {
                    Text(
                        text = displayTemp,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-2).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = displayCond.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFFFFD54F)
                    )
                }
            }

            // Footer metrics bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("WIND", fontSize = 8.sp, color = Color.White.copy(0.6f), fontWeight = FontWeight.Bold)
                    Text("${location.windKmh.toInt()} km/h", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.2f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HUMIDITY", fontSize = 8.sp, color = Color.White.copy(0.6f), fontWeight = FontWeight.Bold)
                    Text("${location.humidityPercent}%", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.2f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RAIN CHANCE", fontSize = 8.sp, color = Color.White.copy(0.6f), fontWeight = FontWeight.Bold)
                    Text("${location.rainChancePercent}%", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun CompactPillWidgetSkin(
    location: SavedLocation,
    isCelsius: Boolean,
    opacity: Float,
    glow: Boolean
) {
    val displayTemp = if (isCelsius) "${location.temperatureC.toInt()}°C" else "${(location.temperatureC * 9/5 + 32).toInt()}°F"

    Row(
        modifier = Modifier
            .width(280.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(Color(0xFF141F30).copy(alpha = opacity))
            .then(
                if (glow) Modifier.border(1.2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(38.dp))
                else Modifier
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val displayCond = getDisplayCondition(location.condition)
            Premium3DWeatherVisual(
                condition = displayCond,
                modifier = Modifier.size(46.dp)
            )

            Column {
                Text(
                    text = location.cityName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = displayCond,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = displayTemp,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = "0% DELAY",
                fontSize = 8.sp,
                color = Color(0xFFFF9E00),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ProWideDeckWidgetSkin(
    location: SavedLocation,
    forecastDetails: com.example.ui.FullWeatherDetails?,
    isCelsius: Boolean,
    opacity: Float,
    glow: Boolean
) {
    val displayTemp = if (isCelsius) "${location.temperatureC.toInt()}°C" else "${(location.temperatureC * 9/5 + 32).toInt()}°F"

    Box(
        modifier = Modifier
            .width(320.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0F1218).copy(alpha = opacity))
            .then(
                if (glow) Modifier.border(1.2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                else Modifier
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left pane
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val displayCond = getDisplayCondition(location.condition)
                Column {
                    Text(
                        text = location.cityName.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = displayCond,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF9E00)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = displayTemp,
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
                        color = Color.White
                    )

                    Text(
                        text = if (isCelsius) {
                            "H: ${location.highestTempC.toInt()}°  L: ${location.lowestTempC.toInt()}°"
                        } else {
                            "H: ${(location.highestTempC * 9/5 + 32).toInt()}°  L: ${(location.lowestTempC * 9/5 + 32).toInt()}°"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                }

                // Mini gauges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Air,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${location.windKmh.toInt()}kmh",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.9f)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.Grain,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${location.humidityPercent}%",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.9f)
                    )
                }
            }

            // Divider line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.White.copy(0.12f))
            )

            // Right pane: Weekly prediction rows
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "FORECAST",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = Color.White.copy(alpha = 0.5f)
                )

                val daysList = forecastDetails?.dailyList?.take(4) ?: listOf(
                    com.example.ui.DailyForecast("Mon", 24, 18, "Sunny", 0),
                    com.example.ui.DailyForecast("Tue", 23, 17, "Cloudy", 15),
                    com.example.ui.DailyForecast("Wed", 22, 16, "Rain", 40),
                    com.example.ui.DailyForecast("Thu", 25, 19, "Sunny", 0)
                )

                daysList.forEach { f ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = f.dayString,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        val dIcon = when {
                            f.condition.contains("Rain", true) || f.condition.contains("Shower", true) -> Icons.Filled.BeachAccess
                            f.condition.contains("Cloud", true) || f.condition.contains("Overcast", true) -> Icons.Filled.WbCloudy
                            else -> Icons.Filled.WbSunny
                        }
                        Icon(
                            imageVector = dIcon,
                            contentDescription = null,
                            tint = if (f.condition.contains("Sun", true)) Color(0xFFFFD54F) else Color(0xFF82B1FF),
                            modifier = Modifier.size(12.dp)
                        )

                        Text(
                            text = if (isCelsius) "${f.highC}°/${f.lowC}°" else "${(f.highC * 9/5 + 32).toInt()}°/${(f.lowC * 9/5 + 32).toInt()}°",
                            fontSize = 11.sp,
                            color = Color.White.copy(0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpaceSlateWidgetSkin(
    location: SavedLocation,
    isCelsius: Boolean,
    opacity: Float,
    glow: Boolean
) {
    val displayTemp = if (isCelsius) "${location.temperatureC.toInt()}°" else "${(location.temperatureC * 9/5 + 32).toInt()}°"

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF231A10).copy(alpha = opacity))
            .then(
                if (glow) Modifier.border(1.2.dp, Color(0xFFFF9E00).copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                else Modifier
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header information label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SPACE SLATE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = Color(0xFFFFD54F)
                    )
                    Text(
                        text = location.cityName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SATELLITE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF66)
                    )
                }
            }

            // Big 3D Weather Icon and Core readings
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val displayCond = getDisplayCondition(location.condition)
                Premium3DWeatherVisual(
                    condition = displayCond,
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = displayTemp,
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = displayCond.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                    color = Color.White.copy(0.85f)
                )
            }

            // High and low predictions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val highStr = if (isCelsius) "${location.highestTempC.toInt()}°" else "${(location.highestTempC * 9/5 + 32).toInt()}°"
                val lowStr = if (isCelsius) "${location.lowestTempC.toInt()}°" else "${(location.lowestTempC * 9/5 + 32).toInt()}°"
                Text(
                    text = "High: $highStr  •  Low: $lowStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun getDisplayCondition(condition: String): String {
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val isNight = currentHour >= 18 || currentHour < 6
    if (isNight) {
        val cond = condition.lowercase(Locale.ROOT)
        return when {
            cond.contains("clear sunny") -> "Clear Night"
            cond.contains("sunny & hot") -> "Warm & Clear"
            cond.contains("extreme heat") -> "Warm & Clear"
            cond.contains("sunny") -> "Clear Night"
            cond.contains("clear") -> "Clear Night"
            else -> condition
        }
    }
    return condition
}

@Composable
fun getThemeCardColor(themeMode: String, transparencyLevel: String): Color {
    val alpha = when (transparencyLevel) {
        "High (Glass)" -> 0.08f
        "Low (Muted)" -> 0.65f
        "Opaque (Solid)" -> 0.96f
        else -> 0.35f // Medium (Semi)
    }
    return when (themeMode) {
        "Deep Cosmic" -> Color(0xFF1D093F).copy(alpha = alpha)
        "Neon Cyan" -> Color(0xFF00121C).copy(alpha = alpha)
        "Amoled Black" -> Color(0xFF000000).copy(alpha = alpha)
        else -> Color(0xFF161B1F).copy(alpha = alpha) // Slate Dark
    }
}

@Composable
fun getThemeBorderColor(themeMode: String, transparencyLevel: String): Color {
    val alpha = if (transparencyLevel == "High (Glass)") 0.15f else 0.45f
    return when (themeMode) {
        "Deep Cosmic" -> Color(0xFF9045FF).copy(alpha = alpha)
        "Neon Cyan" -> Color(0xFF00E5FF).copy(alpha = alpha)
        "Amoled Black" -> Color(0xFFFFFFFF).copy(alpha = alpha)
        else -> Color(0xFF2D3135).copy(alpha = alpha) // Slate Dark
    }
}

@Composable
fun Premium3DWeatherVisual(condition: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating_3d")
    val translationY by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Canvas(modifier = modifier.offset(y = translationY.dp)) {
        val isStorm = condition.contains("Storm", ignoreCase = true)
        val isRain = condition.contains("Rain", ignoreCase = true) || condition.contains("Shower", ignoreCase = true)
        val isCloudy = condition.contains("Cloud", ignoreCase = true) || condition.contains("Overcast", ignoreCase = true)

        val centerOffset = Offset(size.width / 2, size.height / 2)

        if (isStorm || isRain || isCloudy) {
            // Draw gradient-filled layered cloud vectors simulating 3D puffy profiles
            // 1. Ambient blue cloud-glow radial background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF82B1FF).copy(0.35f), Color.Transparent),
                    radius = size.width * 0.55f
                ),
                center = centerOffset - Offset(5f, 5f),
                radius = size.width * 0.55f
            )

            // 2. Base shadow cloud layer
            drawCircle(
                color = Color(0xFF90A4AE).copy(0.6f),
                radius = size.width * 0.28f,
                center = centerOffset + Offset(35f, 15f)
            )

            // 3. Middle blue frost puffy circle
            drawCircle(
                color = Color(0xFFE8F0FE),
                radius = size.width * 0.32f,
                center = centerOffset - Offset(20f, 10f)
            )

            // 4. Forefront bright sunlit puffy cloud
            drawCircle(
                color = Color.White,
                radius = size.width * 0.28f,
                center = centerOffset + Offset(10f, 0f)
            )

            // Storm/Rain elements overlay
            if (isStorm) {
                // Neon yellow lightning path inside cloud
                val boltPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerOffset.x, centerOffset.y + 10f)
                    lineTo(centerOffset.x - 14f, centerOffset.y + 36f)
                    lineTo(centerOffset.x - 2f, centerOffset.y + 36f)
                    lineTo(centerOffset.x - 10f, centerOffset.y + 58f)
                    lineTo(centerOffset.x + 14f, centerOffset.y + 24f)
                    lineTo(centerOffset.x + 2f, centerOffset.y + 24f)
                    close()
                }
                drawPath(path = boltPath, color = Color(0xFFFFD54F))
            } else if (isRain) {
                // Diagonal rain raindrops
                drawCircle(color = Color(0xFF80DEEA), radius = 2.5f, center = centerOffset + Offset(-20f, 42f))
                drawCircle(color = Color(0xFF80DEEA), radius = 2.5f, center = centerOffset + Offset(-5f, 48f))
                drawCircle(color = Color(0xFF80DEEA), radius = 2.5f, center = centerOffset + Offset(10f, 44f))
            }
        } else {
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val isNight = currentHour >= 18 || currentHour < 6

            if (isNight) {
                // Beautiful 3D Glowing Crescent Moon for Night-time Clear sky
                val moonRadius = size.width * 0.32f
                
                // 1. Moonlight glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF82B1FF).copy(0.35f), Color(0x11B3E5FC), Color.Transparent),
                        center = centerOffset,
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = centerOffset
                )

                // 2. Draw crescent moon path using path operations
                val moonPath = androidx.compose.ui.graphics.Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(
                        centerOffset.x - moonRadius,
                        centerOffset.y - moonRadius,
                        centerOffset.x + moonRadius,
                        centerOffset.y + moonRadius
                    ))
                }
                val maskOffset = Offset(moonRadius * 0.45f, -moonRadius * 0.15f)
                val maskPath = androidx.compose.ui.graphics.Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(
                        centerOffset.x - moonRadius + maskOffset.x,
                        centerOffset.y - moonRadius + maskOffset.y,
                        centerOffset.x + moonRadius + maskOffset.x,
                        centerOffset.y + moonRadius + maskOffset.y
                    ))
                }
                
                val crescentPath = androidx.compose.ui.graphics.Path.combine(
                    androidx.compose.ui.graphics.PathOperation.Difference,
                    moonPath,
                    maskPath
                )

                // Draw the crescent with a gorgeous silver gradient
                drawPath(
                    path = crescentPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF64748B)),
                        start = centerOffset - Offset(moonRadius, moonRadius),
                        end = centerOffset + Offset(moonRadius, moonRadius)
                    )
                )

                // 3. Draw rotating twinkling diamond sparkles
                rotate(rotation * 0.5f, pivot = centerOffset) {
                    repeat(4) { i ->
                        val angle = (i * 90.0)
                        val rad = Math.toRadians(angle)
                        val dx = Math.cos(rad).toFloat()
                        val dy = Math.sin(rad).toFloat()
                        val sparkCenter = centerOffset + Offset(dx * moonRadius * 1.3f, dy * moonRadius * 1.3f)
                        
                        val pulseSpark = kotlin.math.sin(rotation * 0.08f + i) * 2.5f
                        val starSize = 5f + pulseSpark
                        
                        val starPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sparkCenter.x, sparkCenter.y - starSize * 2)
                            lineTo(sparkCenter.x + starSize * 0.6f, sparkCenter.y - starSize * 0.6f)
                            lineTo(sparkCenter.x + starSize * 2, sparkCenter.y)
                            lineTo(sparkCenter.x + starSize * 0.6f, sparkCenter.y + starSize * 0.6f)
                            moveTo(sparkCenter.x, sparkCenter.y + starSize * 2)
                            lineTo(sparkCenter.x - starSize * 0.6f, sparkCenter.y + starSize * 0.6f)
                            lineTo(sparkCenter.x - starSize * 2, sparkCenter.y)
                            lineTo(sparkCenter.x - starSize * 0.6f, sparkCenter.y - starSize * 0.6f)
                            close()
                        }
                        drawPath(path = starPath, color = Color(0xFFE2E8F0).copy(alpha = 0.8f))
                    }
                }
            } else {
                // Elegant bright rotating solar body representation
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFA000), Color(0xFFFFD54F), Color.Transparent),
                        radius = size.width * 0.48f
                    ),
                    center = centerOffset,
                    radius = size.width * 0.48f
                )

                // Cozy hot-gold core
                drawCircle(
                    color = Color(0xFFFFEE58),
                    radius = size.width * 0.28f,
                    center = centerOffset
                )

                // Draw clean geometric solar beams rotating dynamically
                rotate(rotation, pivot = centerOffset) {
                    repeat(8) { i ->
                        val angle = (i * 45.0)
                        val rad = Math.toRadians(angle)
                        val dx = Math.cos(rad).toFloat()
                        val dy = Math.sin(rad).toFloat()
                        drawLine(
                            color = Color(0xFFFF9E00).copy(0.8f),
                            start = centerOffset + Offset(dx * size.width * 0.32f, dy * size.height * 0.32f),
                            end = centerOffset + Offset(dx * size.width * 0.46f, dy * size.height * 0.46f),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LauncherWidgetPromoCard(
    onConfigureClick: () -> Unit,
    containerColor: Color = Color(0xFF13171D).copy(alpha = 0.85f),
    borderColor: Color = Color(0xFF232833)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable { onConfigureClick() }
            .testTag("launcher_widget_promo_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF9E00).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFFF9E00),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRANSPARENT WIDGETS",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color.White
                )
                Text(
                    text = "Configure zero-delay glassmorphic skins for your home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E95A2)
                )
            }
            IconButton(
                onClick = onConfigureClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1F2429))
                    .testTag("widget_promo_settings_gear")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Widget Settings",
                    tint = Color(0xFFC4C6CF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun getTranslatedLabel(label: String, language: String): String {
    val isHindi = language.contains("हिंदी") || language.contains("Hindi")
    val isBengali = language.contains("বাংলা") || language.contains("Bengali")
    return when (label) {
        "Atmospheric Details" -> when {
            isHindi -> "वायुमंडलीय विवरण"
            isBengali -> "বায়ুমণ্ডলীয় বিবরণ"
            else -> "Atmospheric Details"
        }
        "Weekly Forecast" -> when {
            isHindi -> "साप्ताहिक पूर्वानुमान"
            isBengali -> "সাপ্তাহিক পূর্বাভাস"
            else -> "Weekly Forecast"
        }
        "Rain Chance" -> when {
            isHindi -> "बारिश की संभावना"
            isBengali -> "বৃষ্টির সম্ভাবনা"
            else -> "Rain Chance"
        }
        "Humidity" -> when {
            isHindi -> "आर्द्रता"
            isBengali -> "আর্দ্রতা"
            else -> "Humidity"
        }
        "Wind Speed" -> when {
            isHindi -> "हवा की गति"
            isBengali -> "বাতাসের গতিবেগ"
            else -> "Wind Speed"
        }
        "UV Index" -> when {
            isHindi -> "यूवी इंडेक्स"
            isBengali -> "ইউভি সূচক"
            else -> "UV Index"
        }
        "Air Quality" -> when {
            isHindi -> "वायु गुणवत्ता"
            isBengali -> "বায়ুর গুণমান"
            else -> "Air Quality"
        }
        "Feels Like" -> when {
            isHindi -> "ऐसा महसूस होता है"
            isBengali -> "অনুভূত তাপমাত্রা"
            else -> "Feels Like"
        }
        "Highest" -> when {
            isHindi -> "अधिकतम"
            isBengali -> "সর্বোচ্চ"
            else -> "Highest"
        }
        "Lowest" -> when {
            isHindi -> "न्यूनतम"
            isBengali -> "সর্বনিম্ন"
            else -> "Lowest"
        }
        "Saved Locations" -> when {
            isHindi -> "सहेजे गए स्थान"
            isBengali -> "সংরक्षित স্থানসমূহ"
            else -> "Saved Locations"
        }
        "Local Alert Center" -> when {
            isHindi -> "स्थानीय अलर्ट केंद्र"
            isBengali -> "স্থানীয় সতর্কতা কেন্দ্র"
            else -> "Local Alert Center"
        }
        "Active Alerts" -> when {
            isHindi -> "सक्रिय अलर्ट"
            isBengali -> "সক্রিয় সতর্কতা"
            else -> "Active Alerts"
        }
        "No active weather warnings. All clear." -> when {
            isHindi -> "कोई सक्रिय मौसम चेतावनी नहीं। सब सामान्य।"
            isBengali -> "কোনো সক্রিয় আবহাওয়া সতর্কতা নেই। সব ঠিক আছে।"
            else -> "No active weather warnings. All clear."
        }
        "Add Alert Trigger" -> when {
            isHindi -> "अलर्ट ट्रिगर जोड़ें"
            isBengali -> "সতর্কতা যোগ করুন"
            else -> "Add Alert Trigger"
        }
        "Search City, Country..." -> when {
            isHindi -> "शहर, देश खोजें..."
            isBengali -> "শহর, দেশ অনুসন্ধান করুন..."
            else -> "Search City, Country..."
        }
        "Select Weather Aspect" -> when {
            isHindi -> "मौसम पहलू का चयन करें"
            isBengali -> "আবহাওয়ার দিক চয়ন করুন"
            else -> "Select Weather Aspect"
        }
        "Threshold Value" -> when {
            isHindi -> "सीमा मान"
            isBengali -> "সীমা মান"
            else -> "Threshold Value"
        }
        "Cancel" -> when {
            isHindi -> "रद्द करें"
            isBengali -> "বাতিল করুন"
            else -> "Cancel"
        }
        "Add" -> when {
            isHindi -> "जोड़ें"
            isBengali -> "যোগ করুন"
            else -> "Add"
        }
        "AI Weather Briefing" -> when {
            isHindi -> "एआई मौसम ब्रीफिंग"
            isBengali -> "এআই আবহাওয়া ব্রিফিং"
            else -> "AI Weather Briefing"
        }
        "Weekly weather overview and expectations" -> when {
            isHindi -> "साप्ताहिक मौसम अवलोकन और अपेक्षाएं"
            isBengali -> "সাপ্তাহিক আবহাওয়া ওভারভিউ এবং প্রত্যাশা"
            else -> "Weekly weather overview and expectations"
        }
        "Historical Trends" -> when {
            isHindi -> "ऐतिहासिक रुझान"
            isBengali -> "ঐতিহাসিক প্রবণতা"
            else -> "Historical Trends"
        }
        "Compare dynamic logs" -> when {
            isHindi -> "गतिशील लॉग की तुलना करें"
            isBengali -> "ডায়নামিক লগ তুলনা করুন"
            else -> "Compare dynamic logs"
        }
        "Radar View Layer" -> when {
            isHindi -> "रडार दृश्य परत"
            isBengali -> "রাডার ভিউ লেয়ার"
            else -> "Radar View Layer"
        }
        "24-Hour Radar Timeline" -> when {
            isHindi -> "24-घंटे रडार समयरेखा"
            isBengali -> "২৪ ঘণ্টার রাডার টাইমলাইন"
            else -> "24-Hour Radar Timeline"
        }
        else -> label
    }
}
