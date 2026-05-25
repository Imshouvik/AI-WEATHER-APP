package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.*
import java.util.Locale

// Simple model representing precipitation cells
data class RadPrecipCell(
    val id: Int,
    val xOffset: Float, // relative to map center
    val yOffset: Float,
    val size: Float,
    val dbzIntensity: Int, // 10-60 DBZ (intensity scale)
    val type: CellType = CellType.RAIN
)

enum class CellType { RAIN, SNOW, STORM_CELL }

enum class RadarLayer { PRECIPITATION, TEMPERATURE, WIND }

// Simple wind particle model for animated air flows
data class RadWindParticle(
    var offset: Offset,
    var alpha: Float,
    var speed: Float,
    var life: Float
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun InteractiveRadarView(
    cityName: String,
    modifier: Modifier = Modifier,
    activeLayer: RadarLayer = RadarLayer.PRECIPITATION,
    isPlaying: Boolean = true,
    radarTimelineMultiplier: Float = 1.0f,
    latitude: Float = 0f,
    longitude: Float = 0f,
    currentTempC: Float = 20f,
    currentHumidity: Int = 50
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Animators
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    // Current timeframe animation
    val frameIndexAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5.99f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TimelineFrame"
    )

    val frameIndex = if (isPlaying) frameIndexAnim.toInt() else 3

    // Dynamic landmass based on geography seed
    val landmassPath = remember(latitude, longitude) {
        val path = Path()
        val r = java.util.Random((latitude.hashCode() xor longitude.hashCode()).toLong() + 12L)
        val startX = -350f + r.nextFloat() * 100f
        val startY = -250f + r.nextFloat() * 100f
        path.moveTo(startX, startY)
        path.relativeQuadraticTo(150f + r.nextFloat() * 100f, -150f, 350f, -50f)
        path.relativeQuadraticTo(180f + r.nextFloat() * 100f, 250f, 50f, 450f)
        path.relativeQuadraticTo(-200f, 80f, -380f, -20f)
        path.relativeQuadraticTo(-150f, -120f, -120f, -380f)
        path.close()
        path
    }

    val islets = remember(latitude, longitude) {
        val r = java.util.Random((latitude.hashCode() xor longitude.hashCode()).toLong() + 87L)
        List(3) {
            val rx = -300f + r.nextFloat() * 600f
            val ry = -300f + r.nextFloat() * 600f
            val rRadius = 25f + r.nextFloat() * 50f
            Pair(Offset(rx, ry), rRadius)
        }
    }

    // Seed precipitation cell coordinates based on cityName to give unique layouts
    val precipCells = remember(cityName) {
        val seed = cityName.hashCode().toLong()
        val random = java.util.Random(seed)
        List(12) { i ->
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val dist = 100f + random.nextFloat() * 250f
            RadPrecipCell(
                id = i,
                xOffset = cos(angle) * dist,
                yOffset = sin(angle) * dist,
                size = 30f + random.nextFloat() * 90f,
                dbzIntensity = 15 + random.nextInt(45),
                type = when {
                    i % 4 == 0 -> CellType.STORM_CELL
                    i % 5 == 0 -> CellType.SNOW
                    else -> CellType.RAIN
                }
            )
        }
    }

    // Seed cities to show on our local weather radar
    val radarCities = remember(cityName) {
        val seed = cityName.hashCode().toLong()
        val random = java.util.Random(seed)
        listOf(
            cityName to Offset(0f, 0f),
            "Sector North" to Offset(70f, -220f),
            "Sector East" to Offset(240f, 40f),
            "Sector Northwest" to Offset(-190f, -140f),
            "Coast Guard" to Offset(-150f, 200f),
            "Meteor Station" to Offset(180f, -160f)
        )
    }

    // Wind particles
    val windParticles = remember {
        mutableStateListOf<RadWindParticle>().apply {
            repeat(35) {
                add(
                    RadWindParticle(
                        offset = Offset((0..600).random().toFloat(), (0..600).random().toFloat()),
                        alpha = Math.random().toFloat(),
                        speed = 3f + Math.random().toFloat() * 6f,
                        life = Math.random().toFloat()
                    )
                )
            }
        }
    }

    // Update wind particle coordinates
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                withFrameMillis {
                    for (particle in windParticles) {
                        particle.offset = particle.offset.copy(
                            x = (particle.offset.x + particle.speed) % 1000f,
                            y = (particle.offset.y + (sin(particle.offset.x / 50f) * 1.5f))
                        )
                        if (particle.offset.y < 0) particle.offset = particle.offset.copy(y = 600f)
                        if (particle.offset.y > 1000) particle.offset = particle.offset.copy(y = 0f)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0C1017)) // Match deep dark marine-blue/slate ocean backdrop from Windy
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 4.0f)
                    offset = offset + pan * scale
                }
            }
    ) {
        val textMeasurer = rememberTextMeasurer()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center + offset

            // 1. Compass / Range Rings (Drawn Static / Centered relative to view panning)
            drawRadarGrid(center, scale, textMeasurer)

            // Draw clean geographic coordinate grid (like professional weather radars)
            drawGisGrid(center, scale, textMeasurer, latitude, longitude)

            // Dynamic scaling transfrom for layers
            withTransform({
                translate(center.x, center.y)
                scale(scale, scale, Offset.Zero)
            }) {
                // 2. Draw satellite topography & contours (Windy-style geographic map landmass simulation)
                // Solid land background with subtle topographic gradient
                drawPath(
                    path = landmassPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF23262C), Color(0xFF333842))
                    )
                )

                // Outer coastal water depth contours (Isolines radiating outward)
                (1..3).forEach { r ->
                    val outwardScale = 1f + (r * 0.05f)
                    withTransform({
                        scale(outwardScale, outwardScale, Offset.Zero)
                    }) {
                        drawPath(
                            path = landmassPath,
                            color = Color(0x1F00E5FF), // Extremely fine neon cyan coastal isoline
                            style = Stroke(width = 0.8f)
                        )
                    }
                }

                // Shoreline soft dark outline
                drawPath(
                    path = landmassPath,
                    color = Color(0xFF4C5361),
                    style = Stroke(width = 1.5f)
                )

                // High-fidelity continental elevation contours inside land
                (1..4).forEach { d ->
                    val insetScale = 1f - (d * 0.12f)
                    withTransform({
                        scale(insetScale, insetScale, Offset.Zero)
                    }) {
                        drawPath(
                            path = landmassPath,
                            color = Color(0xFF3B404E), // Inner terrain altitude rings
                            style = Stroke(width = 0.8f)
                        )
                    }
                }

                // Stylized high-fidelity islets with matching dark topographic contours
                islets.forEach { (offset, radius) ->
                    // Solid landmass
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF23262C), Color(0xFF333842))
                        ),
                        radius = radius,
                        center = offset
                    )
                    // High quality coastline marker
                    drawCircle(
                        color = Color(0xFF4C5361),
                        radius = radius,
                        center = offset,
                        style = Stroke(width = 1.2f)
                    )
                    // Inner elevation isoline
                    drawCircle(
                        color = Color(0xFF3B404E),
                        radius = radius * 0.6f,
                        center = offset,
                        style = Stroke(width = 0.8f)
                    )
                    // Coastal shoal reflection contour
                    drawCircle(
                        color = Color(0x1a00E5FF),
                        radius = radius * 1.15f,
                        center = offset,
                        style = Stroke(width = 1.0f)
                    )
                }

                // Draw professional meteorological isobars (atmospheric pressure curves)
                drawAtmosphericIsobars(Offset.Zero, scale, textMeasurer, if (currentTempC > 25) 1008 else 1014)

                // 3. Render radar overlay layers
                when (activeLayer) {
                    RadarLayer.PRECIPITATION -> {
                        // Draw clouds or rain cells
                        drawPrecipitationLayer(precipCells, sweepAngle, frameIndex)
                    }
                    RadarLayer.TEMPERATURE -> {
                        // Render a colorful thermal Satellite Heatmap
                        drawThermalHeatmapLayer(precipCells, frameIndex)
                    }
                    RadarLayer.WIND -> {
                        // Render vector wind visual stream lines
                        drawWindFlowLines(windParticles)
                    }
                }

                // 4. Radar sweep scanner sweeping beam
                if (activeLayer == RadarLayer.PRECIPITATION) {
                    drawScannerSweepBeam(sweepAngle)
                }

                // 5. Landmarks / Cities on map
                drawRadarCities(radarCities, scale, textMeasurer)
            }

            // Outer Scope: Draw Overlay compass indicators, scale, and HUD metadata
            val scaleSpec = String.format(Locale.getDefault(), "Scale: %.2fx", scale)
            val textResult = textMeasurer.measure(
                text = AnnotatedString(scaleSpec),
                style = TextStyle(color = Color(0xCC00FF66), fontSize = 11.sp)
            )
            drawRect(
                color = Color(0xAA020A14),
                topLeft = Offset(size.width - textResult.size.width - 24f, size.height - 40f),
                size = Size(textResult.size.width + 16f, textResult.size.height + 8f)
            )
            drawText(
                textResult,
                topLeft = Offset(size.width - textResult.size.width - 16f, size.height - 36f)
            )

            // Dynamic Radar GPS HUD
            val hudLabel = String.format(
                Locale.getDefault(),
                "GEOLOC LINK: %.4f°, %.4f° | TEMP: %.1f°C | HUMID: %d%%",
                latitude, longitude, currentTempC, currentHumidity
            )
            val hudTextResult = textMeasurer.measure(
                text = AnnotatedString(hudLabel),
                style = TextStyle(color = Color(0xFF00FF66), fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            )
            drawText(
                hudTextResult,
                topLeft = Offset(45f, 40f) // Just below top HUD
            )

            val modeText = "RADAR BAND: X-BAND LIVE METEOSAT"
            val textModeResult = textMeasurer.measure(
                text = AnnotatedString(modeText),
                style = TextStyle(color = Color(0xCC00FF66), fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            )
            drawText(
                textModeResult,
                topLeft = Offset(45f, 15f)
            )
        }
    }
}

