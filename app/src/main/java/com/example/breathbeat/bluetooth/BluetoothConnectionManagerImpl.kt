package com.example.breathbeat.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnectionManagerImpl(private val context: Context) : BluetoothConnectionManager {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _dataStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    override val dataStream: SharedFlow<ByteArray> = _dataStream.asSharedFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()

    private var connectJob: Job? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val currentList = _scannedDevices.value
                        if (currentList.none { it.address == device.address }) {
                            _scannedDevices.value = currentList + device
                        }
                    }
                }
            }
        }
    }

    override fun startDiscovery() {
        _scannedDevices.value = emptyList()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        @SuppressLint("MissingPermission")
        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        @SuppressLint("MissingPermission")
        bluetoothAdapter?.cancelDiscovery()
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BluetoothDevice) {
        stopDiscovery()
        connectJob?.cancel()
        connectJob = scope.launch {
            _connectionState.value = BluetoothConnectionState.CONNECTING
            try {
                // Standard SerialPortService ID
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                _connectionState.value = BluetoothConnectionState.CONNECTED
                listenForData()
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Connection failed", e)
                _connectionState.value = BluetoothConnectionState.DISCONNECTED
                closeSocket()
            }
        }
    }

    override fun disconnect() {
        _connectionState.value = BluetoothConnectionState.DISCONNECTING
        connectJob?.cancel()
        closeSocket()
        _connectionState.value = BluetoothConnectionState.DISCONNECTED
    }

    override fun sendData(data: ByteArray) {
        scope.launch {
            try {
                bluetoothSocket?.outputStream?.write(data)
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Send failed", e)
            }
        }
    }

    private fun listenForData() {
        val inputStream = bluetoothSocket?.inputStream
        val buffer = ByteArray(1024)

        while (_connectionState.value == BluetoothConnectionState.CONNECTED) {
            try {
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead > 0) {
                    val data = buffer.copyOfRange(0, bytesRead)
                    _dataStream.tryEmit(data)
                } else if (bytesRead == -1) {
                    throw IOException("InputStream reached end of stream")
                }
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Connection lost", e)
                disconnect()
                break
            }
        }
    }

    private fun closeSocket() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Could not close socket", e)
        }
        bluetoothSocket = null
    }
}
