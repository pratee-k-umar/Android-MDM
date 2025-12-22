package com.androidmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.service.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen state receiver - monitors screen on/off and user present events
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val preferencesManager = PreferencesManager(context)

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned on")
                checkLockState(context, preferencesManager)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen turned off")
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present (unlocked)")
                checkLockState(context, preferencesManager)
            }
        }
    }

    private fun checkLockState(context: Context, preferencesManager: PreferencesManager) {
        CoroutineScope(Dispatchers.Main).launch {
            val isLocked = preferencesManager.isDeviceLockedSync()
            if (isLocked) {
                // Ensure lock screen is shown
                val intent = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            }
        }
    }
}
