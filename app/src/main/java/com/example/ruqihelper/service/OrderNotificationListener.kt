package com.example.ruqihelper.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.ruqihelper.calculator.PriceCalculator
import com.example.ruqihelper.ui.FloatingWindow
import com.example.ruqihelper.utils.Config

class OrderNotificationListener : NotificationListenerService() {

    private var floatingWindow: FloatingWindow? = null

    companion object {
        private const val TAG = "RuqiNoti"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Config.isNotificationRunning = true
        Log.i(TAG, "通知监听已连接")
    }

    override fun onListenerDisconnected() {
        Config.isNotificationRunning = false
        super.onListenerDisconnected()
        Log.i(TAG, "通知监听已断开")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try { handleNotification(sbn) } catch (e: Exception) {
            Log.e(TAG, "handleNotification error", e)
            Config.lastError = "Noti: ${e.javaClass.simpleName}: ${e.message}"
            Config.errorTime = System.currentTimeMillis()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 不需要处理移除
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val title = sbn.notification.extras.getString("android.title") ?: ""
        val text = sbn.notification.extras.getString("android.text") ?: ""
        val bigText = sbn.notification.extras.getString("android.bigText") ?: ""
        val subText = sbn.notification.extras.getString("android.subText") ?: ""
        val summary = sbn.notification.extras.getString("android.summaryText") ?: ""
        val ticker = sbn.notification.tickerText?.toString() ?: ""

        val allText = listOf(title, text, bigText, subText, summary, ticker)
            .filter { it.isNotBlank() }

        if (allText.isEmpty()) return

        // 更新调试信息
        val now = System.currentTimeMillis()
        Config.notiPackage = pkg
        Config.notiTitle = title
        Config.notiText = text.ifEmpty { bigText.ifEmpty { subText } }
        Config.notiTime = now
        Config.notiCount++

        Log.i(TAG, "通知 [$pkg] title=$title text=$text bigText=$bigText subText=$subText")

        // 仅处理如祺的通知
        if (pkg != Config.TARGET_PACKAGE) return

        // 解析订单信息
        val order = parseOrderFromNotification(allText)
        if (order == null) {
            Log.i(TAG, "通知不含订单数据: $allText")
            return
        }
        if (!order.isGoodOrder) {
            Log.i(TAG, "不是好单: ${order.amount}元 ${order.distance}km")
            return
        }
        if (now - Config.lastAlertTime < Config.ALERT_COOLDOWN) {
            Log.i(TAG, "冷却中，跳过")
            return
        }

        Config.lastAlertTime = now

        // 弹出悬浮窗
        val titleStr = when {
            order.isBigOrder && order.isShortOrder -> "超级好单!"
            order.isBigOrder -> "大单来了!"
            else -> "短单提醒"
        }
        val msg = String.format("%.2f元 / %.1f公里\n%.2f元/公里",
            order.amount, order.distance, order.pricePerKm)

        if (floatingWindow == null) {
            floatingWindow = FloatingWindow(this)
        }
        floatingWindow?.show(titleStr, msg, order.isBigOrder, order.isShortOrder)

        Log.i(TAG, "好单弹窗! ${order.amount}元 ${order.distance}km ${order.pricePerKm}元/km")

        // 也同步给无障碍那边的 debug
        if (Config.debugTexts.isEmpty()) {
            Config.debugTexts = "通知: $allText"
            Config.debugTime = now
        }
    }

    private fun parseOrderFromNotification(texts: List<String>): com.example.ruqihelper.calculator.OrderInfo? {
        var amount: Double? = null
        var distance: Double? = null

        for (text in texts) {
            val cleaned = text.trim()
            if (cleaned.isEmpty()) continue

            if (amount == null) {
                amount = extractAmount(cleaned)
            }
            if (distance == null) {
                distance = extractDistance(cleaned)
            }
        }

        if (amount != null && distance != null) {
            return PriceCalculator.calculate(amount, distance)
        }
        return null
    }

    private fun extractAmount(text: String): Double? {
        // "¥7.90" "7.90元" "预估计价：7.90元"
        val patterns = listOf(
            Regex("""[¥￥]\s*(\d+\.?\d+)"""),
            Regex("""(\d+\.?\d+)\s*元"""),
            Regex("""(?:预估计价|预估|金额|价格|计费|费用|收入)?[：:]\s*(\d+\.?\d+)\s*元?""")
        )
        for (r in patterns) {
            r.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                if (it >= 0.5) return it
            }
        }
        return null
    }

    private fun extractDistance(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:全程|里程|距离)[：:]\s*(\d+\.?\d+)\s*(?:公里|千米|km)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+\.?\d+)\s*公里"""),
            Regex("""(\d+\.?\d+)\s*(?:km|KM)""")
        )
        for (r in patterns) {
            r.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                if (it in 0.1..200.0) return it
            }
        }
        return null
    }
}
