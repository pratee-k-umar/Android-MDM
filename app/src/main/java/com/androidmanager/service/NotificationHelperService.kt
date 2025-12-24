package com.androidmanager.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.androidmanager.R
import com.androidmanager.MainActivity

object NotificationHelperService {
    
    private const val TAG = "NotificationHelper"
    private const val EMI_REMINDER_CHANNEL_ID = "emi_reminder_channel"
    private const val EMI_REMINDER_CHANNEL_NAME = "EMI Payment Reminders"
    private const val EMI_NOTIFICATION_ID = 1001
    
    /**
     * Show EMI reminder notification to the user
     */
    fun showEmiReminderNotification(
        context: Context,
        title: String,
        message: String
    ) {
        Log.d(TAG, "Attempting to show EMI reminder notification")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Message: $message")
        
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "POST_NOTIFICATIONS permission: ${if (hasPermission) "GRANTED" else "DENIED"}")
            
            if (!hasPermission) {
                Log.w(TAG, "Notification permission not granted - notification will not be shown")
                return
            }
        }
        
        // Create notification channel (required for Android 8.0+)
        createNotificationChannel(context)
        
        // Intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(context, EMI_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        Log.d(TAG, "Notification built successfully")
        
        // Show notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(EMI_NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ Notification shown successfully with ID: $EMI_NOTIFICATION_ID")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show notification", e)
        }
    }
    
    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EMI_REMINDER_CHANNEL_ID,
                EMI_REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for EMI payment reminders"
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $EMI_REMINDER_CHANNEL_ID")
        }
    }
}
