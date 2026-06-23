package com.example.ruqihelper.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 悬浮窗管理器 - 好单提醒
 */
class FloatingWindow(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isShowing = false

    /**
     * 显示订单提醒
     * @param price 订单金额
     * @param distance 订单距离
     * @param pricePerKm 每公里单价
     * @param alertType 提醒类型：GOOD=好单(绿色), SHORT=短单(蓝色)
     */
    fun showOrderAlert(price: Double, distance: Double, pricePerKm: Double, alertType: String = "GOOD") {
        // 震动提醒
        vibrate()

        // 显示悬浮窗
        showFloatingWindow(price, distance, pricePerKm, alertType)
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // 修复：vibrate方法需要正确的参数
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFloatingWindow(price: Double, distance: Double, pricePerKm: Double, alertType: String = "GOOD") {
        if (isShowing) {
            updateFloatingWindow(price, distance, pricePerKm, alertType)
            return
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 根据提醒类型设置颜色和文字
        val (backgroundColor, title) = if (alertType == "SHORT") {
            Pair(Color.parseColor("#CC1565C0"), "📍 短单来了！")  // 蓝色背景
        } else {
            Pair(Color.parseColor("#CC00AA00"), "🎉 好单来了！")  // 绿色背景
        }

        // 创建悬浮窗视图
        floatingView = TextView(context).apply {
            text = "$title\n金额: ${String.format("%.1f", price)}元\n距离: ${String.format("%.1f", distance)}公里\n单价: ${String.format("%.2f", pricePerKm)}元/公里"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(40, 40, 40, 40)
            setBackgroundColor(backgroundColor)
            gravity = Gravity.CENTER
        }

        // 设置悬浮窗参数
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // 距离顶部100px
        }

        // 显示悬浮窗
        try {
            windowManager?.addView(floatingView, params)
            isShowing = true
        } catch (e: Exception) {
            Log.e("FloatingWindow", "添加悬浮窗失败，请检查悬浮窗权限", e)
            isShowing = false
            return
        }

        // 5秒后自动消失
        floatingView?.postDelayed({
            dismiss()
        }, 5000)
    }

    private fun updateFloatingWindow(price: Double, distance: Double, pricePerKm: Double, alertType: String = "GOOD") {
        val backgroundColor = if (alertType == "SHORT") {
            Color.parseColor("#CC1565C0")
        } else {
            Color.parseColor("#CC00AA00")
        }
        val title = if (alertType == "SHORT") {
            "📍 短单来了！"
        } else {
            "🎉 好单来了！"
        }
        (floatingView as? TextView)?.apply {
            text = "$title\n金额: ${String.format("%.1f", price)}元\n距离: ${String.format("%.1f", distance)}公里\n单价: ${String.format("%.2f", pricePerKm)}元/公里"
            setBackgroundColor(backgroundColor)
        }
    }

    /**
     * 关闭悬浮窗
     */
    fun dismiss() {
        if (isShowing && floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
            isShowing = false
        }
    }
}
