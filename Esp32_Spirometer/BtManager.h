#ifndef BT_MANAGER_H
#define BT_MANAGER_H

#include "BluetoothSerial.h"

class BtManager {
private:
    BluetoothSerial _serialBT;

public:
    BtManager();
    void begin(String name);
    void sendData(int val);    // 用于推送毫升数
    void sendData(String msg); // 用于发送状态或最终结果
    bool isConnected();

    // 内部连接回调
    static void bluetoothCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param);
};

// 全局变量，用于跨类记录连接状态
extern bool g_btConnected;

#endif