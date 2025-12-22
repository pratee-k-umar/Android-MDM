package com.androidmanager.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.service.DeviceMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Device Admin Receiver - Handles device owner events and policy management
 * This is the core component that enables MDM capabilities
 */
class EMIDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "EMIDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, EMIDeviceAdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin Enabled")
        
        // Suppress device owner notifications immediately
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getComponentName(context)
            
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                // Clear all messages and notifications
                dpm.setDeviceOwnerLockScreenInfo(adminComponent, null)
                dpm.setShortSupportMessage(adminComponent, null)
                dpm.setLongSupportMessage(adminComponent, null)
                Log.d(TAG, "Device owner notifications suppressed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not suppress notifications: ${e.message}")
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin Disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d(TAG, "Profile Provisioning Complete")
        
        // Start the device monitor service
        val serviceIntent = Intent(context, DeviceMonitorService::class.java)
        context.startForegroundService(serviceIntent)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d(TAG, "Lock Task Mode Entering: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d(TAG, "Lock Task Mode Exiting")
    }

    override fun onPasswordChanged(context: Context, intent: Intent, userHandle: UserHandle) {
        super.onPasswordChanged(context, intent, userHandle)
        Log.d(TAG, "Password Changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent, userHandle: UserHandle) {
        super.onPasswordFailed(context, intent, userHandle)
        Log.d(TAG, "Password Failed")
        
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val failedAttempts = dpm.currentFailedPasswordAttempts
        
        // Log failed attempts for security monitoring
        CoroutineScope(Dispatchers.IO).launch {
            // Could send this to backend for security monitoring
            Log.w(TAG, "Failed password attempts: $failedAttempts")
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, userHandle: UserHandle) {
        super.onPasswordSucceeded(context, intent, userHandle)
        Log.d(TAG, "Password Succeeded")
    }

    /**
     * Called when the device is about to be factory reset
     * We cannot prevent hard reset but FRP will kick in
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_DEVICE_ADMIN_ENABLED -> {
                Log.d(TAG, "Admin enabled via broadcast")
            }
            ACTION_DEVICE_ADMIN_DISABLED -> {
                Log.d(TAG, "Admin disabled via broadcast")
            }
            ACTION_DEVICE_ADMIN_DISABLE_REQUESTED -> {
                Log.d(TAG, "Admin disable requested")
                // This can be blocked if we're device owner
            }
        }
    }
}
