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
    private val alertCooldown = 3000L // 3绉掑喎鍗达紝闃叉閲嶅鎻愰啋

    override fun onServiceConnected() {
        super.onServiceConnected()
        Config.isServiceRunning = true
        floatingWindow = FloatingWindow(this)

        // 閰嶇疆鐩戝惉锛氱洃鍚墍鏈夊簲鐢ㄧ獥鍙ｅ彉鍖?        val info = AccessibilityServiceInfo().apply {
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

        // 闄嶆俯锛氶槻姝㈤绻佸鐞?        val now = System.currentTimeMillis()
        if (now - lastAlertTime < alertCooldown) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val order = parseOrderFromNode(rootNode)
            if (order != null && order.isGoodOrder) {
                lastAlertTime = now
                val title = if (order.isBigOrder && order.isShortOrder) "馃敟 瓒呯骇濂藉崟锛?
                           else if (order.isBigOrder) "馃挵 澶у崟鏉ヤ簡锛?
                           else "馃搷 鐭崟鎻愰啋"

                val msg = "楼%.2f / %.1f鍏噷 = 楼%.2f/鍏噷".format(
                    order.amount, order.distance, order.pricePerKm
                )
                floatingWindow.show(title, msg, order.isBigOrder, order.isShortOrder)
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 浠庣晫闈㈣妭鐐规爲涓彁鍙栬鍗曚俊鎭?     * 閫傞厤濡傜ズ杞︿富APP鐨刄I缁撴瀯
     */
    private fun parseOrderFromNode(root: AccessibilityNodeInfo): OrderInfo? {
        var amount: Double? = null
        var distance: Double? = null

        // 鏂规硶1: 閫氳繃鏂囨湰鍐呭璇嗗埆閲戦锛堝寘鍚?楼"鎴?鍏?鐨勬枃鏈級
        extractByTextSearch(root)?.let { pair ->
            amount = pair.first
            distance = pair.second
        }

        // 鏂规硶2: 濡傛灉鏂规硶1澶辫触锛岄€氳繃View ID鎼滅储锛堝绁虹壒鏈塈D锛?        if (amount == null || distance == null) {
            extractByViewId(root)?.let { pair ->
                if (amount == null) amount = pair.first
                if (distance == null) distance = pair.second
            }
        }

        if (amount != null && distance != null) {
            return PriceCalculator.calculate(amount, distance)
        }
        return null
    }

    /**
     * 鏂规硶1: 鍏ㄦ枃鏈悳绱㈤噾棰濆拰璺濈
     */
    private fun extractByTextSearch(root: AccessibilityNodeInfo): Pair<Double, Double>? {
        val texts = mutableListOf<String>()
        collectTexts(root, texts)

        var amount: Double? = null
        var distance: Double? = null

        for (text in texts) {
            // 鍖归厤閲戦: 楼XX.XX 鎴?XX鍏?鎴?棰勪及XX
            val amountRegex = Regex("""[楼锟\s*(\d+\.?\d*)|(\d+\.?\d*)\s*鍏億棰勪及\s*(\d+\.?\d*)""")
            amountRegex.find(text)?.let { match ->
                val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
                value?.toDoubleOrNull()?.let { if (it > 0) amount = it }
            }

            // 鍖归厤璺濈: XX鍏噷 鎴?XXkm 鎴?XX鍗冪背
            val distRegex = Regex("""(\d+\.?\d*)\s*(?:鍏噷|km|鍗冪背)""", RegexOption.IGNORE_CASE)
            distRegex.find(text)?.let { match ->
                match.groupValues[1].toDoubleOrNull()?.let { if (it > 0) distance = it }
            }
        }

        return if (amount != null && distance != null) Pair(amount, distance) else null
    }

    /**
     * 鏂规硶2: 閫氳繃濡傜ズAPP鐨刅iew ID鎻愬彇
     */
    private fun extractByViewId(root: AccessibilityNodeInfo): Pair<Double, Double>? {
        var amount: Double? = null
        var distance: Double? = null

        // 鎼滅储鍖呭惈浠锋牸淇℃伅鐨勮妭鐐?        val priceIds = listOf(
            "com.ruqi.driver:id/tv_price",
            "com.ruqi.driver:id/tv_amount",
            "com.ruqi.driver:id/tv_fee",
            "com.ruqi.driver:id/tv_estimated_price",
            "com.tencent.gac.driver:id/tv_price",
            "com.tencent.gac.driver:id/tv_amount"
        )
        for (id in priceIds) {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { node ->
                val text = node.text?.toString() ?: ""
                val num = extractNumber(text)
                if (num > 0 && amount == null) amount = num
                node.recycle()
            }
        }

        // 鎼滅储鍖呭惈璺濈淇℃伅鐨勮妭鐐?        val distIds = listOf(
            "com.ruqi.driver:id/tv_distance",
            "com.ruqi.driver:id/tv_mileage",
            "com.tencent.gac.driver:id/tv_distance",
            "com.tencent.gac.driver:id/tv_mileage"
        )
        for (id in distIds) {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { node ->
                val text = node.text?.toString() ?: ""
                val num = extractNumber(text)
                if (num > 0 && distance == null) distance = num
                node.recycle()
            }
        }

        return if (amount != null && distance != null) Pair(amount, distance) else null
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) texts.add(text)

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
