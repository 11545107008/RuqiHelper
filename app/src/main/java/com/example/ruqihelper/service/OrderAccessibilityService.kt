package com.example.ruqihelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ruqihelper.calculator.OrderInfo
import com.example.ruqihelper.calculator.PriceCalculator
import com.example.ruqihelper.ui.FloatingWindow
import com.example.ruqihelper.utils.Config

class OrderAccessibilityService : AccessibilityService() {

    private lateinit var floatingWindow: FloatingWindow
    private var lastAlertTime = 0L
    private val alertCooldown = 3000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Config.isServiceRunning = true
        floatingWindow = FloatingWindow(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 300
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < alertCooldown) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val order = parseOrderFromNode(rootNode)
            if (order != null && order.isGoodOrder) {
                lastAlertTime = now
                val title = when {
                    order.isBigOrder && order.isShortOrder -> "超级好单!"
                    order.isBigOrder -> "大单来了!"
                    else -> "短单提醒"
                }
                val msg = String.format("%.2f / %.1fkm = %.2f/km", order.amount, order.distance, order.pricePerKm)
                floatingWindow.show(title, msg, order.isBigOrder, order.isShortOrder)
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun parseOrderFromNode(root: AccessibilityNodeInfo): OrderInfo? {
        var amount: Double? = null
        var distance: Double? = null

        val texts = mutableListOf<String>()
        collectTexts(root, texts)

        for (text in texts) {
            if (amount == null) {
                val amountRegex = Regex("""[¥￥]\s*(\d+\.?\d*)|(\d+\.?\d*)\s*元""")
                amountRegex.find(text)?.let { m ->
                    val v = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
                    v?.toDoubleOrNull()?.let { if (it > 0) amount = it }
                }
            }
            if (distance == null) {
                val distRegex = Regex("""(\d+\.?\d*)\s*(?:公里|km|千米)""", RegexOption.IGNORE_CASE)
                distRegex.find(text)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it > 0) distance = it }
                }
            }
        }

        if (amount == null || distance == null) {
            val priceIds = listOf("com.ruqi.driver:id/tv_price", "com.ruqi.driver:id/tv_amount")
            for (id in priceIds) {
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { node ->
                    extractNumber(node.text?.toString() ?: "").let { if (it > 0 && amount == null) amount = it }
                    node.recycle()
                }
            }
            val distIds = listOf("com.ruqi.driver:id/tv_distance", "com.ruqi.driver:id/tv_mileage")
            for (id in distIds) {
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { node ->
                    extractNumber(node.text?.toString() ?: "").let { if (it > 0 && distance == null) distance = it }
                    node.recycle()
                }
            }
        }

        return if (amount != null && distance != null) PriceCalculator.calculate(amount!!, distance!!) else null
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
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
