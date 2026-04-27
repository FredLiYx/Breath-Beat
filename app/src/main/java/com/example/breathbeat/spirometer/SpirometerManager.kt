package com.example.breathbeat.spirometer

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.example.breathbeat.bluetooth.BluetoothConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpirometerManager(
    val bluetoothConnectionManager: BluetoothConnectionManager
) {
    private val _volumeML = MutableStateFlow(0)
    val volumeML: StateFlow<Int> = _volumeML.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val dataBuffer = mutableListOf<Byte>()

    init {
        scope.launch {
            // Fix: Use the variable bluetoothConnectionManager, not the interface name
            bluetoothConnectionManager.dataStream.collect { bytes ->
                // Print raw bytes to logcat
                Log.d("SpirometerManager", "Raw bytes received: ${bytes.joinToString(", ") { "0x%02X".format(it) }}")
                
                // Reassemble into Int (Little Endian)
                dataBuffer.addAll(bytes.toList())
                while (dataBuffer.size >= 4) {
                    val volume = ByteBuffer.wrap(dataBuffer.take(4).toByteArray())
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                    
                    // Pass it through to the function
                    processVolume(volume)
                    
                    repeat(4) { dataBuffer.removeAt(0) }
                }
                
                if (dataBuffer.size > 1024) dataBuffer.clear()
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        bluetoothConnectionManager.connect(device)
    }

    fun disconnect() {
        bluetoothConnectionManager.disconnect()
        _volumeML.value = 0
        dataBuffer.clear()
    }

    private fun processVolume(volume: Int) {
        // Log and pass it to the UI state
        Log.d("SpirometerManager", "Data: $volume ml")
        _volumeML.value = volume
    }
}
