#ifndef SENSOR_HANDLER_H
#define SENSOR_HANDLER_H

#include <Arduino.h>

class SensorHandler {
private:
    uint8_t _dataPin, _refPin;
    float _offsetVoltage;
    float _currentVs;
    const float _refDividerRatio = 2.0;    // 10k/10k 分压
    const float _dataDividerRatio = 1.606; // (2k+3.3k)/3.3k 分压

public:
    SensorHandler(uint8_t dataPin, uint8_t refPin);
    void updateSupplyVoltage();            // 实时更新 Vs
    void autoZero();                       // 启动归零
    float getRawVoltage();                 // 获取当前传感器电压（已补偿分压）
    float getVs() { return _currentVs; }
    float getOffset() { return _offsetVoltage; }
};

#endif