private fun DrawScope.drawRadarGrid(center: Offset, scale: Float, textMeasurer: TextMeasurer) {
    val rings = listOf(150f, 300f, 450f, 600f)
    val ringColors = Color(0x3300FF66)
    val gridTextPaint = TextStyle(color = Color(0x7700FF66), fontSize = 10.sp)

    // Grid crosshairs
    drawLine(
        color = ringColors,
        start = Offset(0f, center.y),
        end = Offset(size.width, center.y),
        strokeWidth = 1f
    )
    drawLine(
        color = ringColors,
        start = Offset(center.x, 0f),
        end = Offset(center.x, size.height),
        strokeWidth = 1f
    )

    // Concentric range rings
    rings.forEachIndexed { idx, radius ->
        val scaledRadius = radius * scale
        drawCircle(
            color = ringColors,
            radius = scaledRadius,
            center = center,
            style = Stroke(width = if (idx == rings.lastIndex) 2f else 1f, pathEffect = if (idx % 2 == 1) PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f) else null)
        )

        // Range labels
        val distanceText = "${(idx + 1) * 50} km"
        val textResult = textMeasurer.measure(
            text = AnnotatedString(distanceText),
            style = gridTextPaint
        )
        drawText(
            textLayoutResult = textResult,
            topLeft = Offset(center.x + scaledRadius + 8f, center.y - 18f)
        )
    }

    // N/S/E/W Compass indicators
    val directions = listOf("N" to Offset(0f, -650f), "E" to Offset(650f, 0f), "S" to Offset(0f, 650f), "W" to Offset(-650f, 0f))
    directions.forEach { (dir, pos) ->
        val scaledPos = center + pos * scale
        if (scaledPos.x in 0f..size.width && scaledPos.y in 0f..size.height) {
            val textResult = textMeasurer.measure(
                text = AnnotatedString(dir),
                style = TextStyle(color = Color(0xFF00FF66), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(scaledPos.x - textResult.size.width / 2, scaledPos.y - textResult.size.height / 2)
            )
        }
    }
}

