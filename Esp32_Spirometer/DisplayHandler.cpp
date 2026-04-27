#include "DisplayHandler.h"

// 构造函数：设置屏幕尺寸和重置引脚
DisplayHandler::DisplayHandler() 
    : _display(128, 64, &Wire, -1) {}

void DisplayHandler::begin() {
    // 初始化 I2C，默认使用 ESP32 的 SDA(21), SCL(22)
    if(!_display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
        Serial.println(F("OLED 启动失败"));
        return;
    }
    _display.clearDisplay();
    _display.setTextColor(SSD1306_WHITE);
    _display.display();
}

void DisplayHandler::updateVolume(int ml) {
    _display.clearDisplay();
    
    // 设置“ml”文字的大小和位置
    _display.setTextSize(2);
    _display.setCursor(100, 30);
    _display.print("ml");

    // 设置数字的大小和位置（大号字体突出显示）
    _display.setTextSize(4);
    _display.setCursor(0, 20);
    _display.print(ml);

    _display.display();
}

void DisplayHandler::showStatus(const char* status) {
    _display.clearDisplay();
    _display.setTextSize(1);
    _display.setCursor(0, 0);
    _display.print(status);
    _display.display();
}