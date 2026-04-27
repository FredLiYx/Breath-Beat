package com.example.breathbeat.bluetooth

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothConnectionManager {
    val connectionState: StateFlow<BluetoothConnectionState>
    val dataStream: SharedFlow<ByteArray>
    val scannedDevices: StateFlow<List<BluetoothDevice>>
    
    fun startDiscovery()
    fun stopDiscovery()
    fun connect(device: BluetoothDevice)
    fun disconnect()
    fun sendData(data: ByteArray)
}

enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}
