package com.example.ruqihelper.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ruqihelper.calculator.PriceCalculator
import com.example.ruqihelper.ui.FloatingWindow
import com.example.ruqihelper.utils.Config

/**
 * 如祺车主订单监听无障碍服务
 * 监听如祺车主APP的订单弹窗，自动识别好单并提醒司机
 *
 * 基于APK分析结果：
 * - 真实包名：com.ruqi.driver ✓
 * - 订单弹窗Activity：HalfPushActivity（半屏弹窗）
 * - 开发方：腾讯（com.tencent.gac.driver）
 */
class OrderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OrderAccessibility"
        private const val RUQI_PACKAGE = "com.ruqi.driver"

        // 订单弹窗可能出现的Activity（通过APK分析发现）
        private val ORDER_POPUP_KEYWORDS = listOf(
            "HalfPush", "OrderDetails", "OrderUnderway", "OrderList", "Reserve"
        )
    }

    private var floatingWindow: FloatingWindow? = null
    private var lastOrderText: String = ""
    private var lastAlertTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "========== 无障碍服务已连接 ==========")
        Log.d(TAG, "监听包名: $RUQI_PACKAGE")
        floatingWindow = FloatingWindow(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // 只处理如祺车主APP的事件
        if (packageName != RUQI_PACKAGE) return

        // 判断是否是订单相关界面
        val isOrderScreen = ORDER_POPUP_KEYWORDS.any { className.contains(it) }

        // 基础日志
        if (isOrderScreen || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "【事件】${AccessibilityEvent.eventTypeToString(event.eventType)}")
            Log.d(TAG, "【类名】$className")
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "→ 窗口切换: $className")
                val rootNode = getRootInActiveWindow()
                if (rootNode != null) {
                    checkForOrder(rootNode, className)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 内容变化时也检查（订单弹窗动态出现时用）
                if (isOrderScreen) {
                    val rootNode = getRootInActiveWindow()
                    if (rootNode != null) {
                        checkForOrder(rootNode, className)
                    }
                }
            }
        }
    }

    /**
     * 检查是否为订单弹窗
     */
    private fun checkForOrder(rootNode: AccessibilityNodeInfo, className: String) {
        try {
            // 收集所有文本
            val allTexts = mutableListOf<String>()
            traverseNode(rootNode, allTexts)

            val fullText = allTexts.joinToString("\n")

            // 防重复：3秒内同一内容不重复提醒
            val now = System.currentTimeMillis()
            if (fullText == lastOrderText && now - lastAlertTime < 3000) return
            lastOrderText = fullText
            lastAlertTime = now

            // 打印当前界面所有文本（用于调试）
            Log.d(TAG, "【界面文本】开始打印...")
            allTexts.forEach { text ->
                if (text.isNotBlank()) {
                    Log.d(TAG, "  文字: $text")
                }
            }

            // 提取金额和距离
            var price = PriceCalculator.extractPrice(fullText)
            var distance = PriceCalculator.extractDistance(fullText)

            // 如果文本提取失败，尝试从节点直接读取
            if (price <= 0) {
                price = extractPriceFromNodes(rootNode)
            }
            if (distance <= 0) {
                distance = extractDistanceFromNodes(rootNode)
            }

            Log.d(TAG, "【解析结果】金额=$price, 距离=$distance")

            if (price > 0 && distance > 0) {
                val pricePerKm = PriceCalculator.calculatePricePerKm(price, distance)

                Log.d(TAG, "【订单信息】金额=${String.format("%.1f", price)}元, " +
                        "距离=${String.format("%.1f", distance)}公里, " +
                        "单价=${String.format("%.2f", pricePerKm)}元/公里")

                // 判断是否为好单或短单
                val isGoodOrder = PriceCalculator.isGoodOrder(pricePerKm, Config.MIN_PRICE_PER_KM)
                val isShortDistance = Config.ENABLE_SHORT_DISTANCE_ALERT &&
                        PriceCalculator.isShortDistance(distance, Config.MAX_SHORT_DISTANCE)

                if (isGoodOrder || isShortDistance) {
                    val type = when {
                        isGoodOrder && isShortDistance -> "好单+短单"
                        isGoodOrder -> "好单"
                        else -> "短单"
                    }
                    Log.d(TAG, "【提醒】$type！")
                    val alertType = if (isShortDistance && !isGoodOrder) "SHORT" else "GOOD"
                    floatingWindow?.showOrderAlert(price, distance, pricePerKm, alertType)

                    if (Config.ENABLE_AUTO_CLICK) {
                        Log.d(TAG, "【自动点击】尝试接单...")
                        autoClickAccept(rootNode)
                    }
                } else {
                    Log.d(TAG, "【忽略】不是好单（单价=${String.format("%.2f", pricePerKm)}）")
                }
            } else {
                Log.d(TAG, "【未识别】未能提取完整订单信息")
                Log.d(TAG, "原始文本（前300字）: ${fullText.take(300)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "【错误】解析订单失败: ${e.message}", e)
        }
    }

    /**
     * 从节点中直接读取金额
     */
    private fun extractPriceFromNodes(node: AccessibilityNodeInfo): Double {
        val keywords = listOf("元", "金额", "预估", "price", "Price")
        for (keyword in keywords) {
            val nodes = node.findAccessibilityNodeInfosByText(keyword)
            if (nodes != null) {
                for (n in nodes) {
                    val text = n.text?.toString() ?: ""
                    val desc = n.contentDescription?.toString() ?: ""
                    val price = PriceCalculator.extractPrice(text + desc)
                    if (price > 0) {
                        Log.d(TAG, "【节点读取】金额=$price（来源: $keyword）")
                        return price
                    }
                }
            }
        }
        return -1.0
    }

    /**
     * 从节点中直接读取距离
     */
    private fun extractDistanceFromNodes(node: AccessibilityNodeInfo): Double {
        val keywords = listOf("公里", "km", "距离", "distance", "Distance")
        for (keyword in keywords) {
            val nodes = node.findAccessibilityNodeInfosByText(keyword)
            if (nodes != null) {
                for (n in nodes) {
                    val text = n.text?.toString() ?: ""
                    val desc = n.contentDescription?.toString() ?: ""
                    val distance = PriceCalculator.extractDistance(text + desc)
                    if (distance > 0) {
                        Log.d(TAG, "【节点读取】距离=$distance（来源: $keyword）")
                        return distance
                    }
                }
            }
        }
        return -1.0
    }

    /**
     * 自动点击接单按钮
     */
    private fun autoClickAccept(rootNode: AccessibilityNodeInfo) {
        try {
            val keywords = listOf("接单", "抢单", "立即接单", "确认接单")
            for (keyword in keywords) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
                if (nodes != null && nodes.isNotEmpty()) {
                    for (n in nodes) {
                        if (n.isClickable) {
                            n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "【自动点击】已点击: $keyword")
                            return
                        }
                    }
                }
            }
            Log.d(TAG, "【自动点击】未找到接单按钮")
        } catch (e: Exception) {
            Log.e(TAG, "【自动点击】失败: ${e.message}", e)
        }
    }

    /**
     * 遍历节点，收集所有文本
     */
    private fun traverseNode(node: AccessibilityNodeInfo?, result: MutableList<String>) {
        if (node == null) return
        node.text?.let { result.add(it.toString()) }
        node.contentDescription?.let { result.add(it.toString()) }
        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), result)
        }
    }

    override fun onInterrupt() {
        Log.e(TAG, "【中断】无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingWindow?.dismiss()
        Log.d(TAG, "【销毁】无障碍服务已销毁")
    }
}
