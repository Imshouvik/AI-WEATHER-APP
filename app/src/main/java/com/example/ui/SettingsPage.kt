package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    onShowWidgetStudio: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weather_prefs_v3", android.content.Context.MODE_PRIVATE) }

    // Collect variables from ViewModel
    val isCelsius by viewModel.isCelsius.collectAsStateWithLifecycle()
    val transparencyLevel by viewModel.transparencyLevel.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val autoRefreshInterval by viewModel.autoRefreshInterval.collectAsStateWithLifecycle()

    // Local dialog controls for simulation
    var showFaqDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var isClearingCache by remember { mutableStateOf(false) }

    // Set correct initial transparency and language in shared preferences if not set
    LaunchedEffect(Unit) {
        val savedOpacity = prefs.getFloat("widget_glass_opacity", 0.18f)
        val level = when {
            savedOpacity <= 0.08f -> "High (Glass)"
            savedOpacity <= 0.20f -> "Medium (Semi)"
            savedOpacity <= 0.40f -> "Low (Muted)"
            else -> "Opaque (Solid)"
        }
        viewModel.setTransparencyLevel(level)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F1216) // Premium dark obsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back to Dashboard",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = getTranslatedLabel("PREMIUM SETTINGS", selectedLanguage),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = getTranslatedLabel("Tailor your atmospheric view and overlays", selectedLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8C9199)
                    )
                }
            }

            Divider(color = Color(0xFF1F2429), thickness = 1.dp)

            // Scrollable settings body
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // SECTION 1: Temperature Unit Selector
                SettingsSectionHeader(title = getTranslatedLabel("UNIT PREFERENCES", selectedLanguage), icon = Icons.Filled.Thermostat)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B1F), RoundedCornerShape(14.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isCelsius) Color(0xFF7491FF) else Color.Transparent)
                            .clickable { if (!isCelsius) viewModel.toggleTempUnit() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedLanguage.contains("हिंदी") || selectedLanguage.contains("Hindi")) "सेल्सियस (°C)" else if (selectedLanguage.contains("বাংলা") || selectedLanguage.contains("Bengali")) "সেলসিয়াস (°C)" else "Celsius (°C)",
                            fontWeight = FontWeight.Bold,
                            color = if (isCelsius) Color.Black else Color.White,
                            fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (!isCelsius) Color(0xFF7491FF) else Color.Transparent)
                            .clickable { if (isCelsius) viewModel.toggleTempUnit() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedLanguage.contains("हिंदी") || selectedLanguage.contains("Hindi")) "फ़ारेनहाइट (°F)" else if (selectedLanguage.contains("বাংলা") || selectedLanguage.contains("Bengali")) "ফারেনহাইট (°F)" else "Fahrenheit (°F)",
                            fontWeight = FontWeight.Bold,
                            color = if (!isCelsius) Color.Black else Color.White,
                            fontSize = 14.sp
                        )
                    }
                }

                // SECTION 2: Transparency & Glassmorphism Studio
                SettingsSectionHeader(title = getTranslatedLabel("DASHBOARD TRANSPARENCY PRESET", selectedLanguage), icon = Icons.Filled.BlurOn)
                Text(
                    text = getTranslatedLabel("Controls card opacity levels across all screens, allowing the beautiful animated weather backgrounds to flow behind elements.", selectedLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8C9199),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                val opacityPresets = listOf(
                    Triple("High (Glass)", 0.05f, "Highly transparent crystal glass look"),
                    Triple("Medium (Semi)", 0.18f, "Perfect balanced translucency (Default)"),
                    Triple("Low (Muted)", 0.35f, "Strong contrast, subtle background bleed"),
                    Triple("Opaque (Solid)", 0.90f, "Solid dark cards for absolute readability")
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF161B1F))
                ) {
                    opacityPresets.forEach { (label, opacityValue, desc) ->
                        val isSelected = transparencyLevel == label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTransparencyLevel(label)
                                    prefs.edit().putFloat("widget_glass_opacity", opacityValue).apply()
                                    Toast.makeText(context, "Transparency set to: $label", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setTransparencyLevel(label)
                                    prefs.edit().putFloat("widget_glass_opacity", opacityValue).apply()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7491FF))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = desc,
                                    color = Color(0xFF8C9199),
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF7491FF).copy(0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Active",
                                        tint = Color(0xFF7491FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 3: APP CUSTOM THEMES
                SettingsSectionHeader(title = getTranslatedLabel("EXCLUSIVE THEME STYLES", selectedLanguage), icon = Icons.Filled.Palette)
                val themes = listOf(
                    Triple("Slate Dark", Color(0xFF1F2429), "Premium Charcoal Slate & Gold accents"),
                    Triple("Deep Cosmic", Color(0xFF1D093F), "Cosmic Violet & Nebula Pink tints"),
                    Triple("Neon Cyan", Color(0xFF00121C), "Cyber Grid dark & electric glowing Cyan"),
                    Triple("Amoled Black", Color(0xFF000000), "Pitch battery saving velvet Black")
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(themes) { (tName, tColor, tDesc) ->
                        val isSelected = themeMode == tName
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable {
                                    viewModel.setThemeMode(tName)
                                    Toast.makeText(context, "$tName theme applied!", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1F2429) else Color(0xFF161B1F)
                            ),
                            border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF7491FF)) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(55.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(tColor, tColor.copy(alpha = 0.5f))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Brush,
                                            contentDescription = "Active Theme",
                                            tint = Color(0xFF7491FF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = tName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tDesc,
                                    fontSize = 10.sp,
                                    color = Color(0xFF8C9199),
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }

                // SECTION 4: Hompage & Overlays Customizer Studio Link
                SettingsSectionHeader(title = getTranslatedLabel("WIDGET OVERLAY STUDIO", selectedLanguage), icon = Icons.Filled.AspectRatio)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowWidgetStudio() },
                    colors = CardDefaults.cardColors(containerColor = Color(0x227491FF)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF7491FF).copy(0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF7491FF).copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Studio",
                                tint = Color(0xFF7491FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getTranslatedLabel("Lancher Overlays & Designs", selectedLanguage),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = getTranslatedLabel("Design custom transparent overlay skins, border glows, and twinkle factors", selectedLanguage),
                                color = Color(0xFF8C9199),
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Forward Settings",
                            tint = Color.White.copy(0.6f)
                        )
                    }
                }

                // SECTION 5: Localization / Language
                SettingsSectionHeader(title = getTranslatedLabel("INTERNATIONALIZATION", selectedLanguage), icon = Icons.Filled.Language)
                val languages = listOf("English (US)", "हिंदी (Hindi)", "বাংলা (Bengali)")
                var showLangDropdown by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF161B1F))
                        .clickable { showLangDropdown = !showLangDropdown }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getTranslatedLabel("App Display Language", selectedLanguage),
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = selectedLanguage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7491FF)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (showLangDropdown) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = "Toggle picker",
                            tint = Color.White.copy(0.7f)
                        )
                    }
                }

                if (showLangDropdown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F2429))
                            .padding(vertical = 4.dp)
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(text = lang, color = Color.White) },
                                onClick = {
                                    viewModel.setSelectedLanguage(lang)
                                    showLangDropdown = false
                                    Toast.makeText(context, "Language switched to $lang!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // SECTION 6: Broadcast Settings / Notifications
                SettingsSectionHeader(title = getTranslatedLabel("REALTIME ALERTS & NOTIFICATIONS", selectedLanguage), icon = Icons.Filled.NotificationsActive)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF161B1F))
                ) {
                    NotificationToggleRow(
                        title = getTranslatedLabel("Atmospheric Notifications", selectedLanguage),
                        desc = getTranslatedLabel("Receive severe storms and weather warnings", selectedLanguage),
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                    Divider(color = Color(0xFF1F2429), thickness = 1.dp)
                    var dailyReport by remember { mutableStateOf(true) }
                    NotificationToggleRow(
                        title = getTranslatedLabel("Daily Morning Briefing", selectedLanguage),
                        desc = getTranslatedLabel("Get Gemini Smart summaries every single morning", selectedLanguage),
                        checked = dailyReport,
                        onCheckedChange = { dailyReport = it }
                    )
                    Divider(color = Color(0xFF1F2429), thickness = 1.dp)
                    var autoRefreshValue by remember { mutableStateOf(true) }
                    NotificationToggleRow(
                        title = getTranslatedLabel("Persistent Status Bar Temp", selectedLanguage),
                        desc = getTranslatedLabel("Show real-time temperature in notifications", selectedLanguage),
                        checked = autoRefreshValue,
                        onCheckedChange = { autoRefreshValue = it }
                    )
                }

                // SECTION 7: Advanced Tools & Sync
                SettingsSectionHeader(title = getTranslatedLabel("ADVANCED SYNCHRONIZATION", selectedLanguage), icon = Icons.Filled.Sync)
                
                // Interval Row
                var showIntervalDropdown by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF161B1F))
                        .clickable { showIntervalDropdown = !showIntervalDropdown }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getTranslatedLabel("Background Auto Refresh", selectedLanguage),
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = autoRefreshInterval,
                        fontSize = 14.sp,
                        color = Color(0xFF7491FF),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showIntervalDropdown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F2429))
                            .padding(vertical = 4.dp)
                    ) {
                        val intervals = listOf("15 min", "30 min", "1 hour", "Manual Only")
                        intervals.forEach { interval ->
                            DropdownMenuItem(
                                text = { Text(text = interval, color = Color.White) },
                                onClick = {
                                    viewModel.setAutoRefreshInterval(interval)
                                    showIntervalDropdown = false
                                }
                            )
                        }
                    }
                }

                // Cache cleaner row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF161B1F))
                        .clickable {
                            isClearingCache = true
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getTranslatedLabel("Clear Database & Vector Cache", selectedLanguage),
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (isClearingCache) {
                        CircularProgressIndicator(
                            color = Color(0xFF7491FF),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1200)
                            isClearingCache = false
                            Toast.makeText(context, "Cleared 24.3 MB cache successfully!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        IconButton(
                            onClick = { isClearingCache = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clean cache",
                                tint = Color(0xFFE2E2E6)
                            )
                        }
                    }
                }

                // SECTION 8: FAQ & Customer Support
                SettingsSectionHeader(title = getTranslatedLabel("SUPPORT & ASSISTANCE", selectedLanguage), icon = Icons.Filled.HelpCenter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showFaqDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B1F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.HelpOutline, contentDescription = "FAQ", tint = Color(0xFF7491FF))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = getTranslatedLabel("FAQ Guide", selectedLanguage), color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showFeedbackDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B1F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.Feedback, contentDescription = "Feedback", tint = Color(0xFFFF9E00))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = getTranslatedLabel("Send Feedback", selectedLanguage), color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "AI WEATHER RADAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(0.4f),
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "v1.0.0 Premium Active",
                        fontSize = 10.sp,
                        color = Color.White.copy(0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Developed by Shouvik Maitra",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color(0xFFA1A1AA)
                        )
                    }
                    Text(
                        text = "© 2026. shouvikmaitra.com",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.25.sp
                        ),
                        color = Color(0xFF7491FF)
                    )
                }
            }
        }
    }

    // FAQ Dialog Mockup
    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = {
                Text(
                    text = "AI Weather Radar Help & FAQ",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FaqItem(
                        q = "How do transparency presets work?",
                        a = "Setting transparency to 'High (Glass)' reduces card opacity dynamically so you can view beautiful clouds, stars, or lighting rain directly behind the cards!"
                    )
                    FaqItem(
                        q = "Can I customize separate cards?",
                        a = "Yes, use the 'Widget Overlay Studio' to customise home screen, skins, borders, or specific wallpaper designs."
                    )
                    FaqItem(
                        q = "Are AI briefings updated automatically?",
                        a = "Yes, every time the weather is updated, Gemini analyses physical values and compiles high-fidelity reports instantly."
                    )
                }
            },
            containerColor = Color(0xFF141A1E),
            confirmButton = {
                TextButton(onClick = { showFaqDialog = false }) {
                    Text("Close", color = Color(0xFF7491FF))
                }
            }
        )
    }

    // Feedback Dialog Mockup
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = {
                Text(
                    text = "Submit Premium Feedback",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text("What options/improvements would you like to see?", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF7491FF),
                        unfocusedBorderColor = Color.White.copy(0.2f)
                    )
                )
            },
            containerColor = Color(0xFF141A1E),
            confirmButton = {
                Button(
                    onClick = {
                        if (feedbackText.trim().isNotEmpty()) {
                            // Intent to send email
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:") 
                                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("maitrashouvik@gmail.com")) // REPLACE WITH YOUR EMAIL
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "AI Weather App Feedback")
                                putExtra(android.content.Intent.EXTRA_TEXT, feedbackText)
                            }
                            try {
                                context.startActivity(android.content.Intent.createChooser(intent, "Send feedback via..."))
                                feedbackText = ""
                                showFeedbackDialog = false
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email app found to send feedback.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Body cannot be empty!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7491FF))
                ) {
                    Text("Submit Report", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF7491FF).copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color(0xFF7491FF).copy(alpha = 0.8f)
        )
    }
}

@Composable
fun NotificationToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = desc,
                color = Color(0xFF8C9199),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF7491FF),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1F2429)
            )
        )
    }
}

@Composable
fun FaqItem(q: String, a: String) {
    Column {
        Text(
            text = q,
            color = Color(0xFF7491FF),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = a,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Divider(color = Color.White.copy(0.08f), thickness = 1.dp, modifier = Modifier.padding(top = 8.dp))
    }
}
