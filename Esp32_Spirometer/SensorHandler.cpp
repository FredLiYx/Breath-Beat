#include "SensorHandler.h"

SensorHandler::SensorHandler(uint8_t dataPin, uint8_t refPin) 
    : _dataPin(dataPin), _refPin(refPin), _offsetVoltage(0), _currentVs(5.0) {}

void SensorHandler::updateSupplyVoltage() {
  int raw = analogRead(_refPin);
  float measuredVs = (raw / 4095.0) * 3.3 * _refDividerRatio;

  // --- 安全守护逻辑 ---
  // 正常手机供电应在 4.5V-5.25V 之间。如果测得低于 3.0V，判定为硬件接触不良或干扰。
  if (measuredVs > 3.0) {
    // 使用一阶滤波让电压变化更平滑，防止跳变
    _currentVs = (_currentVs * 0.9) + (measuredVs * 0.1); 
    // // --- 每5秒打印一次 vs 进行调试 ---
    // static unsigned long lastPrintTime = 0;
    // if (millis() - lastPrintTime >= 5000) {
    //     Serial.print("调试信息 - 当前vs: ");
    //     Serial.println(_currentVs, 4); // 保留4位小数
    //     lastPrintTime = millis();
    // }
    // // -------------------------------
  } else {
    // 如果读数异常（如 0），保持旧值不变，不更新 _currentVs
    // 这样可以防止分母变为 0 导致的 inf
  }
}
/*
void SensorHandler::updateSupplyVoltage() {
    int raw = analogRead(_refPin);
    // 实时计算当前的 Vs [cite: 44, 46]
    _currentVs = (raw / 4095.0) * 3.3 * _refDividerRatio;
}
*/
void SensorHandler::autoZero() {
    updateSupplyVoltage();
    long sum = 0;
    for (int i = 0; i < 100; i++) {
        sum += analogRead(_dataPin);
        delay(5);
    }
    // 存储初始零点电压 [cite: 47, 63]
    _offsetVoltage = (sum / 100.0 / 4095.0) * 3.3 * _dataDividerRatio;
}

float SensorHandler::getRawVoltage() {
    long sum = 0;
    for (int i = 0; i < 20; i++) sum += analogRead(_dataPin);
    return (sum / 20.0 / 4095.0) * 3.3 * _dataDividerRatio;
}