// Simulated map outline to make it look premium and real
private fun DrawScope.drawLandmassOutline() {
    val mapPath = Path().apply {
        moveTo(-400f, -200f)
        relativeQuadraticTo(100f, -100f, 300f, -50f)
        relativeQuadraticTo(200f, 200f, 100f, 400f)
        relativeQuadraticTo(-150f, 100f, -300f, 0f)
        relativeQuadraticTo(-200f, -100f, -100f, -350f)
        close()
    }

    drawPath(
        path = mapPath,
        color = Color(0x0F00E5FF)
    )
    drawPath(
        path = mapPath,
        color = Color(0x3300E5FF),
        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    )

    // Islets
    drawCircle(color = Color(0x1F00E5FF), radius = 50f, center = Offset(-200f, 300f))
    drawCircle(color = Color(0x1F00E5FF), radius = 75f, center = Offset(350f, -250f))
}

// Drawing dynamic precipitation clouds
private fun DrawScope.drawPrecipitationLayer(cells: List<RadPrecipCell>, sweepAngle: Float, frameIndex: Int) {
    cells.forEach { cell ->
        // Simulating timelines: cells move slowly in a direction
        val timeOffsetX = frameIndex * 14f * (if (cell.id % 2 == 0) 1f else -0.5f)
        val timeOffsetY = frameIndex * 8f

        val cellCenter = Offset(cell.xOffset + timeOffsetX, cell.yOffset + timeOffsetY)

        // Calculate intensity brush
        val colors = getPrecipColorScale(cell.dbzIntensity, cell.type)

        // Build composite glow gradients
        val radialBrush = Brush.radialGradient(
            colors = colors,
            center = cellCenter,
            radius = cell.size
        )

        drawCircle(
            brush = radialBrush,
            radius = cell.size,
            center = cellCenter
        )

        // Highlight cores for active storms
        if (cell.dbzIntensity > 40) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFFF3366), Color.Transparent),
                    center = cellCenter,
                    radius = cell.size * 0.35f
                ),
                radius = cell.size * 0.35f,
                center = cellCenter
            )
        }
    }
}

