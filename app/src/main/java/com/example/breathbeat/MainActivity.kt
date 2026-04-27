package com.example.breathbeat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.example.breathbeat.bluetooth.BluetoothConnectionManagerImpl
import com.example.breathbeat.bluetooth.BluetoothConnectionState
import com.example.breathbeat.spirometer.SpirometerManager
import com.example.breathbeat.ui.theme.BreathBeatTheme

class MainActivity : ComponentActivity() {
    private lateinit var spirometerManager: SpirometerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val bluetoothConnectionManager = BluetoothConnectionManagerImpl(applicationContext)
        spirometerManager = SpirometerManager(bluetoothConnectionManager)

        enableEdgeToEdge()
        setContent {
            BreathBeatTheme {
                BreathBeatApp(spirometerManager)
            }
        }
    }
}

@Composable
fun BreathBeatApp(spirometerManager: SpirometerManager) {
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
                    AppDestinations.HOME -> HomeScreen(spirometerManager)
                    else -> Greeting(name = currentDestination.label)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(spirometerManager: SpirometerManager) {
    val context = LocalContext.current
    val connectionState by spirometerManager.bluetoothConnectionManager.connectionState.collectAsState()
    val volumeML by spirometerManager.volumeML.collectAsState()
    val scannedDevices by spirometerManager.bluetoothConnectionManager.scannedDevices.collectAsState()

    var hasPermissions by remember {
        mutableStateOf(checkBluetoothPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(getRequiredPermissions())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasPermissions) {
            Text(text = "Bluetooth permissions are required.")
            Button(onClick = { permissionLauncher.launch(getRequiredPermissions()) }) {
                Text("Authorize")
            }
        } else {
            Text(text = "Status: ${connectionState.name}", style = MaterialTheme.typography.titleMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "$volumeML", style = MaterialTheme.typography.displayLarge)
            Text(text = "ml", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(24.dp))

            when (connectionState) {
                BluetoothConnectionState.DISCONNECTED -> {
                    Button(onClick = { spirometerManager.bluetoothConnectionManager.startDiscovery() }) {
                        Text("Start Discovery")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Scanned Devices:", style = MaterialTheme.typography.titleSmall)
                    
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(scannedDevices.filter { it.name?.contains("ESP32") == true }) { device ->
                            DeviceItem(device = device) {
                                spirometerManager.connect(device)
                            }
                        }
                    }
                }
                BluetoothConnectionState.CONNECTED -> {
                    Button(onClick = { spirometerManager.disconnect() }) {
                        Text("Disconnect")
                    }
                }
                else -> {
                    Text("Connecting...")
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
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
