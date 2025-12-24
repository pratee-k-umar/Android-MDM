package com.androidmanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.remote.NetworkModule
import com.androidmanager.data.repository.DeviceRepository
import com.androidmanager.manager.DevicePolicyManagerHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class - initializes core components
 */
class EMIDeviceManagerApp : Application() {

    companion object {
        private const val TAG = "EMIDeviceManagerApp"
        const val NOTIFICATION_CHANNEL_ID = "device_lock_channel"
        
        lateinit var instance: EMIDeviceManagerApp
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var policyHelper: DevicePolicyManagerHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "Application started")

        // Create notification channel for FCM
        createNotificationChannel()

        // Initialize preferences
        preferencesManager = PreferencesManager(this)

        // Initialize policy helper
        policyHelper = DevicePolicyManagerHelper(this)

        // Initialize network if backend URL is configured
        initializeNetwork()

        // Request FCM token explicitly (important for fresh emulators)
        requestFcmToken()

        // Log device owner status
        Log.d(TAG, "Is Device Owner: ${policyHelper.isDeviceOwner()}")
        Log.d(TAG, "Is Admin Active: ${policyHelper.isAdminActive()}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Device Lock Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for device lock/unlock status"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $NOTIFICATION_CHANNEL_ID")
        }
    }

    private fun initializeNetwork() {
        applicationScope.launch {
            try {
                val backendUrl = preferencesManager.getBackendUrl()
                if (backendUrl != null) {
                    NetworkModule.initialize(backendUrl)
                    Log.d(TAG, "Network initialized with URL: $backendUrl")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing network", e)
            }
        }
    }

    /**
     * Explicitly request FCM token
     * This is important for fresh emulators where Firebase may not automatically generate tokens
     */
    private fun requestFcmToken() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "============================================")
                Log.d(TAG, "STARTING FCM TOKEN REQUEST")
                Log.d(TAG, "============================================")
                
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    Log.d(TAG, "FCM token task completed. Success: ${task.isSuccessful}")
                    
                    if (!task.isSuccessful) {
                        Log.e(TAG, "====== FCM TOKEN FETCH FAILED ======")
                        Log.e(TAG, "Exception: ${task.exception}")
                        Log.e(TAG, "Exception class: ${task.exception?.javaClass?.name}")
                        Log.e(TAG, "Exception message: ${task.exception?.message}")
                        Log.e(TAG, "Exception cause: ${task.exception?.cause}")
                        task.exception?.printStackTrace()
                        Log.e(TAG, "====================================")
                        return@addOnCompleteListener
                    }

                    // Get new FCM token
                    val token = task.result
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "✅ FCM TOKEN RETRIEVED SUCCESSFULLY")
                    Log.d(TAG, "Token length: ${token?.length ?: 0}")
                    Log.d(TAG, "Token: $token")
                    Log.d(TAG, "========================================")

                    if (token == null || token.isEmpty()) {
                        Log.e(TAG, "FCM token is null or empty!")
                        return@addOnCompleteListener
                    }

                    // Save token locally
                    applicationScope.launch {
                        preferencesManager.setFcmToken(token)
                        Log.d(TAG, "FCM token saved to preferences")

                        // Register with backend if network is initialized
                        try {
                            if (NetworkModule.isInitialized()) {
                                val repository = DeviceRepository(preferencesManager)
                                val result = repository.registerFcmToken(token)
                                result.onSuccess {
                                    Log.d(TAG, "FCM token registered with backend successfully")
                                }
                                result.onFailure { error ->
                                    Log.e(TAG, "Failed to register FCM token: ${error.message}")
                                }
                            } else {
                                Log.d(TAG, "Network not initialized - FCM token will be registered later")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error registering FCM token", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "====== FCM TOKEN REQUEST EXCEPTION ======")
                Log.e(TAG, "Error requesting FCM token", e)
                Log.e(TAG, "Exception: ${e.javaClass.name}")
                Log.e(TAG, "Message: ${e.message}")
                Log.e(TAG, "Cause: ${e.cause}")
                e.printStackTrace()
                Log.e(TAG, "=========================================")
            }
        }
    }
}
