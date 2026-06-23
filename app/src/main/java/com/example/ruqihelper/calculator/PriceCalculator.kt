package com.example.ruqihelper.calculator

import com.example.ruqihelper.utils.Config

data class OrderInfo(
    val amount: Double,   // 金额（元）
    val distance: Double, // 距离（公里）
    val pricePerKm: Double // 每公里单价
) {
    val isBigOrder: Boolean get() = pricePerKm >= Config.MIN_PRICE_PER_KM
    val isShortOrder: Boolean get() = distance <= Config.MAX_SHORT_DISTANCE
    val isGoodOrder: Boolean get() = isBigOrder || isShortOrder

    val reason: String get() = buildString {
        if (isBigOrder) append("大单 ")
        if (isShortOrder) append("短单")
    }
}

object PriceCalculator {
    fun calculate(amount: Double, distance: Double): OrderInfo? {
        if (amount <= 0 || distance <= 0) return null
        val pricePerKm = amount / distance
        return OrderInfo(amount, distance, pricePerKm)
    }
}