// Satellite Satellite Heatmap overlay (Thermal view)
private fun DrawScope.drawThermalHeatmapLayer(cells: List<RadPrecipCell>, frameIndex: Int) {
    cells.forEach { cell ->
        val timeOffsetX = frameIndex * 10f
        val timeOffsetY = frameIndex * 5f
        val cellCenter = Offset(cell.xOffset + timeOffsetX, cell.yOffset + timeOffsetY)

        // Thermal Colors: Warm centers, cool boundaries
        val colors = listOf(
            Color(0xFFFF5722), // 35C (Red/Orange)
            Color(0xFFFFC107), // 28C (Yellow)
            Color(0xBB4CAF50), // 20C (Green)
            Color(0x5500BCD4), // 15C (Cyan)
            Color.Transparent
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = cellCenter,
                radius = cell.size * 1.5f
            ),
            radius = cell.size * 1.5f,
            center = cellCenter
        )
    }
}

// Particle stream lines
private fun DrawScope.drawWindFlowLines(particles: List<RadWindParticle>) {
    particles.forEach { particle ->
        val lineLength = 40f
        val endY = particle.offset.y + (sin(particle.offset.x / 50f) * 10f)

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color(0xEE00B0FF).copy(alpha = particle.alpha)),
                startX = particle.offset.x - lineLength,
                endX = particle.offset.x
            ),
            start = Offset(particle.offset.x - lineLength, particle.offset.y),
            end = Offset(particle.offset.x, endY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
}

// Draws the bright sweep glow
private fun DrawScope.drawScannerSweepBeam(sweepAngle: Float) {
    val sweepRad = Math.toRadians(sweepAngle.toDouble()).toFloat()
    val sweepDistance = 800f
    val endPoint = Offset(
        x = cos(sweepRad) * sweepDistance,
        y = sin(sweepRad) * sweepDistance
    )

    // Main line of sweeps
    drawLine(
        color = Color(0xFF00FF66),
        start = Offset.Zero,
        end = endPoint,
        strokeWidth = 2.5f,
        cap = StrokeCap.Round
    )

    // Sweep trail / sector slice using brush
    val shaderBrush = Brush.sweepGradient(
        colors = listOf(
            Color(0xAA00FF66),
            Color(0x3300FF66),
            Color(0x0500FF66),
            Color.Transparent,
            Color.Transparent
        ),
        center = Offset.Zero
    )

    drawArc(
        brush = shaderBrush,
        startAngle = sweepAngle - 45f,
        sweepAngle = 45f,
        useCenter = true,
        size = Size(sweepDistance * 2, sweepDistance * 2),
        topLeft = Offset(-sweepDistance, -sweepDistance)
    )
}

