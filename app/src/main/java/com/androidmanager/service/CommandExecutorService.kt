package com.androidmanager.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.model.CommandType
import com.androidmanager.data.model.DeviceCommand
import com.androidmanager.data.repository.DeviceRepository
import com.androidmanager.manager.DevicePolicyManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service to execute commands received from backend
 */
object CommandExecutorService {

    private const val TAG = "CommandExecutor"

    /**
     * Execute lock command from FCM notification
     */
    fun executeLockFromFcm(context: Context, message: String) {
        Log.d(TAG, "Executing LOCK from FCM")

        val preferencesManager = PreferencesManager(context)
        val policyHelper = DevicePolicyManagerHelper(context)
        val repository = DeviceRepository(preferencesManager)

        CoroutineScope(Dispatchers.Main).launch {
            var success = false
            var errorMessage: String? = null

            try {
                success = executeLockCommand(context, preferencesManager, policyHelper, message)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing lock command", e)
                errorMessage = e.message
            }

            // Send lock response to backend
            repository.sendLockResponse("LOCK_DEVICE", success, errorMessage)
        }
    }

    /**
     * Execute unlock command from FCM notification
     */
    fun executeUnlockFromFcm(context: Context) {
        Log.d(TAG, "Executing UNLOCK from FCM")

        val preferencesManager = PreferencesManager(context)
        val policyHelper = DevicePolicyManagerHelper(context)
        val repository = DeviceRepository(preferencesManager)

        CoroutineScope(Dispatchers.Main).launch {
            var success = false
            var errorMessage: String? = null

            try {
                success = executeUnlockCommand(context, preferencesManager, policyHelper)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing unlock command", e)
                errorMessage = e.message
            }

            // Send unlock response to backend
            repository.sendLockResponse("UNLOCK_DEVICE", success, errorMessage)
        }
    }

    /**
     * Execute a command from backend (legacy)
     */
    fun executeCommand(context: Context, command: DeviceCommand) {
        Log.d(TAG, "Executing command: ${command.commandType}")

        val preferencesManager = PreferencesManager(context)
        val policyHelper = DevicePolicyManagerHelper(context)
        val repository = DeviceRepository(preferencesManager)

        CoroutineScope(Dispatchers.Main).launch {
            var success = false
            var errorMessage: String? = null

            try {
                when (command.commandType) {
                    CommandType.LOCK -> {
                        success = executeLockCommand(context, preferencesManager, policyHelper, command.payload?.message)
                    }
                    CommandType.UNLOCK -> {
                        success = executeUnlockCommand(context, preferencesManager, policyHelper)
                    }
                    CommandType.WIPE -> {
                        success = executeWipeCommand(policyHelper, command.payload?.wipeData ?: false)
                    }
                    CommandType.UPDATE_MESSAGE -> {
                        success = executeUpdateMessageCommand(preferencesManager, command.payload?.message)
                    }
                    CommandType.GET_LOCATION -> {
                        success = executeGetLocationCommand(context)
                    }
                    CommandType.SET_PIN -> {
                        success = executeSetPinCommand(policyHelper, command.payload?.pin)
                    }
                    CommandType.PING -> {
                        success = true // Just acknowledge
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
                errorMessage = e.message
            }

            // Acknowledge command
            repository.acknowledgeCommand(command.commandId, success, errorMessage)
        }
    }

    /**
     * Lock the device
     */
    private suspend fun executeLockCommand(
        context: Context,
        preferencesManager: PreferencesManager,
        policyHelper: DevicePolicyManagerHelper,
        message: String?
    ): Boolean {
        Log.d(TAG, "Executing LOCK command")

        // Save lock state
        val lockMessage = message ?: "Payment overdue. Please contact the shop owner."
        preferencesManager.setDeviceLocked(true, lockMessage)

        // Enter kiosk mode
        if (policyHelper.isDeviceOwner()) {
            policyHelper.enterKioskMode(context.packageName)
        }

        // Launch lock screen
        val intent = Intent(context, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        context.startActivity(intent)

        return true
    }

    /**
     * Unlock the device
     */
    private suspend fun executeUnlockCommand(
        context: Context,
        preferencesManager: PreferencesManager,
        policyHelper: DevicePolicyManagerHelper
    ): Boolean {
        Log.d(TAG, "Executing UNLOCK command")

        // Clear lock state
        preferencesManager.setDeviceLocked(false)

        // Exit kiosk mode
        if (policyHelper.isDeviceOwner()) {
            policyHelper.exitKioskMode()
        }

        // Broadcast unlock event
        val intent = Intent("com.androidmanager.ACTION_DEVICE_UNLOCKED")
        context.sendBroadcast(intent)

        return true
    }

    /**
     * Wipe device data
     */
    private fun executeWipeCommand(
        policyHelper: DevicePolicyManagerHelper,
        wipeExternalStorage: Boolean
    ): Boolean {
        Log.d(TAG, "Executing WIPE command")

        if (!policyHelper.isDeviceOwner()) {
            Log.e(TAG, "Cannot wipe - not device owner")
            return false
        }

        // This is a destructive operation
        // The device will be factory reset
        // FRP will require the shop owner's Google account
        
        // Note: Uncomment to enable wipe functionality
        // val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        // val flags = if (wipeExternalStorage) {
        //     DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        // } else {
        //     0
        // }
        // dpm.wipeData(flags)

        Log.w(TAG, "Wipe command received but not executed for safety")
        return true
    }

    /**
     * Update lock screen message
     */
    private suspend fun executeUpdateMessageCommand(
        preferencesManager: PreferencesManager,
        message: String?
    ): Boolean {
        if (message == null) {
            Log.e(TAG, "No message provided")
            return false
        }

        Log.d(TAG, "Updating lock message: $message")

        val isLocked = preferencesManager.isDeviceLockedSync()
        if (isLocked) {
            preferencesManager.setDeviceLocked(true, message)
        }

        return true
    }

    /**
     * Request immediate location update
     */
    private fun executeGetLocationCommand(context: Context): Boolean {
        Log.d(TAG, "Executing GET_LOCATION command")

        // Trigger location update through service
        val intent = Intent(context, DeviceMonitorService::class.java).apply {
            action = "ACTION_GET_LOCATION"
        }
        context.startService(intent)

        return true
    }

    /**
     * Set new PIN
     */
    private fun executeSetPinCommand(
        policyHelper: DevicePolicyManagerHelper,
        pin: String?
    ): Boolean {
        if (pin == null || pin.length != 4) {
            Log.e(TAG, "Invalid PIN")
            return false
        }

        Log.d(TAG, "Setting new PIN")
        return policyHelper.setScreenLockPin(pin)
    }
}
