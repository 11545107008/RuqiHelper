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
            text = "如祺好单助手"
            textSize = 28f
            setTextColor(0xFF212121.toInt())
            setPadding(0, 0, 0, 8)
        })

        layout.addView(TextView(this).apply {
            text = "自动识别如祺订单，大单/短单震动+弹窗提醒"
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
            text = "• 大单：每公里单价 ≥ ¥${Config.MIN_PRICE_PER_KM}\n• 短单：距离 ≤ ${Config.MAX_SHORT_DISTANCE}公里\n• 满足任一条件即弹窗+震动提醒"
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

        // 使用说明
        layout.addView(TextView(this).apply {
            text = "\n使用说明："
            textSize = 14f
            setTextColor(0xFF212121.toInt())
            setPadding(0, 16, 0, 8)
        })

        layout.addView(TextView(this).apply {
            text = "1. 开启本应用的无障碍服务和悬浮窗权限\n2. 打开如祺车主APP正常接单\n3. 收到订单时自动判断\n4. 大单/短单自动弹窗+震动提醒\n5. 如果没反应，查看下方调试信息"
            textSize = 14f
            setTextColor(0xFF424242.toInt())
        })

        layout.addView(TextView(this).apply {
            text = "\n如遇到不提醒：查看调试信息中的包名和文本，截图发给开发者调整。"
            textSize = 12f
            setTextColor(0xFF9E9E9E.toInt())
        })

        scrollView.addView(layout)
        return scrollView
    }

    private fun updateStatus() {
        val running = Config.isServiceRunning
        statusText.text = if (running) "● 服务运行中" else "○ 服务未启动"
        statusText.setTextColor(if (running) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt())
    }

    private fun updateDebug() {
        val timeStr = if (Config.debugTime > 0) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(Config.debugTime))
        } else "无"
        debugText.text = buildString {
            append("扫描时间: $timeStr\n")
            append("应用包名: ${Config.debugPackage.ifEmpty { "无" }}\n")
            append("最近文本: ${Config.debugTexts.ifEmpty { "无" }}")
        }
    }
}
