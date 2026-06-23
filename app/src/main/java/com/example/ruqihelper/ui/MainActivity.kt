package com.example.ruqihelper.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ruqihelper.utils.Config

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
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
            text = "在无障碍设置中找到"如祺好单助手"并开启"
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
            lineHeight = 28
        })

        // 使用说明
        layout.addView(TextView(this).apply {
            text = "\n使用步骤："
            textSize = 14f
            setTextColor(0xFF212121.toInt())
            setPadding(0, 16, 0, 8)
        })

        layout.addView(TextView(this).apply {
            text = "1. 开启本应用的无障碍服务和悬浮窗权限\n2. 打开如祺车主APP正常接单\n3. 收到订单时自动判断\n4. 大单/短单自动弹窗+震动提醒\n5. 5秒后自动消失，不挡操作"
            textSize = 14f
            setTextColor(0xFF424242.toInt())
            lineHeight = 28
        })

        layout.addView(TextView(this).apply {
            text = "\n💡 提示：如遇到不提醒的情况，可以在无障碍设置中关闭后重新开启本服务。"
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
}
