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
import kotlinx.coroutines.delay
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
        private const val KEY_MESSAGE = "message"
        private const val KEY_TITLE = "title"
        
        // Message types
        private const val TYPE_DEVICE_LOCK_STATUS = "DEVICE_LOCK_STATUS"
        private const val TYPE_EMI_REMINDER = "EMI_REMINDER"
        
        // Action types (for lock/unlock)
        private const val ACTION_LOCK_DEVICE = "LOCK_DEVICE"
        private const val ACTION_UNLOCK_DEVICE = "UNLOCK_DEVICE"
        
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

        when (type) {
            TYPE_DEVICE_LOCK_STATUS -> handleLockUnlockCommand(action, data)
            TYPE_EMI_REMINDER -> handleEmiReminder(data)
            else -> Log.w(TAG, "Unknown message type: $type")
        }
    }
    
    /**
     * Handle device lock/unlock commands
     */
    private fun handleLockUnlockCommand(action: String?, data: Map<String, String>) {
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
     * Handle EMI payment reminder notification
     */
    private fun handleEmiReminder(data: Map<String, String>) {
        val title = data[KEY_TITLE] ?: "EMI Payment Reminder"
        val message = data[KEY_MESSAGE] ?: "You have a pending EMI payment. Please pay at the earliest."
        
        Log.d(TAG, "EMI Reminder - Title: $title, Message: $message")
        
        // Show notification to user
        NotificationHelperService.showEmiReminderNotification(
            context = this,
            title = title,
            message = message
        )
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

            // Register token with backend with retry logic
            registerTokenWithRetry(token)
        }
    }
    
    /**
     * Register FCM token with backend with exponential backoff retry
     */
    private suspend fun registerTokenWithRetry(token: String, maxRetries: Int = 3) {
        var attempt = 0
        
        while (attempt < maxRetries) {
            try {
                if (NetworkModule.isInitialized()) {
                    val result = deviceRepository.registerFcmToken(token)
                    result.onSuccess {
                        Log.d(TAG, "✅ FCM token registered with backend successfully")
                        return  // Success - exit retry loop
                    }
                    result.onFailure { error ->
                        Log.e(TAG, "❌ Failed to register FCM token (attempt ${attempt + 1}/$maxRetries): ${error.message}")
                        throw error
                    }
                } else {
                    Log.w(TAG, "⚠️ Network not initialized yet - will retry in ${(attempt + 1) * 5}s")
                    delay((attempt + 1) * 5000L)  // Wait before retry
                    attempt++
                }
            } catch (e: Exception) {
                attempt++
                if (attempt < maxRetries) {
                    val delayMs = (attempt * 5000L)  // Exponential backoff: 5s, 10s, 15s
                    Log.w(TAG, "Retrying FCM token registration in ${delayMs/1000}s...")
                    delay(delayMs)
                } else {
                    Log.e(TAG, "❌ Failed to register FCM token after $maxRetries attempts", e)
                    // Token is saved locally, will be sent during next app restart or manual retry
                }
            }
        }
    }
}
