package com.example.ruqihelper.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
class FloatingWindow(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var isShowing = false

    fun show(title: String, message: String, isBig: Boolean, isShort: Boolean) {
        if (isShowing) hide()

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 创建悬浮窗布局
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            // background: rounded rectangle
            setBackgroundColor(
                when {
                    isBig && isShort -> 0xFFE91E63.toInt() // 大单+短单: 玫红
                    isBig -> 0xFFFF5722.toInt()            // 大单: 橙色
                    isShort -> 0xFF2196F3.toInt()          // 短单: 蓝色
                    else -> 0xFF4CAF50.toInt()             // 普通好单: 绿色
                }
            )
            gravity = Gravity.CENTER
            minimumWidth = 500
        }

        // 标题
        layout.addView(TextView(context).apply {
            text = title
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })

        // 内容
        layout.addView(TextView(context).apply {
            text = message
            textSize = 48f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })

        floatView = layout

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = -200

        windowManager?.addView(floatView, params)
        isShowing = true

        // 震动提醒
        vibrate()

        // 闪烁动画
        startBlink(layout)

        // 5秒后自动消失
        layout.postDelayed({ hide() }, 5000)
    }

    fun hide() {
        try {
            if (floatView != null && windowManager != null) {
                windowManager?.removeView(floatView)
                floatView = null
            }
        } catch (_: Exception) {}
        isShowing = false
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
            }
        } catch (_: Exception) {}
    }

    private fun startBlink(view: View) {
        val animator = ValueAnimator.ofFloat(1f, 0.3f).apply {
            duration = 300
            repeatCount = 8
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { view.alpha = it.animatedValue as Float }
            start()
        }
    }
}
