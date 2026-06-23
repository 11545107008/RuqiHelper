package com.example.ruqihelper.utils

object Config {
    // 大单判断：每公里单价 >= 此值
    const val MIN_PRICE_PER_KM = 1.5

    // 短单判断：距离 <= 此值（公里）
    const val MAX_SHORT_DISTANCE = 2.0

    // 如祺车主包名
    const val TARGET_PACKAGE = "com.ruqi.driver"

    // 无障碍服务是否激活
    var isServiceRunning = false

    // 通知监听是否激活
    var isNotificationRunning = false

    // 共享冷却（防止 Accessibility 和 Notification 同时弹窗）
    @Volatile var lastAlertTime = 0L
    const val ALERT_COOLDOWN = 3000L

    // ===== 无障碍调试 =====
    var debugPackage = ""
    var debugTexts = ""
    var debugTime = 0L
    var debugEventCount = 0L

    // ===== 通知调试 =====
    var notiPackage = ""
    var notiTitle = ""
    var notiText = ""
    var notiTime = 0L
    var notiCount = 0L

    // 最后错误信息
    var lastError = ""
    var errorTime = 0L
}
