package com.androidmanager.util

/**
 * Application constants
 */
object Constants {
    
    // ============================================================
    // CONFIGURE THESE VALUES BEFORE BUILDING THE APP
    // ============================================================
    
    // Backend API - Matches the API documentation base URL
    const val BACKEND_URL = "https://emi-backend-j2qc.onrender.com/"
    
    // Shop Information - CHANGE THESE TO YOUR SHOP DETAILS
    const val SHOP_ID = "SHOP_001"
    const val SHOP_NAME = "Your Shop Name"
    const val SHOP_CONTACT = "+91-XXXXXXXXXX"
    
    // ============================================================
    
    const val API_TIMEOUT_SECONDS = 30L
    
    // Intervals (in milliseconds)
    const val LOCATION_UPDATE_INTERVAL = 15 * 60 * 1000L // 15 minutes
    
    // Lock screen
    const val LOCK_SCREEN_DEFAULT_MESSAGE = "Payment overdue. Please contact the shop owner to unlock your device."
    
    // Notification channels
    const val NOTIFICATION_CHANNEL_SERVICE = "emi_device_service"
    const val NOTIFICATION_CHANNEL_ALERTS = "emi_device_alerts"
    
    // Notification IDs
    const val NOTIFICATION_ID_SERVICE = 1001
    const val NOTIFICATION_ID_LOCK = 1002
    
    // Intent actions
    const val ACTION_DEVICE_LOCKED = "com.androidmanager.ACTION_DEVICE_LOCKED"
    const val ACTION_DEVICE_UNLOCKED = "com.androidmanager.ACTION_DEVICE_UNLOCKED"
    const val ACTION_GET_LOCATION = "com.androidmanager.ACTION_GET_LOCATION"
    
    // FCM keys
    const val FCM_KEY_COMMAND_TYPE = "commandType"
    const val FCM_KEY_COMMAND_ID = "commandId"
    const val FCM_KEY_MESSAGE = "message"
    const val FCM_KEY_PAYLOAD = "payload"
    
    // PIN requirements
    const val PIN_LENGTH = 4
}
