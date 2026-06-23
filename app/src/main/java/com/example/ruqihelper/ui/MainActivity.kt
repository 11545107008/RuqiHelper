package com.example.ruqihelper.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ruqihelper.utils.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var debugText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateDebug()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        handler.postDelayed(refreshRunnable, 500)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun createLayout(): ScrollView {
        val scrollView = ScrollView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        // 标题
        layout.addView(TextView(this).apply {
            text = "如祺好单助手 v2.4"
            textSize = 28f
            setTextColor(0xFF212121.toInt())
            setPadding(0, 0, 0, 8)
        })

        layout.addView(TextView(this).apply {
            text = "自动识别如祺订单，大单/短单震动+弹窗提醒\n支持界面扫描 + 通知监听，双重覆盖"
            textSize = 14f
            setTextColor(0xFF757575.toInt())
            setPadding(0, 0, 0, 32)
        })

        // 状态
        statusText = TextView(this).apply {
            textSize = 18f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(statusText)

        // 开启无障碍服务按钮
        layout.addView(Button(this).apply {
            text = "开启无障碍服务"
            textSize = 16f
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        layout.addView(TextView(this).apply {
            text = "在无障碍设置中找到【如祺好单助手】并开启"
            textSize = 12f
            setTextColor(0xFF757575.toInt())
            setPadding(0, 4, 0, 16)
        })

        // 开启通知使用权按钮
        layout.addView(Button(this).apply {
            text = "开启通知使用权"
            textSize = 16f
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        })
        layout.addView(TextView(this).apply {
            text = "在通知使用权中找到【如祺好单助手】并开启\n（关键！订单通知必须靠这个抓）"
            textSize = 12f
            setTextColor(0xFF757575.toInt())
            setPadding(0, 4, 0, 16)
        })

        // 悬浮窗权限按钮
        layout.addView(Button(this).apply {
            text = "开启悬浮窗权限"
            textSize = 16f
            setOnClickListener {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        })

        // 配置说明
        layout.addView(TextView(this).apply {
            text = "\n提醒规则："
            textSize = 14f
            setTextColor(0xFF212121.toInt())
            setPadding(0, 24, 0, 8)
        })

        layout.addView(TextView(this).apply {
            text = "• 大单：每公里单价 >= ¥${Config.MIN_PRICE_PER_KM}\n• 短单：距离 <= ${Config.MAX_SHORT_DISTANCE}公里\n• 满足任一条件即弹窗+震动提醒"
            textSize = 14f
            setTextColor(0xFF424242.toInt())
        })

        // 调试信息
        layout.addView(TextView(this).apply {
            text = "\n调试信息（每3秒刷新）："
            textSize = 14f
            setTextColor(0xFF212121.toInt())
            setPadding(0, 24, 0, 8)
        })

        debugText = TextView(this).apply {
            text = "等待数据..."
            textSize = 12f
            setTextColor(0xFF757575.toInt())
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(12, 12, 12, 12)
        }
        layout.addView(debugText)

        scrollView.addView(layout)
        return scrollView
    }

    private fun updateStatus() {
        val asRunning = Config.isServiceRunning
        val notiRunning = Config.isNotificationRunning
        val statusStr = buildString {
            append(if (asRunning) "● 无障碍:运行中" else "○ 无障碍:未启动")
            append("\n")
            append(if (notiRunning) "● 通知监听:运行中" else "○ 通知监听:未启动")
        }
        statusText.text = statusStr
        statusText.setTextColor(
            if (asRunning || notiRunning) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
        )
    }

    private fun updateDebug() {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val asTimeStr = if (Config.debugTime > 0) fmt.format(Date(Config.debugTime)) else "无"
        val notiTimeStr = if (Config.notiTime > 0) fmt.format(Date(Config.notiTime)) else "无"

        val errStr = if (Config.errorTime > 0) {
            val et = fmt.format(Date(Config.errorTime))
            "\n错误: ${Config.lastError} ($et)"
        } else ""

        debugText.text = buildString {
            append("[无障碍扫描]\n")
            append("  最后扫描: $asTimeStr\n")
            append("  事件计数: ${Config.debugEventCount}\n")
            append("  包名: ${Config.debugPackage.ifEmpty { "无" }}\n")
            append("  文本: ${Config.debugTexts.ifEmpty { "无" }}\n")
            append("\n[通知监听]\n")
            append("  最后通知: $notiTimeStr\n")
            append("  通知计数: ${Config.notiCount}\n")
            append("  包名: ${Config.notiPackage.ifEmpty { "无" }}\n")
            append("  标题: ${Config.notiTitle.ifEmpty { "无" }}\n")
            append("  内容: ${Config.notiText.ifEmpty { "无" }}")
            append(errStr)
        }
    }
}
