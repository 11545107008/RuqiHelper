package com.example.ruqihelper.utils

/**
 * 配置类 - 可调整的参数
 */
object Config {
    /** 最低单价阈值（元/公里），超过此值才提醒 */
    const val MIN_PRICE_PER_KM = 1.5

    /** 短单最大距离（公里），2公里以内的单子优先提醒 */
    const val MAX_SHORT_DISTANCE = 2.0

    /** 是否启用短单提醒（2公里以内） */
    const val ENABLE_SHORT_DISTANCE_ALERT = true

    /** 如祺司机端包名 */
    const val RUQI_PACKAGE_NAME = "com.ruqi.driver"

    /** 震动提醒时长（毫秒） */
    const val VIBRATE_DURATION = 500L

    /** 悬浮窗显示时长（毫秒），0表示一直显示 */
    const val FLOATING_WINDOW_DURATION = 5000L

    /** 是否启用自动点击（风险高） */
    const val ENABLE_AUTO_CLICK = false

    /** 订单关键词 - 金额 */
    const val KEYWORD_PRICE = "预估"

    /** 订单关键词 - 距离 */
    const val KEYWORD_DISTANCE = "公里"
}