// Precipitation DBZ color mappings
private fun getPrecipColorScale(dbz: Int, type: CellType): List<Color> {
    return when (type) {
        CellType.SNOW -> {
            // White, Ice Light Blue
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color(0xFF80DEEA).copy(alpha = 0.7f),
                Color(0xFF00BCD4).copy(alpha = 0.4f),
                Color.Transparent
            )
        }
        CellType.STORM_CELL -> {
            // High Intensity Red/Pink core
            listOf(
                Color(0xFFE040FB), // Severe hail magenta
                Color(0xFFFF1744), // Heavy red storm
                Color(0xFFFF9100), // Heavy rain orange
                Color(0xFF76FF03).copy(alpha = 0.5f),
                Color.Transparent
            )
        }
        CellType.RAIN -> {
            // Green to Blue standard drizzle schema
            listOf(
                Color(0xFFFFEA00).copy(alpha = 0.8f), // Yellow heavy
                Color(0xFF00E676).copy(alpha = 0.7f), // Moderate green
                Color(0xFF2979FF).copy(alpha = 0.5f), // Light blue
                Color.Transparent
            )
        }
    }
}

// Labels on Map
private fun DrawScope.drawRadarCities(cities: List<Pair<String, Offset>>, scale: Float, textMeasurer: TextMeasurer) {
    cities.forEach { (name, pos) ->
        val markerRadius = 5f
        // Draw standard round marker
        drawCircle(
            color = if (name == cities.first().first) Color(0xFFFF5252) else Color(0xFF00FF66),
            radius = markerRadius,
            center = pos
        )
        // Draw pulsating halo indicator
        drawCircle(
            color = if (name == cities.first().first) Color(0x55FF5252) else Color(0x3300FF66),
            radius = markerRadius * 2.5f,
            center = pos,
            style = Stroke(width = 1.5f)
        )

        // Draw name label text
        val labelStyle = TextStyle(
            color = Color.White.copy(alpha = 0.85f),
            fontSize = (11 / scale).coerceIn(8f, 15f).sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
        val textLayout = textMeasurer.measure(AnnotatedString(name), labelStyle)

        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(pos.x + 8f, pos.y - textLayout.size.height / 2f)
        )
    }
}

