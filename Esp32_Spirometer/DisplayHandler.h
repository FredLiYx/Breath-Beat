#ifndef DISPLAY_HANDLER_H
#define DISPLAY_HANDLER_H

#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Wire.h>

class DisplayHandler {
private:
    Adafruit_SSD1306 _display;
    const int _screenWidth = 128;
    const int _screenHeight = 64;

public:
    // 初始化 OLED，使用 I2C 接口
    DisplayHandler();
    void begin();
    
    // 清屏并显示特定的毫升数
    void updateVolume(int ml);
    
    // 显示系统状态（如“等待中”、“吹气中”）
    void showStatus(const char* status);
};

#endif