package com.example.ruqihelper.utils

object Config {
    // 大单判断：每公里单价 >= 此值
    const val MIN_PRICE_PER_KM = 1.5

    // 短单判断：距离 <= 此值（公里）
    const val MAX_SHORT_DISTANCE = 2.0

    // 无障碍服务是否激活
    var isServiceRunning = false
}
