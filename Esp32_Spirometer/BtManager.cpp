#include "BtManager.h"

bool g_btConnected = false;

BtManager::BtManager() {}

void BtManager::bluetoothCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param) {
    if (event == ESP_SPP_SRV_OPEN_EVT) {
        Serial.println(">>> 经典蓝牙：手机已连接");
        g_btConnected = true;
    } else if (event == ESP_SPP_CLOSE_EVT) {
        Serial.println(">>> 经典蓝牙：连接已断开");
        g_btConnected = false;
    }
}

void BtManager::begin(String name) {
    _serialBT.register_callback(bluetoothCallback);
    if (!_serialBT.begin(name)) {
        Serial.println("经典蓝牙初始化失败！");
    } else {
        Serial.println("经典蓝牙已就绪，设备名: " + name);
    }
}

bool BtManager::isConnected() {
    return g_btConnected;
}

void BtManager::sendData(int val) {
    if (g_btConnected) {
        // 发送 4 字节原始内存，不带任何格式转换
        _serialBT.write((uint8_t*)&val, 4); 
    }
}

void BtManager::sendData(String msg) {
    if (g_btConnected) {
        _serialBT.println(msg);
    }
}