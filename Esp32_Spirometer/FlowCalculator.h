#ifndef FLOW_CALCULATOR_H
#define FLOW_CALCULATOR_H

#include <Arduino.h>

class FlowCalculator {
private:
    float _A1, _A2;
    float _totalVolume; // 单位: L
    unsigned long _lastMillis;
    const float _rho = 1.225; // 空气密度
    const float _Cd = 0.96;   // 流量系数

public:
    FlowCalculator(float d1_mm, float d2_mm);
    float getPressureKpa(float v_out, float v_offset, float vs);
    void integrate(float kpa);
    void reset() { _totalVolume = 0; _lastMillis = millis(); }
    float getVolume() { return _totalVolume; }
    int getVolumeML() { return (int)lround(_totalVolume * 1000.0); }
};

#endif