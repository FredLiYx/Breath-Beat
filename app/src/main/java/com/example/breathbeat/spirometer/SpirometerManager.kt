package com.example.breathbeat.spirometer

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.example.breathbeat.bluetooth.BluetoothConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _isBlowing = MutableStateFlow(false)
    val isBlowing: StateFlow<Boolean> = _isBlowing.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val dataBuffer = mutableListOf<Byte>()
    private var stopBlowingJob: Job? = null

    init {
        scope.launch {
            bluetoothConnectionManager.dataStream.collect { bytes ->
                dataBuffer.addAll(bytes.toList())
                while (dataBuffer.size >= 4) {
                    val volume = ByteBuffer.wrap(dataBuffer.take(4).toByteArray())
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                    
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
        _isBlowing.value = false
        dataBuffer.clear()
    }

    private fun processVolume(volume: Int) {
        if (volume > 0) {
            _isBlowing.value = true
            stopBlowingJob?.cancel()
            stopBlowingJob = scope.launch {
                delay(2000) // Consider stopped after 2 seconds of no/zero data
                _isBlowing.value = false
            }
        }
        _volumeML.value = volume
    }
}
