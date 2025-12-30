package com.androidmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.manager.DevicePolicyManagerHelper
import com.androidmanager.service.DeviceMonitorService
import com.androidmanager.service.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Boot completed receiver - starts services after device boot
 * CRITICAL: Handles device lock state restoration after reboot/force restart
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        // Handle all boot actions
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == ACTION_QUICKBOOT ||
            action == ACTION_HTC_QUICKBOOT) {
            
            Log.d(TAG, "🔄 Boot event received: $action")
            
            // Use goAsync() to extend the life of the broadcast receiver
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    handleBootCompleted(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling boot completed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
    
    private suspend fun handleBootCompleted(context: Context) {
        val preferencesManager = PreferencesManager(context)
        val policyHelper = DevicePolicyManagerHelper(context)
        
        val isSetupComplete = preferencesManager.isSetupComplete.first()
        Log.d(TAG, "Setup complete: $isSetupComplete")
        
        if (!isSetupComplete) {
            Log.d(TAG, "Setup not complete - skipping boot handling")
            return
        }
        
        // ALWAYS start the monitor service after boot
        try {
            val serviceIntent = Intent(context, DeviceMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "✅ DeviceMonitorService started after boot")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start DeviceMonitorService after boot", e)
        }
        
        // Check if device should be locked
        val isLocked = preferencesManager.isDeviceLockedSync()
        Log.d(TAG, "Device lock state: isLocked=$isLocked")
        
        if (isLocked) {
            Log.d(TAG, "🔒 Device was locked before reboot - RESTORING KIOSK MODE")
            
            // Restore kiosk mode FIRST - this blocks power menu and navigation
            if (policyHelper.isDeviceOwner()) {
                policyHelper.enterKioskMode(context.packageName)
                Log.d(TAG, "✅ Kiosk mode restored after boot")
            } else {
                Log.w(TAG, "⚠️ Not device owner - cannot restore kiosk mode")
            }
            
            // Then start lock screen activity
            val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(lockIntent)
            Log.d(TAG, "✅ LockScreenActivity started after boot")
        }
    }
}
