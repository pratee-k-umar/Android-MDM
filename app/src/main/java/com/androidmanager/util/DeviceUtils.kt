package com.androidmanager.util

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * Utility functions for device information
 */
object DeviceUtils {

    /**
     * Generate a unique device ID
     */
    fun generateDeviceId(context: Context): String {
        // Try to use Android ID
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return if (androidId != null && androidId != "9774d56d682e549c") {
            // Valid Android ID
            "DEV_${androidId}"
        } else {
            // Fallback to random UUID
            "DEV_${UUID.randomUUID()}"
        }
    }

    /**
     * Get battery level
     */
    fun getBatteryLevel(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /**
     * Get device info string
     */
    fun getDeviceInfoString(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }

    /**
     * Check if device is running on emulator
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }
}
