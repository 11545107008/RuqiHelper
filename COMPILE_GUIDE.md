# 编译指南 - 如祺好单助手

## 第一步：安装 Android Studio

1. 下载 [Android Studio](https://developer.android.com/studio)
2. 安装时选择 "Standard" 安装类型
3. 等待 SDK 下载完成（需要约 2GB 空间）

## 第二步：打开项目

1. 启动 Android Studio
2. 点击 **Open** 或 **Open an existing project**
3. 选择文件夹：`C:\Users\Administrator\WorkBuddy\2026-06-23-07-20-53\RuqiHelper`
4. 等待 Gradle 同步完成（第一次可能要 5-10 分钟）

## 第三步：编译 APK

**方法1：图形界面**
1. 菜单栏点击 **Build → Make Project**
2. 等待编译完成
3. APK 生成在：`RuqiHelper\app\build\outputs\apk\debug\app-debug.apk`

**方法2：命令行**
```bash
cd C:\Users\Administrator\WorkBuddy\2026-06-23-07-20-53\RuqiHelper
.\gradlew.bat assembleDebug
```

## 第四步：安装到手机

1. 把 `app-debug.apk` 传到手机
2. 在手机上点击安装
3. 如果提示"禁止安装未知来源应用"，去设置里允许

## 第五步：使用

1. 打开 **如祺好单助手** APP
2. 点击 **开启无障碍服务** → 在系统设置里找到"如祺好单助手"并开启
3. 返回 APP，点击 **开启悬浮窗权限** → 允许
4. 点击 **测试提醒** 按钮，确认能看到悬浮窗提醒
5. 打开如祺司机端，等待订单

---

## ⚠️ 可能遇到的问题

### 问题1：Gradle 同步失败
**解决**：检查网络，可能需要翻墙下载依赖

### 问题2：编译报错
**解决**：把错误信息发给我，我帮你改

### 问题3：安装后没反应（检测不到订单）
**原因**：如祺司机端的包名或界面结构和我写的不一样
**解决**：
1. 用 `adb shell pm list packages | findstr ruqi` 查如祺的真实包名
2. 用 `adb shell uiautomator dump` 查看订单弹窗的界面结构
3. 把信息发给我，我帮你改代码

---

## 📝 我已修复的代码问题

| 文件 | 修复内容 |
|------|---------|
| `FloatingWindow.kt` | 移除未使用的 import、修复震动 API 调用、修复更新悬浮窗时背景色不变的问题 |
| `MainActivity.kt` | 移除未使用的 import、修复检查无障碍服务是否开启的逻辑、添加测试按钮 |
| `PriceCalculator.kt` | 增强正则匹配，支持更多金额/距离格式（¥45.5、45.5块、12.3km等） |
| `AndroidManifest.xml` | 移除可能导致问题的 `foregroundServiceType` 属性 |
| `OrderAccessibilityService.kt` | 增加详细日志输出，方便调试 |

---

## 📞 需要帮忙？

把编译错误或运行时的日志发给我，我帮你改！
