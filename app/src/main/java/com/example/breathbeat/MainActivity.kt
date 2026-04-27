package com.example.breathbeat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.example.breathbeat.bluetooth.BluetoothConnectionManagerImpl
import com.example.breathbeat.bluetooth.BluetoothConnectionState
import com.example.breathbeat.database.AppDatabase
import com.example.breathbeat.database.LungCapacityRecord
import com.example.breathbeat.database.LungCapacityRepository
import com.example.breathbeat.database.LungCapacityRepositoryImpl
import com.example.breathbeat.health.HealthManager
import com.example.breathbeat.health.HealthManagerImpl
import com.example.breathbeat.health.MonthlyStats
import com.example.breathbeat.spirometer.SpirometerManager
import com.example.breathbeat.ui.theme.BreathBeatTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private lateinit var spirometerManager: SpirometerManager
    private lateinit var healthManager: HealthManager
    private lateinit var lungCapacityRepository: LungCapacityRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val bluetoothConnectionManager = BluetoothConnectionManagerImpl(applicationContext)
        spirometerManager = SpirometerManager(bluetoothConnectionManager)
        healthManager = HealthManagerImpl(applicationContext)
        
        val database = AppDatabase.getDatabase(applicationContext)
        lungCapacityRepository = LungCapacityRepositoryImpl(database.lungCapacityDao())

        enableEdgeToEdge()
        setContent {
            BreathBeatTheme {
                BreathBeatApp(spirometerManager, healthManager, lungCapacityRepository)
            }
        }
    }
}

