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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.example.breathbeat.bluetooth.BluetoothConnectionManagerImpl
import com.example.breathbeat.bluetooth.BluetoothConnectionState
import com.example.breathbeat.health.HealthManager
import com.example.breathbeat.health.HealthManagerImpl
import com.example.breathbeat.spirometer.SpirometerManager
import com.example.breathbeat.ui.theme.BreathBeatTheme
import java.time.Instant
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private lateinit var spirometerManager: SpirometerManager
    private lateinit var healthManager: HealthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val bluetoothConnectionManager = BluetoothConnectionManagerImpl(applicationContext)
        spirometerManager = SpirometerManager(bluetoothConnectionManager)
        healthManager = HealthManagerImpl(applicationContext)

        enableEdgeToEdge()
        setContent {
            BreathBeatTheme {
                BreathBeatApp(spirometerManager, healthManager)
            }
        }
    }
}

@Composable
fun BreathBeatApp(spirometerManager: SpirometerManager, healthManager: HealthManager) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen(healthManager)
                    AppDestinations.MEASURING -> MeasuringScreen(spirometerManager)
                    AppDestinations.PROFILE -> Greeting(name = "Profile")
                }
            }
        }
    }
}

@Composable
fun HomeScreen(healthManager: HealthManager) {
    val context = LocalContext.current
    var avgHeartRate by remember { mutableStateOf<Long?>(null) }
    var avgOxygen by remember { mutableStateOf<Double?>(null) }

    var hasHealthPermissions by remember { mutableStateOf(false) }
    var healthAvailability by remember { mutableStateOf(HealthConnectClient.SDK_UNAVAILABLE) }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d("BreathBeat", "Health permissions granted: $granted")
        hasHealthPermissions = granted.containsAll(healthManager.getPermissionRequestIntent())
    }

    LaunchedEffect(Unit) {
        val status = HealthConnectClient.getSdkStatus(context)
        Log.d("BreathBeat", "Health Connect SDK status: $status")
        healthAvailability = status
        
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            hasHealthPermissions = healthManager.hasAllPermissions()
            Log.d("BreathBeat", "Has Health permissions: $hasHealthPermissions")
        }
    }

    LaunchedEffect(hasHealthPermissions) {
        if (hasHealthPermissions) {
            try {
                val endTime = Instant.now()
                val startTime = endTime.minus(7, ChronoUnit.DAYS)
                avgHeartRate = healthManager.getAverageHeartRate(startTime, endTime)
                avgOxygen = healthManager.getAverageOxygenSaturation(startTime, endTime)
            } catch (e: Exception) {
                Log.e("BreathBeat", "Error fetching health data", e)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Health Overview", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Health Section
        Text(text = "Past Week Average (Health Connect)", style = MaterialTheme.typography.titleMedium)
        
        when (healthAvailability) {
            HealthConnectClient.SDK_AVAILABLE -> {
                if (!hasHealthPermissions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val permissions = healthManager.getPermissionRequestIntent()
                        Log.d("BreathBeat", "Requesting Health permissions: $permissions")
                        healthPermissionLauncher.launch(permissions)
                    }) {
                        Text("Authorize Health Connect")
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Heart Rate", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = avgHeartRate?.toString() ?: "--",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = "BPM", style = MaterialTheme.typography.labelMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Blood Oxygen", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = avgOxygen?.let { "%.1f".format(it) } ?: "--",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(text = "%", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Text("Health Connect update required.")
            }
            else -> {
                Text("Health Connect is not available on this device.")
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MeasuringScreen(spirometerManager: SpirometerManager) {
    val context = LocalContext.current
    val connectionState by spirometerManager.bluetoothConnectionManager.connectionState.collectAsState()
    val volumeML by spirometerManager.volumeML.collectAsState()
    val scannedDevices by spirometerManager.bluetoothConnectionManager.scannedDevices.collectAsState()

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
            // Lung Capacity Section
            Text(text = "Lung Capacity", style = MaterialTheme.typography.titleMedium)
            Text(text = "$volumeML", style = MaterialTheme.typography.displayLarge)
            Text(text = "ml", style = MaterialTheme.typography.headlineMedium)
            Text(text = "Status: ${connectionState.name}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(8.dp))
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
        }
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
            Column {
                @Suppress("DEPRECATION")
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
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
