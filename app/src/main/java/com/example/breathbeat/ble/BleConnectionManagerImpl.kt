package com.example.breathbeat.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleConnectionManagerImpl(private val context: Context) : BleConnectionManager {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _characteristicData = MutableSharedFlow<Pair<UUID, ByteArray>>(extraBufferCapacity = 10)
    override val characteristicData: SharedFlow<Pair<UUID, ByteArray>> = _characteristicData.asSharedFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device != null && device.name != null) {
                val currentList = _scannedDevices.value
                if (currentList.none { it.address == device.address }) {
                    _scannedDevices.value = currentList + device
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: List<ScanResult>) {
            val newDevices = results.mapNotNull { it.device }.filter { it.name != null }
            val currentList = _scannedDevices.value
            val updatedList = (currentList + newDevices).distinctBy { it.address }
            _scannedDevices.value = updatedList
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleConnectionManager", "Scan failed with error: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val state = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BleConnectionManager", "Connected to GATT server.")
                    gatt.discoverServices()
                    BleConnectionState.CONNECTED
                }
                BluetoothProfile.STATE_CONNECTING -> BleConnectionState.CONNECTING
                BluetoothProfile.STATE_DISCONNECTING -> BleConnectionState.DISCONNECTING
                else -> {
                    Log.i("BleConnectionManager", "Disconnected from GATT server.")
                    closeGatt()
                    BleConnectionState.DISCONNECTED
                }
            }
            _connectionState.value = state
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BleConnectionManager", "Services discovered")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            _characteristicData.tryEmit(characteristic.uuid to characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            _characteristicData.tryEmit(characteristic.uuid to value)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScan() {
        _scannedDevices.value = emptyList()
        bleScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        bleScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = BleConnectionState.CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        _connectionState.value = BleConnectionState.DISCONNECTING
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    override fun enableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(characteristicUuid)

        if (characteristic != null) {
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.let {
                @Suppress("DEPRECATION")
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeDescriptor(it)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
