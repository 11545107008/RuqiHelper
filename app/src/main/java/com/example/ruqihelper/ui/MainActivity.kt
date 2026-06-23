package com.example.ruqihelper.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ruqihelper.R

/**
 * 主设置界面
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etMinPrice: EditText
    private lateinit var switchAutoClick: Switch
    private lateinit var switchShortDistance: Switch
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnOpenOverlay: Button
    private lateinit var btnTestAlert: Button
    private lateinit var floatingWindow: FloatingWindow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        floatingWindow = FloatingWindow(this)
        initViews()
        setupListeners()
        checkPermissions()
    }

    private fun initViews() {
        etMinPrice = findViewById(R.id.etMinPrice)
        switchAutoClick = findViewById(R.id.switchAutoClick)
        switchShortDistance = findViewById(R.id.switchShortDistance)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        btnOpenOverlay = findViewById(R.id.btnOpenOverlay)
        btnTestAlert = findViewById(R.id.btnTestAlert)

        // 设置默认值
        etMinPrice.setText(Config.MIN_PRICE_PER_KM.toString())
        switchAutoClick.isChecked = Config.ENABLE_AUTO_CLICK
        switchShortDistance.isChecked = Config.ENABLE_SHORT_DISTANCE_ALERT
    }

    private fun setupListeners() {
        // 保存单价阈值
        etMinPrice.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = etMinPrice.text.toString().toDoubleOrNull()
                if (value != null && value > 0) {
                    // 这里应该保存到SharedPreferences，简化处理直接提示
                    Toast.makeText(this, "单价阈值已设置为: ${value}元/公里", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 自动点击开关
        switchAutoClick.setOnCheckedChangeListener { _, isChecked ->
            val tip = if (isChecked) "已开启自动点击（风险高，可能被封号）" else "已关闭自动点击"
            Toast.makeText(this, tip, Toast.LENGTH_LONG).show()
        }

        // 短单提醒开关
        switchShortDistance.setOnCheckedChangeListener { _, isChecked ->
            val tip = if (isChecked) "已开启短单提醒（2公里内）" else "已关闭短单提醒"
            Toast.makeText(this, tip, Toast.LENGTH_SHORT).show()
        }

        // 打开无障碍设置
        btnOpenAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // 打开悬浮窗权限设置
        btnOpenOverlay.setOnClickListener {
            openOverlaySettings()
        }

        // 测试提醒按钮
        btnTestAlert.setOnClickListener {
            // 测试好单提醒
            floatingWindow?.showOrderAlert(25.5, 15.0, 1.7, "GOOD")
            Toast.makeText(this, "已触发测试提醒，查看屏幕顶部", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        // 检查无障碍服务是否开启
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请开启无障碍服务", Toast.LENGTH_LONG).show()
        }

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请开启悬浮窗权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (accessibilityServices.isNullOrEmpty()) return false

        // 格式是 "package1/service1:package2/service2:..."
        val services = accessibilityServices.split(":")
        return services.any { it.contains("com.example.ruqihelper") }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "请找到"如祺助手"并开启", Toast.LENGTH_LONG).show()
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
