#include "SensorHandler.h"
#include "FlowCalculator.h"
#include "BtManager.h"      // 1. 换成经典蓝牙头文件
#include "DisplayHandler.h"

SensorHandler sensor(34, 35);
FlowCalculator calc(10.0, 4.0); 
DisplayHandler display;
BtManager bt;               // 2. 实例化经典蓝牙管理类

enum DeviceState { IDLE, RECORDING, SENDING, DISCONNECTED };
DeviceState state = IDLE;
unsigned long silenceStart = 0;
unsigned long lastNotifyTime = 0; 

void setup() {
    Serial.begin(115200);
    sensor.autoZero(); 
    
    // 3. 初始化经典蓝牙，设置设备名
    bt.begin("ESP32_Spirometer_Classic"); 
    
    display.begin();        
    display.showStatus("BT Ready"); 
    Serial.println("系统就绪，请在手机设置中配对并等待连接...");
}

void loop() {
    sensor.updateSupplyVoltage();
    float v = sensor.getRawVoltage();
    float p = calc.getPressureKpa(v, sensor.getOffset(), sensor.getVs());

    // 检查连接状态并在屏幕显示（可选）
    static bool lastConnState = false;
    if (bt.isConnected() != lastConnState) {
        lastConnState = bt.isConnected();
        display.showStatus(lastConnState ? "Connected" : "Disconnected");
    }

    switch (state) {
        case IDLE:
            if (p > 0.1) {
                calc.reset();
                state = RECORDING;
                lastNotifyTime = millis(); 
                Serial.println(">>> 准备吹气...");
            }
            break;

        case RECORDING:
            calc.integrate(p);
            
            // 每 0.5s 推送一次数据
            if (millis() - lastNotifyTime >= 500) {
                int currentML = calc.getVolumeML();
                display.updateVolume(currentML); 
                
                // 4. 使用经典蓝牙发送数据
                bt.sendData(currentML);
                
                Serial.printf("实时气量: %d ml\n", currentML);
                lastNotifyTime = millis();
            }

            if (p < 0.05) {
                if (silenceStart == 0) silenceStart = millis();
                if (millis() - silenceStart > 800) state = SENDING;
            } else {
                silenceStart = 0;
            }
            break;

        case SENDING:
            int finalML = calc.getVolumeML();
            display.updateVolume(finalML);   
            
            // 5. 使用经典蓝牙发送最终结果
            bt.sendData(finalML); 
            
            Serial.printf("测量结束！最终总量: %d ml\n", finalML);
            
            state = IDLE;
            silenceStart = 0;
            delay(2000); 
            break;
    }

    delay(20); 
}