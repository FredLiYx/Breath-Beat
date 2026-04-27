#include "FlowCalculator.h"

FlowCalculator::FlowCalculator(float d1, float d2) : _totalVolume(0) {
    _A1 = PI * pow(d1 / 2000.0, 2); // mm 转 m 再求面积
    _A2 = PI * pow(d2 / 2000.0, 2);
}

float FlowCalculator::getPressureKpa(float v_out, float v_offset, float vs) {
    // 根据文档公式推导: P = (Vout/Vs - 0.5) / 0.2
    // 补偿零点漂移误差
    float residual = v_offset - (vs * 0.5);
    float p = ((v_out - residual) / vs - 0.5) / 0.2;
    return p; // 范围 -2.0 到 2.0 kPa [cite: 12, 44]
}

void FlowCalculator::integrate(float kpa) {
    if (kpa < 0.02) return; // 忽略微小噪声
    
    // 伯努利原理计算流速
    float velocity = _Cd * sqrt((2.0 * kpa * 1000.0) / (_rho * (1.0 - pow(_A2/_A1, 2))));
    float flowRate_Lps = (velocity * _A2) * 1000.0; // m3/s 转 L/s

    unsigned long now = millis();
    float dt = (now - _lastMillis) / 1000.0;
    if (dt > 0 && dt < 0.5) _totalVolume += flowRate_Lps * dt;
    _lastMillis = now;
}