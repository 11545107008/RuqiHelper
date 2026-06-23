package com.example.ruqihelper.calculator

import com.example.ruqihelper.utils.Config

data class OrderInfo(
    val amount: Double,   // 閲戦锛堝厓锛?    val distance: Double, // 璺濈锛堝叕閲岋級
    val pricePerKm: Double // 姣忓叕閲屽崟浠?) {
    val isBigOrder: Boolean get() = pricePerKm >= Config.MIN_PRICE_PER_KM
    val isShortOrder: Boolean get() = distance <= Config.MAX_SHORT_DISTANCE
    val isGoodOrder: Boolean get() = isBigOrder || isShortOrder

    val reason: String get() = buildString {
        if (isBigOrder) append("澶у崟 ")
        if (isShortOrder) append("鐭崟")
    }
}

object PriceCalculator {
    fun calculate(amount: Double, distance: Double): OrderInfo? {
        if (amount <= 0 || distance <= 0) return null
        val pricePerKm = amount / distance
        return OrderInfo(amount, distance, pricePerKm)
    }
}
