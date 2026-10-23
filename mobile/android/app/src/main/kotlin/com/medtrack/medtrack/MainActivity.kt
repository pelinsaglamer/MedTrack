package com.medtrack.medtrack

import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.medtrack.medtrack/notification_settings")
            .setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "batteryExempt" -> result.success((getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName))
                        "openNotifications" -> {
                            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                            result.success(null)
                        }
                        "openBattery" -> {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            result.success(null)
                        }
                        else -> result.notImplemented()
                    }
                } catch (_: Exception) {
                    result.error("SETTINGS_UNAVAILABLE", "System settings could not be opened", null)
                }
            }
    }
}
