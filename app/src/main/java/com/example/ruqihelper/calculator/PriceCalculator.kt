package com.example.ruqihelper.calculator

import java.util.regex.Pattern

/**
 * 订单单价计算器
 */
object PriceCalculator {

    /**
     * 计算每公里单价
     * @param price 订单金额（元）
     * @param distance 订单距离（公里）
     * @return 每公里单价（元/公里），如果计算失败返回 -1
     */
    fun calculatePricePerKm(price: Double, distance: Double): Double {
        if (distance <= 0) return -1.0
        return price / distance
    }

    /**
     * 从文本中提取金额
     * @param text 包含金额的文本，如 "预估金额: 45.5元" 或 "¥45.5" 或 "45.5"
     * @return 提取到的金额，如果未找到返回 -1.0
     */
    fun extractPrice(text: String): Double {
        // 匹配多种金额模式
        val patterns = listOf(
            "(\\d+\\.?\\d*)\\s*元",           // 45.5元
            "¥\\s*(\\d+\\.?\\d*)",              // ¥45.5
            "(\\d+\\.?\\d*)\\s*块",             // 45.5块
            "价格[：:]\\s*(\\d+\\.?\\d*)",      // 价格：45.5
            "金额[：:]\\s*(\\d+\\.?\\d*)",      // 金额：45.5
            "预估[：:]\\s*(\\d+\\.?\\d*)"       // 预估：45.5
        )
        
        for (patternStr in patterns) {
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.toDoubleOrNull() ?: -1.0
            }
        }
        return -1.0
    }

    /**
     * 从文本中提取距离
     * @param text 包含距离的文本，如 "距离: 12.3公里" 或 "12.3km"
     * @return 提取到的距离（公里），如果未找到返回 -1.0
     */
    fun extractDistance(text: String): Double {
        // 匹配多种距离模式
        val patterns = listOf(
            "(\\d+\\.?\\d*)\\s*公里",          // 12.3公里
            "(\\d+\\.?\\d*)\\s*km",            // 12.3km
            "距离[：:]\\s*(\\d+\\.?\\d*)",     // 距离：12.3
            "(\\d+\\.?\\d*)\\s*米"              // 1234米 → 需要转换为公里
        )
        
        for (patternStr in patterns) {
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val value = matcher.group(1)?.toDoubleOrNull() ?: -1.0
                // 如果是"米"，转换为公里
                return if (text.contains("米") && !text.contains("公里")) {
                    value / 1000.0
                } else {
                    value
                }
            }
        }
        return -1.0
    }

    /**
     * 判断是否为好单
     * @param pricePerKm 每公里单价
     * @param threshold 阈值（元/公里）
     * @return true 如果是好单
     */
    fun isGoodOrder(pricePerKm: Double, threshold: Double = 1.5): Boolean {
        return pricePerKm >= threshold
    }

    /**
     * 判断是否为短单（2公里以内）
     * @param distance 订单距离（公里）
     * @param maxDistance 最大距离阈值（公里）
     * @return true 如果是短单
     */
    fun isShortDistance(distance: Double, maxDistance: Double = 2.0): Boolean {
        return distance > 0 && distance <= maxDistance
    }
}
