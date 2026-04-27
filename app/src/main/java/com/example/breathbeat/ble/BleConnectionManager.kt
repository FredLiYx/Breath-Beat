package com.example.breathbeat.ble

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface BleConnectionManager {
    val connectionState: StateFlow<BleConnectionState>
    val characteristicData: SharedFlow<Pair<UUID, ByteArray>>
    val scannedDevices: StateFlow<List<BluetoothDevice>>
    
    fun startScan()
    fun stopScan()
    fun connect(device: BluetoothDevice)
    fun disconnect()
    fun enableNotifications(serviceUuid: UUID, characteristicUuid: UUID)
}

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}