// HUD graphics
private fun DrawScope.drawRadarHud(layer: RadarLayer, scale: Float, textMeasurer: TextMeasurer) {
    // Zoom HUD info
    val scaleSpec = String.format(Locale.getDefault(), "Scale: %.2fx", scale)
    val textResult = textMeasurer.measure(
        text = AnnotatedString(scaleSpec),
        style = TextStyle(color = Color(0xCC00FF66), fontSize = 11.sp)
    )
    drawRect(
        color = Color(0xAA020A14),
        topLeft = Offset(size.width - textResult.size.width - 24f, size.height - 40f),
        size = Size(textResult.size.width + 16f, textResult.size.height + 8f)
    )
    drawText(
        textLayoutResult = textResult,
        topLeft = Offset(size.width - textResult.size.width - 16f, size.height - 36f)
    )

    // Mode details
    val modeText = "RADAR BAND: X-BAND SATELLITE (${layer.name})"
    val textModeResult = textMeasurer.measure(
        text = AnnotatedString(modeText),
        style = TextStyle(color = Color(0xFF00FF66), fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    )
    drawText(
        textLayoutResult = textModeResult,
        topLeft = Offset(45f, 40f)
    )
}

// Spectrum Legend control panel
@Composable
fun RadarLegend(modifier: Modifier = Modifier, activeLayer: RadarLayer = RadarLayer.PRECIPITATION) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0x99020F24)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3300FF66))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "OVERLAY SPECTRUM",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF00FF66),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (activeLayer) {
                    RadarLayer.PRECIPITATION -> {
                        LegendItem("Drizzle", Color(0xFF2979FF))
                        LegendItem("Moderate", Color(0xFF00E676))
                        LegendItem("Storm Cell", Color(0xFFFF9100))
                        LegendItem("Severe / Hail", Color(0xFFE040FB))
                    }
                    RadarLayer.TEMPERATURE -> {
                        LegendItem("Cold (10°C)", Color(0x5500BCD4))
                        LegendItem("Mild (20°C)", Color(0xBB4CAF50))
                        LegendItem("Warm (28°C)", Color(0xFFFFC107))
                        LegendItem("Core Hot (35°C)", Color(0xFFFF5722))
                    }
                    RadarLayer.WIND -> {
                        LegendItem("Low Flow", Color(0x3300B0FF))
                        LegendItem("Brisk Vector", Color(0xAA00B0FF))
                        LegendItem("High Current", Color(0xEE00B0FF))
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Text(text = label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

// Draw a premium GIS geographic coordinates grid matching actual Latitude/Longitude
private fun DrawScope.drawGisGrid(
    center: Offset,
    scale: Float,
    textMeasurer: TextMeasurer,
    latitude: Float,
    longitude: Float
) {
    val step = 200f
    val cols = (size.width / step).toInt() + 2
    val rows = (size.height / step).toInt() + 2
    
    // Horizontal Latitudes
    for (i in -rows..rows) {
        val y = center.y + i * step * scale
        if (y in 0f..size.height) {
            drawLine(
                color = Color(0x0C00E5FF),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 18f))
            )
            val latVal = latitude - (i * 0.12f)
            val formatStr = String.format(Locale.getDefault(), "%.2f° %s", abs(latVal), if (latVal >= 0) "N" else "S")
            val textLayout = textMeasurer.measure(
                text = AnnotatedString(formatStr),
                style = TextStyle(color = Color(0x4400E5FF), fontSize = 8.sp)
            )
            drawText(textLayout, topLeft = Offset(15f, y - 10f))
        }
    }
    
    // Vertical Longitudes
    for (j in -cols..cols) {
        val x = center.x + j * step * scale
        if (x in 0f..size.width) {
            drawLine(
                color = Color(0x0C00E5FF),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 18f))
            )
            val lonVal = longitude + (j * 0.18f)
            val formatStr = String.format(Locale.getDefault(), "%.2f° %s", abs(lonVal), if (lonVal >= 0) "E" else "W")
            val textLayout = textMeasurer.measure(
                text = AnnotatedString(formatStr),
                style = TextStyle(color = Color(0x4400E5FF), fontSize = 8.sp)
            )
            drawText(textLayout, topLeft = Offset(x + 5f, size.height - 18f))
        }
    }
}

// Draw professional isobar pressure bounds (Windy-style contour graphics)
private fun DrawScope.drawAtmosphericIsobars(
    center: Offset,
    scale: Float,
    textMeasurer: TextMeasurer,
    pressureHpa: Int
) {
    val isobarRadii = listOf(
        pressureHpa - 4 to 260f,
        pressureHpa to 400f,
        pressureHpa + 4 to 540f
    )
    
    isobarRadii.forEach { (press, baseRadius) ->
        val scaledRadius = baseRadius * scale
        val centerShift = Offset(-30f * scale, -25f * scale)
        
        drawCircle(
            color = Color(0x1200E5FF),
            radius = scaledRadius,
            center = center + centerShift,
            style = Stroke(
                width = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f))
            )
        )
        
        val label = "$press hPa"
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(color = Color(0x2E00E5FF), fontSize = 9.sp)
        )
        
        // Position on lower left of curve
        val angleRad = Math.toRadians(135.0).toFloat()
        val textPos = center + centerShift + Offset(
            cos(angleRad) * scaledRadius,
            sin(angleRad) * scaledRadius
        )
        if (textPos.x in 0f..size.width && textPos.y in 0f..size.height) {
            drawText(textLayout, topLeft = textPos)
        }
    }
}
