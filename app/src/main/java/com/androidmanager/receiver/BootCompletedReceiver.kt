package com.androidmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.service.DeviceMonitorService
import com.androidmanager.service.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Boot completed receiver - starts services after device boot
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            
            Log.d(TAG, "Boot completed - starting services")

            val preferencesManager = PreferencesManager(context)

            CoroutineScope(Dispatchers.Main).launch {
                val isSetupComplete = preferencesManager.isSetupComplete.first()
                
                if (isSetupComplete) {
                    // Start the monitor service as foreground (required for Android O+)
                    val serviceIntent = Intent(context, DeviceMonitorService::class.java)
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.d(TAG, "DeviceMonitorService started after boot")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start DeviceMonitorService after boot", e)
                    }

                    // Check if device should be locked
                    val isLocked = preferencesManager.isDeviceLockedSync()
                    if (isLocked) {
                        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(lockIntent)
                    }
                }
            }
        }
    }
}
