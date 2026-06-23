package com.example.ruqihelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.ruqihelper.calculator.OrderInfo
import com.example.ruqihelper.calculator.PriceCalculator
import com.example.ruqihelper.ui.FloatingWindow
import com.example.ruqihelper.utils.Config

class OrderAccessibilityService : AccessibilityService() {

    private lateinit var floatingWindow: FloatingWindow
    private var lastAlertTime = 0L
    private val alertCooldown = 3000L
    private var lastDebugToastTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Config.isServiceRunning = true
        floatingWindow = FloatingWindow(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 200
        }
        serviceInfo = info

        Toast.makeText(this, "如祺好单助手已启动，正在监听...", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()

        // 调试：记录包名和事件类型
        val pkg = event.packageName?.toString() ?: "unknown"
        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFY"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT"
            else -> "OTHER"
        }

        // 先处理通知事件（如祺可能通过通知发单）
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            handleNotificationEvent(event, now)
            return
        }

        // 窗口事件：扫描所有可见文字
        if (now - lastAlertTime < alertCooldown) return

        val rootNode = rootInActiveWindow ?: return
        try {
            // 收集所有文本用于调试
            val allTexts = mutableListOf<String>()
            collectTexts(rootNode, allTexts)

            // 更新调试信息
            Config.debugPackage = pkg
            Config.debugTexts = allTexts.take(20).joinToString(" | ")
            Config.debugTime = now

            // 调试Toast（每30秒一次）
            if (now - lastDebugToastTime > 30000 && allTexts.isNotEmpty()) {
                lastDebugToastTime = now
                val preview = allTexts.take(3).joinToString(" | ").take(80)
                Toast.makeText(this, "[$pkg] $preview", Toast.LENGTH_SHORT).show()
            }

            // 解析订单
            val order = parseOrderFromTexts(allTexts, rootNode)
            if (order != null && order.isGoodOrder) {
                lastAlertTime = now
                val title = when {
                    order.isBigOrder && order.isShortOrder -> "超级好单!"
                    order.isBigOrder -> "大单来了!"
                    else -> "短单提醒"
                }
                val msg = String.format("¥%.2f / %.1fkm\n¥%.2f/km",
                    order.amount, order.distance, order.pricePerKm)
                floatingWindow.show(title, msg, order.isBigOrder, order.isShortOrder)
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun handleNotificationEvent(event: AccessibilityEvent, now: Long) {
        // 扫描通知内容
        val texts = mutableListOf<String>()
        for (i in 0 until event.recordCount) {
            val record = event.text[i]?.toString() ?: continue
            if (record.isNotBlank()) texts.add(record)
        }

        if (texts.isEmpty()) return

        Config.debugPackage = "notif:${event.packageName}"
        Config.debugTexts = texts.joinToString(" | ")
        Config.debugTime = now

        val order = parseOrderFromTexts(texts, null)
        if (order != null && order.isGoodOrder) {
            lastAlertTime = now
            val title = when {
                order.isBigOrder && order.isShortOrder -> "超级好单!"
                order.isBigOrder -> "大单来了!"
                else -> "短单提醒"
            }
            val msg = String.format("¥%.2f / %.1fkm\n¥%.2f/km",
                order.amount, order.distance, order.pricePerKm)
            floatingWindow.show(title, msg, order.isBigOrder, order.isShortOrder)
        }
    }

    private fun parseOrderFromTexts(texts: List<String>, rootNode: AccessibilityNodeInfo?): OrderInfo? {
        var amount: Double? = null
        var distance: Double? = null

        // 策略1：文本正则匹配（更灵活的格式）
        for (text in texts) {
            val cleaned = text.trim()
            if (amount == null) {
                // 支持: ¥12.5 / 12.5元 / 12.5 / 预估12.5 / 金额12.5
                val amountRegex = Regex("""(?:[¥￥]|预估|金额|价格|费用|收入)?\s*(\d+\.?\d*)\s*(?:元|块|¥|￥)?""")
                amountRegex.find(cleaned)?.let { m ->
                    val v = m.groupValues[1]
                    v.toDoubleOrNull()?.let { if (it >= 0.5) amount = it }
                }
            }
            if (distance == null) {
                // 支持: 2.5公里 / 2.5km / 2.5千米 / 距离2.5 / 2.5公里
                val distRegex = Regex("""(?:距离|里程)?\s*(\d+\.?\d*)\s*(?:公里|千米|km)""", RegexOption.IGNORE_CASE)
                distRegex.find(cleaned)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it in 0.1..200.0) distance = it }
                }
            }
        }

        // 策略2：View ID匹配（如祺APP常用ID）
        if ((amount == null || distance == null) && rootNode != null) {
            if (amount == null) {
                val priceViewIds = listOf(
                    "com.ruqi.driver:id/tv_price",
                    "com.ruqi.driver:id/tv_amount",
                    "com.ruqi.driver:id/tv_income",
                    "com.ruqi.driver:id/tv_money",
                    "com.ry.ryapp:id/tv_price",
                    "com.ry.ryapp:id/tv_amount"
                )
                for (id in priceViewIds) {
                    rootNode.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { node ->
                        val t = node.text?.toString() ?: ""
                        extractNumber(t).let { if (it > 0) amount = it }
                        node.recycle()
                    }
                    if (amount != null) break
                }
            }
            if (distance == null) {
                val distViewIds = listOf(
                    "com.ruqi.driver:id/tv_distance",
                    "com.ruqi.driver:id/tv_mileage",
                    "com.ry.ryapp:id/tv_distance",
                    "com.ry.ryapp:id/tv_mileage"
                )
                for (id in distViewIds) {
                    rootNode.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { node ->
                        val t = node.text?.toString() ?: ""
                        extractNumber(t).let { if (it > 0) distance = it }
                        node.recycle()
                    }
                    if (distance != null) break
                }
            }
        }

        return if (amount != null && distance != null) {
            PriceCalculator.calculate(amount, distance)
        } else null
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it.trim()) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, texts) }
        }
    }

    private fun extractNumber(text: String): Double {
        return Regex("""(\d+\.?\d*)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    override fun onInterrupt() {
        Config.isServiceRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Config.isServiceRunning = false
        floatingWindow.hide()
    }
}
