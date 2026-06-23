package com.example.ruqihelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = Runnable { pollWindows() }
    private var isPolling = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Config.isServiceRunning = true
        floatingWindow = FloatingWindow(this)

        val info = AccessibilityServiceInfo().apply {
            // 监听关键事件类型（不用TYPES_ALL_MASK避免过度回调）
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // 关键flags — 注意：NOT include FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            // 那个会导致触摸变成"探索模式"（双击才点击），严重影响手机正常使用
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.DEFAULT

            // 500ms超时，避免过于频繁
            notificationTimeout = 500
        }
        serviceInfo = info

        // 启动轮询扫描（每2秒主动扫描）
        startPolling()

        Toast.makeText(this, "如祺好单助手 v2.2\n目标: ${Config.TARGET_PACKAGE}", Toast.LENGTH_SHORT).show()
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        handler.postDelayed(pollRunnable, 2000)
    }

    private fun pollWindows() {
        if (!isPolling) return
        try {
            scanAllWindows(debugMode = true)
        } finally {
            handler.postDelayed(pollRunnable, 2000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()

        Config.debugEventCount++

        val pkg = event.packageName?.toString() ?: "unknown"

        // 先处理通知事件
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            handleNotificationEvent(event, now)
            return
        }

        // 排除自己
        if (pkg.startsWith("com.example.ruqihelper") || pkg.startsWith("com.android.systemui")) return

        try {
            Config.debugPackage = "$pkg (evt:${event.eventType})"

            // 扫描所有窗口
            scanAllWindows(debugMode = false)

            // 尝试rootInActiveWindow
            val root = rootInActiveWindow ?: return
            try {
                val texts = mutableListOf<String>()
                deepCollectTexts(root, texts, 0, 50)
                Config.debugTexts = texts.take(30).joinToString(" | ")

                val order = parseOrderFromTexts(texts, root)
                processOrder(order, now)
            } finally {
                root.recycle()
            }
        } catch (_: Exception) {}
    }

    private fun scanAllWindows(debugMode: Boolean) {
        try {
            val wins = windows ?: return
            if (wins.isEmpty()) return

            val allTexts = mutableListOf<String>()
            var foundTarget = false

            for (i in 0 until wins.size) {
                val window = wins[i] ?: continue
                val root = window.root ?: continue
                try {
                    val rootPkg = root.packageName?.toString() ?: ""
                    if (rootPkg == Config.TARGET_PACKAGE ||
                        (debugMode && rootPkg.isNotEmpty() && rootPkg != "com.example.ruqihelper")) {
                        foundTarget = true
                        if (debugMode) {
                            Config.debugPackage = "$rootPkg [窗口$i]"
                        }
                    }
                    deepCollectTexts(root, allTexts, 0, 30)
                } catch (_: Exception) {
                } finally {
                    root.recycle()
                }
            }

            if (foundTarget || debugMode) {
                Config.debugTexts = allTexts.take(30).joinToString(" | ")
                Config.debugTime = System.currentTimeMillis()
            }
        } catch (_: Exception) {}
    }

    private fun handleNotificationEvent(event: AccessibilityEvent, now: Long) {
        val texts = mutableListOf<String>()
        for (i in 0 until event.recordCount) {
            val record = event.text[i]?.toString() ?: continue
            if (record.isNotBlank()) texts.add(record)
        }
        if (texts.isEmpty()) return

        Config.debugPackage = "通知:${event.packageName}"
        Config.debugTexts = texts.joinToString(" | ")
        Config.debugTime = now

        val order = parseOrderFromTexts(texts, null)
        processOrder(order, now)
    }

    private fun processOrder(order: OrderInfo?, now: Long) {
        if (order == null) return
        if (!order.isGoodOrder) return
        if (now - lastAlertTime < alertCooldown) return

        lastAlertTime = now
        val title = when {
            order.isBigOrder && order.isShortOrder -> "超级好单!"
            order.isBigOrder -> "大单来了!"
            else -> "短单提醒"
        }
        val msg = String.format("%.2f元 / %.1f公里\n%.2f元/公里",
            order.amount, order.distance, order.pricePerKm)
        floatingWindow.show(title, msg, order.isBigOrder, order.isShortOrder)
    }

    private fun parseOrderFromTexts(texts: List<String>, rootNode: AccessibilityNodeInfo?): OrderInfo? {
        var amount: Double? = null
        var distance: Double? = null

        for (text in texts) {
            val cleaned = text.trim()
            if (cleaned.isEmpty()) continue

            if (amount == null) {
                // r1: 关键词+冒号前缀格式: "预估计价：7.90元"
                // 注意：冒号前部分可选，冒号后部分必须
                val r1 = Regex("""(?:预估计价|预估|金额|价格|计费|费用|收入)?[：:]\s*(\d+\.?\d+)\s*元?""")
                r1.find(cleaned)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it >= 0.5) amount = it }
                }
            }
            if (amount == null) {
                // r2: ¥符号前缀: "¥7.90" or "¥12.50"
                val r2 = Regex("""[¥￥]\s*(\d+\.?\d+)\s*元?""")
                r2.find(cleaned)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it >= 0.5) amount = it }
                }
            }
            if (amount == null) {
                // r3: "数字+元" 格式（最通用）: "7.90元", "12.5元"
                val r3 = Regex("""(\d+\.?\d+)\s*元""")
                r3.find(cleaned)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it >= 0.5) amount = it }
                }
            }

            if (distance == null) {
                // d1: 关键词前缀: "全程：3.77公里"
                val d1 = Regex("""(?:全程|里程|距离)[：:]\s*(\d+\.?\d+)\s*(?:公里|千米|km)""", RegexOption.IGNORE_CASE)
                d1.find(cleaned)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it in 0.1..200.0) distance = it }
                }
            }
            if (distance == null) {
                // d2: "数字+公里": "3.77公里"
                val d2 = Regex("""(\d+\.?\d+)\s*公里""")
                d2.find(cleaned)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it in 0.1..200.0) distance = it }
                }
            }
            if (distance == null) {
                // d3: "数字+km": "3.77km"
                val d3 = Regex("""(\d+\.?\d+)\s*(?:km|KM)""")
                d3.find(cleaned)?.let { m ->
                    m.groupValues[1].toDoubleOrNull()?.let { if (it in 0.1..200.0) distance = it }
                }
            }
        }

        // View ID精确匹配（备选）
        if ((amount == null || distance == null) && rootNode != null) {
            amount = amount ?: findInViewById(rootNode, listOf(
                "com.ruqi.driver:id/tv_price",
                "com.ruqi.driver:id/tv_amount",
                "com.ruqi.driver:id/tv_estimate_price",
                "com.ruqi.driver:id/tv_income",
                "com.ruqi.driver:id/tv_money",
                "com.ruqi.driver:id/tv_fee",
                "com.ry.ryapp:id/tv_price",
                "com.ry.ryapp:id/tv_amount"
            ), 0.5)

            distance = distance ?: findInViewById(rootNode, listOf(
                "com.ruqi.driver:id/tv_distance",
                "com.ruqi.driver:id/tv_mileage",
                "com.ruqi.driver:id/tv_total_distance",
                "com.ruqi.driver:id/tv_journey",
                "com.ry.ryapp:id/tv_distance",
                "com.ry.ryapp:id/tv_mileage"
            ), 0.1)
        }

        val a = amount
        val d = distance
        return if (a != null && d != null) {
            PriceCalculator.calculate(a, d)
        } else null
    }

    private fun findInViewById(root: AccessibilityNodeInfo, ids: List<String>, minVal: Double): Double? {
        for (id in ids) {
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                for (node in nodes) {
                    val t = node.text?.toString() ?: ""
                    val n = Regex("""(\d+\.?\d+)""").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
                    node.recycle()
                    if (n != null && n >= minVal) return n
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun deepCollectTexts(
        node: AccessibilityNodeInfo,
        texts: MutableList<String>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return

        try {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length >= 1) {
                texts.add(text)
            }
            val cd = node.contentDescription?.toString()?.trim()
            if (!cd.isNullOrBlank() && cd.length >= 1 && cd != text) {
                texts.add(cd)
            }
        } catch (_: Exception) {}

        try {
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                deepCollectTexts(child, texts, depth + 1, maxDepth)
                try { child.recycle() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        Config.isServiceRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isPolling = false
        handler.removeCallbacks(pollRunnable)
        Config.isServiceRunning = false
        floatingWindow.hide()
    }
}
