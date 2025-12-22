package com.androidmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.remote.NetworkModule
import com.androidmanager.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Package change receiver - monitors app installations and uninstallations
 */
class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_REMOVED -> {
                Log.d(TAG, "Package removed: $packageName")
                
                // If our app is being removed (shouldn't be possible as device owner)
                if (packageName == context.packageName) {
                    Log.w(TAG, "Our app is being removed!")
                }
            }
            Intent.ACTION_PACKAGE_ADDED -> {
                Log.d(TAG, "Package added: $packageName")
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (packageName == context.packageName) {
                    Log.d(TAG, "Our app was updated")
                    // Restart services after update
                    val preferencesManager = PreferencesManager(context)
                    CoroutineScope(Dispatchers.Main).launch {
                        // Re-initialize if needed
                    }
                }
            }
        }
    }
}
