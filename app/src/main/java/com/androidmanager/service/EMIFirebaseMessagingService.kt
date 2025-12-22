package com.androidmanager.service

import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.model.CommandPayload
import com.androidmanager.data.model.CommandType
import com.androidmanager.data.model.DeviceCommand
import com.androidmanager.data.remote.NetworkModule
import com.androidmanager.data.repository.DeviceRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging Service
 * Receives push notifications from backend for lock/unlock commands
 */
class EMIFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "EMIFirebaseService"
        
        // FCM data keys from backend
        private const val KEY_ACTION = "action"
        private const val KEY_TYPE = "type"
        private const val KEY_TIMESTAMP = "timestamp"
        
        // Action types
        private const val ACTION_LOCK_DEVICE = "LOCK_DEVICE"
        private const val ACTION_UNLOCK_DEVICE = "UNLOCK_DEVICE"
        private const val TYPE_DEVICE_LOCK_STATUS = "DEVICE_LOCK_STATUS"
        
        // Deduplication: ignore duplicate messages within this window
        private const val DEDUP_WINDOW_MS = 5000L // 5 seconds
    }
    
    // Track last processed message per action type
    private data class MessageRecord(
        val action: String,
        val timestamp: Long
    )
    
    private var lastLockMessage: MessageRecord? = null
    private var lastUnlockMessage: MessageRecord? = null

    private val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(this)
    }

    private val deviceRepository: DeviceRepository by lazy {
        DeviceRepository(preferencesManager)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "========================================")
        Log.d(TAG, "FCM MESSAGE RECEIVED!")
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Notification: ${remoteMessage.notification}")
        Log.d(TAG, "Data payload: ${remoteMessage.data}")
        Log.d(TAG, "========================================")

        // Handle data message
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Processing data message...")
            handleDataMessage(remoteMessage.data)
        } else {
            Log.w(TAG, "Received message with empty data payload - cannot process")
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data[KEY_TYPE]
        val action = data[KEY_ACTION]

        Log.d(TAG, "FCM data - type: $type, action: $action")

        // Only handle device lock status messages
        if (type != TYPE_DEVICE_LOCK_STATUS) {
            Log.w(TAG, "Unknown message type: $type")
            return
        }
        
        // Check for duplicate messages (deduplication)
        if (isDuplicateMessage(action)) {
            Log.w(TAG, "Ignoring duplicate $action message (received within ${DEDUP_WINDOW_MS}ms window)")
            return
        }

        when (action) {
            ACTION_LOCK_DEVICE -> {
                updateLastMessage(action)
                CommandExecutorService.executeLockFromFcm(this, "Payment overdue. Please contact shop.")
            }
            ACTION_UNLOCK_DEVICE -> {
                updateLastMessage(action)
                CommandExecutorService.executeUnlockFromFcm(this)
            }
            else -> {
                Log.w(TAG, "Unknown action: $action")
            }
        }
    }
    
    /**
     * Check if this message is a duplicate of a recently processed message
     */
    private fun isDuplicateMessage(action: String?): Boolean {
        if (action == null) return false
        
        val now = System.currentTimeMillis()
        val lastMessage = when (action) {
            ACTION_LOCK_DEVICE -> lastLockMessage
            ACTION_UNLOCK_DEVICE -> lastUnlockMessage
            else -> null
        }
        
        return lastMessage?.let { 
            (now - it.timestamp) < DEDUP_WINDOW_MS 
        } ?: false
    }
    
    /**
     * Update the last processed message timestamp
     */
    private fun updateLastMessage(action: String) {
        val now = System.currentTimeMillis()
        when (action) {
            ACTION_LOCK_DEVICE -> lastLockMessage = MessageRecord(action, now)
            ACTION_UNLOCK_DEVICE -> lastUnlockMessage = MessageRecord(action, now)
        }
        Log.d(TAG, "Updated last $action message timestamp: $now")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "========================================")
        Log.d(TAG, "NEW FCM TOKEN GENERATED")
        Log.d(TAG, "Token: $token")
        Log.d(TAG, "========================================")

        // Save token locally first
        CoroutineScope(Dispatchers.IO).launch {
            preferencesManager.setFcmToken(token)
            Log.d(TAG, "FCM token saved to preferences")

            // Register token with backend using IMEI (without PIN/location as setup already happened)
            try {
                if (NetworkModule.isInitialized()) {
                    val result = deviceRepository.registerFcmToken(token)
                    result.onSuccess {
                        Log.d(TAG, "FCM token registered with backend successfully")
                    }
                    result.onFailure { error ->
                        Log.e(TAG, "Failed to register FCM token: ${error.message}")
                    }
                } else {
                    Log.w(TAG, "Network not initialized yet - token will be registered later")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token on backend", e)
            }
        }
    }
}