@Composable
fun BreathBeatApp(
    spirometerManager: SpirometerManager, 
    healthManager: HealthManager,
    lungCapacityRepository: LungCapacityRepository
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var measuringDataSaved by rememberSaveable { mutableStateOf(false) }
    val isBlowing by spirometerManager.isBlowing.collectAsState()

    // Reset saved state when user starts blowing again
    LaunchedEffect(isBlowing) {
        if (isBlowing) {
            measuringDataSaved = false
        }
    }

    val destinations = remember { AppDestinations.entries.filter { it != AppDestinations.HISTORY } }
    
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            destinations.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Breath&Beat Alpha 0.0.1",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        ) { innerPadding ->
            var totalDrag by remember { mutableFloatStateOf(0f) }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pointerInput(currentDestination) {
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                val currentIndex = destinations.indexOf(currentDestination)
                                
                                if (currentIndex != -1) {
                                    if (totalDrag > 150) { // Swipe Right -> Go Previous
                                        if (currentIndex > 0) {
                                            currentDestination = destinations[currentIndex - 1]
                                            change.consume()
                                            totalDrag = -10000f // Stop further detection for this gesture
                                        }
                                    } else if (totalDrag < -150) { // Swipe Left -> Go Next
                                        if (currentIndex < destinations.size - 1) {
                                            currentDestination = destinations[currentIndex + 1]
                                            change.consume()
                                            totalDrag = 10000f // Stop further detection
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        val fromIndex = destinations.indexOf(initialState)
                        val toIndex = destinations.indexOf(targetState)
                        
                        if (fromIndex == -1 || toIndex == -1) {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        } else if (toIndex > fromIndex) {
                            (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn()).togetherWith(
                             slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn()).togetherWith(
                             slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut())
                        }
                    },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        AppDestinations.HOME -> HomeScreen(healthManager, lungCapacityRepository) {
                            currentDestination = AppDestinations.HISTORY
                        }
                        AppDestinations.MEASURING -> MeasuringScreen(
                            spirometerManager = spirometerManager, 
                            lungCapacityRepository = lungCapacityRepository,
                            dataSaved = measuringDataSaved,
                            onDataSavedChange = { measuringDataSaved = it }
                        )
                        AppDestinations.MY_PLAN -> MyPlanScreen(healthManager, lungCapacityRepository)
                        AppDestinations.HISTORY -> HistoryScreen(lungCapacityRepository) {
                            currentDestination = AppDestinations.HOME
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    healthManager: HealthManager, 
    lungCapacityRepository: LungCapacityRepository,
    onLungCapacityClick: () -> Unit
) {
    val context = LocalContext.current
    var avgHeartRate by remember { mutableStateOf<Long?>(null) }
    var minHeartRate by remember { mutableStateOf<Long?>(null) }
    var maxHeartRate by remember { mutableStateOf<Long?>(null) }
    
    var avgOxygen by remember { mutableStateOf<Double?>(null) }
    var minOxygen by remember { mutableStateOf<Double?>(null) }
    var maxOxygen by remember { mutableStateOf<Double?>(null) }

    var avgLung by remember { mutableStateOf<Double?>(null) }
    var minLung by remember { mutableStateOf<Double?>(null) }
    var maxLung by remember { mutableStateOf<Double?>(null) }

    var yearlyHeartRateStats by remember { mutableStateOf<List<MonthlyStats>>(emptyList()) }
    var yearlyOxygenStats by remember { mutableStateOf<List<MonthlyStats>>(emptyList()) }
    var yearlyLungStats by remember { mutableStateOf<List<MonthlyStats>>(emptyList()) }

    var hasHealthPermissions by remember { mutableStateOf(false) }
    var healthAvailability by remember { mutableStateOf(HealthConnectClient.SDK_UNAVAILABLE) }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasHealthPermissions = granted.containsAll(healthManager.getPermissionRequestIntent())
    }

    LaunchedEffect(Unit) {
        healthAvailability = HealthConnectClient.getSdkStatus(context)
        if (healthAvailability == HealthConnectClient.SDK_AVAILABLE) {
            hasHealthPermissions = healthManager.hasAllPermissions()
        }
        
        try {
            val endTime = Instant.now().toEpochMilli()
            val startTime = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()
            avgLung = lungCapacityRepository.getAverageLungCapacity(startTime, endTime)
            val (minL, maxL) = lungCapacityRepository.getMinMaxLungCapacity(startTime, endTime)
            minLung = minL
            maxLung = maxL
            yearlyLungStats = lungCapacityRepository.getYearlyLungStats()
        } catch (e: Exception) {
            Log.e("BreathBeat", "Error fetching lung data", e)
        }
    }

    LaunchedEffect(hasHealthPermissions) {
        if (hasHealthPermissions) {
            try {
                val endTime = Instant.now()
                val startTime = endTime.minus(7, ChronoUnit.DAYS)
                avgHeartRate = healthManager.getAverageHeartRate(startTime, endTime)
                val (minHR, maxHR) = healthManager.getMinMaxHeartRate(startTime, endTime)
                minHeartRate = minHR
                maxHeartRate = maxHR

                avgOxygen = healthManager.getAverageOxygenSaturation(startTime, endTime)
                val (minO2, maxO2) = healthManager.getMinMaxOxygenSaturation(startTime, endTime)
                minOxygen = minO2
                maxOxygen = maxO2

                yearlyHeartRateStats = healthManager.getYearlyHeartRateStats()
                yearlyOxygenStats = healthManager.getYearlyOxygenStats()
            } catch (e: Exception) {
                Log.e("BreathBeat", "Error fetching health data", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Health Overview", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Past Week Summary", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        HealthMetricCard(
            title = "Vital Capacity",
            unit = "ml",
            avgValue = avgLung?.let { "%.0f".format(it) } ?: "--",
            minValue = minLung?.let { "%.0f".format(it) } ?: "--",
            maxValue = maxLung?.let { "%.0f".format(it) } ?: "--",
            color = Color(0xFF4CAF50),
            onClick = onLungCapacityClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (healthAvailability == HealthConnectClient.SDK_AVAILABLE) {
            if (!hasHealthPermissions) {
                Button(onClick = {
                    healthPermissionLauncher.launch(healthManager.getPermissionRequestIntent())
                }) {
                    Text("Authorize Health Connect")
                }
            } else {
                HealthMetricCard(
                    title = "Heart Rate",
                    unit = "BPM",
                    avgValue = avgHeartRate?.toString() ?: "--",
                    minValue = minHeartRate?.toString() ?: "--",
                    maxValue = maxHeartRate?.toString() ?: "--",
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HealthMetricCard(
                    title = "SpO2",
                    unit = "%",
                    avgValue = avgOxygen?.let { "%.1f".format(it) } ?: "--",
                    minValue = minOxygen?.let { "%.1f".format(it) } ?: "--",
                    maxValue = maxOxygen?.let { "%.1f".format(it) } ?: "--",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Yearly Trends", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        MonthlyLineChart(
            title = "Vital Capacity Trends (Yearly)",
            stats = yearlyLungStats,
            color = Color(0xFF4CAF50),
            unit = "ml"
        )

        if (hasHealthPermissions) {
            Spacer(modifier = Modifier.height(16.dp))
            MonthlyLineChart(
                title = "Heart Rate Trends (Yearly)",
                stats = yearlyHeartRateStats,
                color = MaterialTheme.colorScheme.primary,
                unit = "BPM"
            )

            Spacer(modifier = Modifier.height(16.dp))

            MonthlyLineChart(
                title = "SpO2 Trends (Yearly)",
                stats = yearlyOxygenStats,
                color = MaterialTheme.colorScheme.secondary,
                unit = "%"
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp)) // Extra space for the alpha text
    }
}

@Composable
fun HealthMetricCard(
    title: String,
    unit: String,
    avgValue: String,
    minValue: String,
    maxValue: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(text = "Average", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = avgValue,
                            style = MaterialTheme.typography.headlineLarge,
                            color = color
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = unit, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Min", style = MaterialTheme.typography.labelSmall)
                        Text(text = minValue, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Max", style = MaterialTheme.typography.labelSmall)
                        Text(text = maxValue, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(lungCapacityRepository: LungCapacityRepository, onBack: () -> Unit) {
    val records by lungCapacityRepository.getAllRecords().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    val groupedRecords = records.groupBy { record ->
        Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vital Capacity History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No records found")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groupedRecords.forEach { (month, monthRecords) ->
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = month, style = MaterialTheme.typography.titleMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "${monthRecords.size} records", style = MaterialTheme.typography.bodySmall)
                                            Icon(
                                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                    
                                    if (expanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        monthRecords.forEach { record ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(text = "${record.volumeML} ml", style = MaterialTheme.typography.bodyLarge)
                                                    Text(
                                                        text = Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM, HH:mm")),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    scope.launch { lungCapacityRepository.deleteRecord(record) }
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                                }
                                            }
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyLineChart(
    title: String,
    stats: List<MonthlyStats>,
    color: Color,
    unit: String
) {
    var showAvg by remember { mutableStateOf(true) }
    var showMin by remember { mutableStateOf(true) }
    var showMax by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showAvg,
                    onClick = { showAvg = !showAvg },
                    label = { Text("Avg", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = showMin,
                    onClick = { showMin = !showMin },
                    label = { Text("Min", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = showMax,
                    onClick = { showMax = !showMax },
                    label = { Text("Max", style = MaterialTheme.typography.labelSmall) }
                )
            }

            if (stats.isNotEmpty() && stats.any { it.avg != null || it.min != null || it.max != null }) {
                val allValues = stats.flatMap { listOfNotNull(it.avg, it.min, it.max) }
                val minY = (allValues.minOrNull() ?: 0.0) * 0.9
                val maxY = (allValues.maxOrNull() ?: 100.0) * 1.1
                val rangeY = if (maxY - minY == 0.0) 1.0 else maxY - minY

                Box(modifier = Modifier.height(150.dp).fillMaxWidth().padding(top = 16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val stepX = width / (stats.size - 1)

                        fun getX(index: Int) = index * stepX
                        fun getY(value: Double) = height - ((value - minY) / rangeY * height).toFloat()

                        if (showAvg) drawLineSeries(stats.map { it.avg }, color, this, ::getX, ::getY)
                        if (showMin) drawLineSeries(stats.map { it.min }, color.copy(alpha = 0.4f), this, ::getX, ::getY)
                        if (showMax) drawLineSeries(stats.map { it.max }, color.copy(alpha = 0.7f), this, ::getX, ::getY)
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    stats.forEachIndexed { index, stat ->
                        if (index % 2 == 0 || index == stats.size - 1) {
                            Text(stat.monthName, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.height(150.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No data available", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun drawLineSeries(
    data: List<Double?>,
    color: Color,
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    getX: (Int) -> Float,
    getY: (Double) -> Float
) {
    val path = Path()
    var firstPoint = true
    
    data.forEachIndexed { index, value ->
        if (value != null) {
            val x = getX(index)
            val y = getY(value)
            if (firstPoint) {
                path.moveTo(x, y)
                firstPoint = false
            } else {
                path.lineTo(x, y)
            }
            drawScope.drawCircle(color = color, radius = 4f, center = Offset(x, y))
        }
    }
    
    drawScope.drawPath(
        path = path,
        color = color,
        style = Stroke(width = 4f)
    )
}

@SuppressLint("MissingPermission")
@Composable
fun MeasuringScreen(
    spirometerManager: SpirometerManager, 
    lungCapacityRepository: LungCapacityRepository,
    dataSaved: Boolean,
    onDataSavedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val connectionState by spirometerManager.bluetoothConnectionManager.connectionState.collectAsState()
    val volumeML by spirometerManager.volumeML.collectAsState()
    val isBlowing by spirometerManager.isBlowing.collectAsState()
    val scannedDevices by spirometerManager.bluetoothConnectionManager.scannedDevices.collectAsState()
    
    val scope = rememberCoroutineScope()
    var hasBtPermissions by remember { mutableStateOf(checkBluetoothPermissions(context)) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBtPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasBtPermissions) {
            btPermissionLauncher.launch(getRequiredPermissions())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasBtPermissions) {
            Text(text = "Bluetooth permissions are required for measuring.")
            Button(onClick = { btPermissionLauncher.launch(getRequiredPermissions()) }) {
                Text("Authorize Bluetooth")
            }
        } else {
            // Vital Capacity Section
            Text(text = "Vital Capacity", style = MaterialTheme.typography.titleMedium)
            Text(text = "$volumeML", style = MaterialTheme.typography.displayLarge)
            Text(text = "ml", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Status: ${connectionState.name}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(16.dp))
            
            // Save Data Button
            Button(
                onClick = { 
                    scope.launch {
                        try {
                            lungCapacityRepository.insertRecord(volumeML)
                            onDataSavedChange(true)
                            Log.d("MeasuringScreen", "Saved volume: $volumeML")
                        } catch (e: Exception) {
                            Log.e("MeasuringScreen", "Error saving data", e)
                        }
                    }
                },
                enabled = !isBlowing && volumeML > 0 && !dataSaved
            ) {
                Text(if (dataSaved) "Data Saved" else "Save Data")
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (connectionState == BluetoothConnectionState.DISCONNECTED) {
                Button(onClick = { 
                    Log.d("BreathBeat", "Starting discovery")
                    spirometerManager.bluetoothConnectionManager.startDiscovery() 
                }) {
                    Text("Start Discovery")
                }
            } else if (connectionState == BluetoothConnectionState.CONNECTED) {
                Button(onClick = { spirometerManager.disconnect() }) {
                    Text("Disconnect")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bluetooth Scan Section (only if disconnected)
            if (connectionState == BluetoothConnectionState.DISCONNECTED) {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(scannedDevices.filter { it.name?.contains("ESP32") == true }) { device ->
                        DeviceItem(device = device) {
                            spirometerManager.connect(device)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun MyPlanScreen(healthManager: HealthManager, lungCapacityRepository: LungCapacityRepository) {
    var avgHR by remember { mutableStateOf<Long?>(null) }
    var avgSpO2 by remember { mutableStateOf<Double?>(null) }
    var avgVC by remember { mutableStateOf<Double?>(null) }
    
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceColor)
    val scoreStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = onSurfaceColor)

    // Baseline logic (using 1 week averages for now as placeholder for baseline)
    val baselineHR = 70.0
    val baselineSpO2 = 98.0
    val baselineVC = 3500.0

    LaunchedEffect(Unit) {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)
        avgHR = healthManager.getAverageHeartRate(startTime, endTime)
        avgSpO2 = healthManager.getAverageOxygenSaturation(startTime, endTime)
        avgVC = lungCapacityRepository.getAverageLungCapacity(startTime.toEpochMilli(), endTime.toEpochMilli())
    }

    val hrScore = avgHR?.let { 100 - ((it - baselineHR) / baselineHR * 100).coerceIn(0.0, 100.0) } ?: 0.0
    val spo2Score = avgSpO2?.let { if (it < 92) (it/92*40) else if (it < 94) 40 + (it-92)/2*30 else 70 + (it-94)/6*30 }.let { it?.coerceIn(0.0, 100.0) } ?: 0.0
    val vcScore = avgVC?.let { (it / baselineVC * 100).coerceIn(0.0, 100.0) } ?: 0.0

    val readinessScore = (spo2Score * 0.4 + hrScore * 0.35 + vcScore * 0.25)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My Training Plan", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.size(280.dp)) {
            RadarChart(
                scores = listOf(spo2Score, hrScore, vcScore),
                labels = listOf("SpO2", "Heart Rate", "Vital Capacity"),
                color = MaterialTheme.colorScheme.primary,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
                scoreStyle = scoreStyle
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Body Readiness Score: ${readinessScore.toInt()}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                val recommendation = when {
                    readinessScore >= 90 -> "Option A: High-Intensity Breakthrough\nFocus: VO2max challenges, HIIT, heavy lifting."
                    readinessScore >= 70 -> "Option B: Routine Endurance/Strength\nFocus: Zone 2 - Zone 3 aerobic training, moderate circuit training."
                    readinessScore >= 40 -> "Option C: Active Recovery\nFocus: Yoga, walking, Breathing Exercises (VC restoration)."
                    else -> "Option D: Mandatory Care\nAction: Stop training. Focus on meditation, hydration, and medical observation."
                }
                
                Text(recommendation, style = MaterialTheme.typography.bodyLarge)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Physiological Status Guide", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your training plan is dynamically adjusted based on the integration of SpO2 (40%), Heart Rate (35%), and Vital Capacity (25%). \n\n" +
                   "• Optimal: suitable for challenges.\n" +
                   "• Fatigue: stay with low-to-moderate loads.\n" +
                   "• Stress: focus on recovery and sleep.\n" +
                   "• Warning: absolute rest required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun RadarChart(
    scores: List<Double>, 
    labels: List<String>, 
    color: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: androidx.compose.ui.text.TextStyle,
    scoreStyle: androidx.compose.ui.text.TextStyle
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 * 0.7f // Adjusted to fit labels
        val numAxes = scores.size
        val angleStep = 2 * Math.PI / numAxes

        // Draw grid lines
        for (i in 1..4) {
            val r = radius * i / 4
            val path = Path()
            for (j in 0 until numAxes) {
                val angle = j * angleStep - Math.PI / 2
                val x = centerX + r * cos(angle).toFloat()
                val y = centerY + r * sin(angle).toFloat()
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color.Gray.copy(alpha = 0.3f), style = Stroke(width = 1.dp.toPx()))
        }

        // Draw axis lines and marks
        for (i in 0 until numAxes) {
            val angle = i * angleStep - Math.PI / 2
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(centerX, centerY), Offset(x, y), strokeWidth = 1.dp.toPx())

            // Labels at the ends of axes
            val label = labels[i]
            val labelLayout = textMeasurer.measure(label, labelStyle)
            val labelRadius = radius + 20.dp.toPx()
            val lx = centerX + labelRadius * cos(angle).toFloat() - labelLayout.size.width / 2
            val ly = centerY + labelRadius * sin(angle).toFloat() - labelLayout.size.height / 2
            drawText(labelLayout, topLeft = Offset(lx, ly))
        }

        // Draw data area and points
        val dataPath = Path()
        for (i in 0 until numAxes) {
            val angle = i * angleStep - Math.PI / 2
            val r = radius * (scores[i] / 100).toFloat()
            val x = centerX + r * cos(angle).toFloat()
            val y = centerY + r * sin(angle).toFloat()
            
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            drawCircle(color, 4.dp.toPx(), Offset(x, y))

            // Score numerical mark next to the point
            val scoreText = scores[i].toInt().toString()
            val scoreLayout = textMeasurer.measure(scoreText, scoreStyle)
            val sx = x + 12.dp.toPx() * cos(angle).toFloat() - scoreLayout.size.width / 2
            val sy = y + 12.dp.toPx() * sin(angle).toFloat() - scoreLayout.size.height / 2
            drawText(scoreLayout, topLeft = Offset(sx, sy))
        }
        dataPath.close()
        drawPath(dataPath, color.copy(alpha = 0.3f))
        drawPath(dataPath, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            @Suppress("DEPRECATION")
            Column {
                Text(text = device.name ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun getRequiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        permissions.add(Manifest.permission.BLUETOOTH)
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
    }
    return permissions.toTypedArray()
}

private fun checkBluetoothPermissions(context: Context): Boolean {
    return getRequiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    MEASURING("Measuring", R.drawable.ic_favorite),
    MY_PLAN("My Plan", R.drawable.ic_account_box),
    HISTORY("History", R.drawable.ic_home) // Navigation to this is handled via click
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
