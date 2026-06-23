package com.example.ruqihelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
            // 监听所有事件类型
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // 关键flags
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.DEFAULT

            notificationTimeout = 100
        }
        serviceInfo = info

        // 启动轮询扫描（每2秒主动扫描）
        startPolling()

        Toast.makeText(this, "如祺好单助手已启动 v2.1\n目标: ${Config.TARGET_PACKAGE}", Toast.LENGTH_SHORT).show()
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
            // 更新调试信息
            Config.debugPackage = "$pkg (evt:${event.eventType})"

            // 扫描所有窗口
            scanAllWindows(debugMode = false)

            // 尝试rootInActiveWindow
            val root = rootInActiveWindow ?: return
            try {
                val texts = mutableListOf<String>()
                deepCollectTexts(root, texts, 0, 50) // 最多50层深度
                Config.debugTexts = texts.take(30).joinToString(" | ")

                val order = parseOrderFromTexts(texts, root)
                processOrder(order, now)
            } finally {
                root.recycle()
            }
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    private fun scanAllWindows(debugMode: Boolean) {
        try {
            val windows = windows ?: return
            if (windows.isEmpty()) return

            val allTexts = mutableListOf<String>()
            var foundTarget = false

            for (i in 0 until windows.size) {
                val window = windows[i] ?: continue
                val root = window.root ?: continue
                try {
                    // 检查是否是目标APP的窗口
                    val rootPkg = root.packageName?.toString() ?: ""
                    if (rootPkg == Config.TARGET_PACKAGE || 
                        (debugMode && !rootPkg.isNullOrBlank() && rootPkg != "com.example.ruqihelper")) {
                        foundTarget = true
                        if (debugMode) {
                            Config.debugPackage = "$rootPkg 【找到了! 窗口$i】"
                        }
                    }

                    deepCollectTexts(root, allTexts, 0, 30)
                } catch (e: Exception) {
                } finally {
                    root.recycle()
                }
            }

            if (foundTarget || debugMode) {
                Config.debugTexts = allTexts.take(30).joinToString(" | ")
                Config.debugTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // 忽略
        }
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
        var pickupDistance: Double? = null

        // 策略1：文本正则匹配（针对如祺实际格式优化）
        for (text in texts) {
            val cleaned = text.trim()

            if (amount == null) {
                // 如祺格式: "7.90元", "预估计价：7.90元", "¥7.90"
                val r1 = Regex("""(?:预估计价|预估|金额|价格|计费|费用|收入)?[：:]\s*(\d+\.?\d*)\s*元?""")
                val r2 = Regex("""[¥￥]?\s*(\d+\.\d{2})\s*元?""")
                val r3 = Regex("""(\d+\.?\d*)\s*元""")

                r1.find(cleaned)?.groups?.get(1)?.value?.toDoubleOrNull()?.let { 
                    if (it >= 0.5) amount = it 
                }
                if (amount == null) {
                    r2.find(cleaned)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
                        if (it >= 0.5) amount = it
                    }
                }
                if (amount == null) {
                    r3.find(cleaned)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
                        if (it >= 0.5) amount = it
                    }
                }
            }

            if (distance == null) {
                // 如祺格式: "全程：3.77公里", "3.77公里", "3.77km"
                val d1 = Regex("""(?:全程|里程|距离)[：:]\s*(\d+\.?\d*)\s*(?:公里|千米|km)""", RegexOption.IGNORE_CASE)
                val d2 = Regex("""(\d+\.?\d*)\s*公里""")
                val d3 = Regex("""(\d+\.?\d*)\s*(?:km|KM)""")

                d1.find(cleaned)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
                    if (it in 0.1..200.0) distance = it
                }
                if (distance == null) {
                    d2.find(cleaned)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
                        if (it in 0.1..200.0) distance = it
                    }
                }
                if (distance == null) {
                    d3.find(cleaned)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
                        if (it in 0.1..200.0) distance = it
                    }
                }
            }

            // 顺便提取接驾距离
            if (pickupDistance == null) {
                val pd = Regex("""距您[：:]?\s*(\d+\.?\d*)\s*(?:公里|km)""", RegexOption.IGNORE_CASE)
                pd.find(cleaned)?.groups?.get(1)?.value?.toDoubleOrNull()?.let {
                    pickupDistance = it
                }
            }
        }

        // 策略2：View ID精确匹配
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
                    val n = Regex("""(\d+\.?\d*)""").find(t)?.groups?.get(1)?.value?.toDoubleOrNull()
                    node.recycle()
                    if (n != null && n >= minVal) return n
                }
            } catch (e: Exception) {
                // view id not found
            }
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
            if (!text.isNullOrBlank() && text.length > 1) {
                texts.add(text)
            }
            // 也提取contentDescription
            val cd = node.contentDescription?.toString()?.trim()
            if (!cd.isNullOrBlank() && cd.length > 1 && cd != text) {
                texts.add(cd)
            }
        } catch (e: Exception) {}

        try {
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                deepCollectTexts(child, texts, depth + 1, maxDepth)
                try { child.recycle() } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
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
