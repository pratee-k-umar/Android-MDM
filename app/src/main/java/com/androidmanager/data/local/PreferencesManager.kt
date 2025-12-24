package com.androidmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "emi_device_manager")

/**
 * Manages app preferences using DataStore
 */
class PreferencesManager(private val context: Context) {

    companion object {
        // Setup keys
        private val KEY_IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        
        // Account keys
        private val KEY_LOCKED_ACCOUNT_EMAIL = stringPreferencesKey("locked_account_email")
        private val KEY_LOCKED_ACCOUNT_ID = stringPreferencesKey("locked_account_id")
        
        // Lock state keys
        private val KEY_IS_DEVICE_LOCKED = booleanPreferencesKey("is_device_locked")
        private val KEY_LOCK_MESSAGE = stringPreferencesKey("lock_message")
        private val KEY_LOCK_TIMESTAMP = longPreferencesKey("lock_timestamp")
        
        // Backend keys
        private val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        
        // Device info
        private val KEY_IMEI = stringPreferencesKey("imei")
        private val KEY_SERIAL_NUMBER = stringPreferencesKey("serial_number")
        
        // Shop owner info
        private val KEY_SHOP_ID = stringPreferencesKey("shop_id")
        private val KEY_SHOP_NAME = stringPreferencesKey("shop_name")
        
        // Hidden mode
        private val KEY_IS_HIDDEN = booleanPreferencesKey("is_hidden")
    }

    // Setup state
    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_SETUP_COMPLETE] ?: false
    }

    suspend fun setSetupComplete(complete: Boolean) {
        // Write to DataStore (for normal app use)
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_SETUP_COMPLETE] = complete
        }
        
        // ALSO write to SharedPreferences (for boot persistence)
        // DataStore writes asynchronously and may not be ready at boot
        // SharedPreferences is synchronous and guaranteed to persist
        val sharedPrefs = context.getSharedPreferences("emi_device_manager_boot", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("is_setup_complete", complete).apply()
    }

    // Device ID
    val deviceId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID]
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = id
        }
    }

    suspend fun getDeviceIdSync(): String? {
        return context.dataStore.data.first()[KEY_DEVICE_ID]
    }

    // FCM Token
    val fcmToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_FCM_TOKEN]
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FCM_TOKEN] = token
        }
    }

    suspend fun getFcmToken(): String? {
        return context.dataStore.data.first()[KEY_FCM_TOKEN]
    }

    // Locked Account
    val lockedAccountEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCKED_ACCOUNT_EMAIL]
    }

    suspend fun setLockedAccount(email: String, accountId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCKED_ACCOUNT_EMAIL] = email
            prefs[KEY_LOCKED_ACCOUNT_ID] = accountId
        }
    }

    suspend fun getLockedAccountEmail(): String? {
        return context.dataStore.data.first()[KEY_LOCKED_ACCOUNT_EMAIL]
    }

    // Device Lock State
    val isDeviceLocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_DEVICE_LOCKED] ?: false
    }

    suspend fun isDeviceLockedSync(): Boolean {
        return context.dataStore.data.first()[KEY_IS_DEVICE_LOCKED] ?: false
    }

    suspend fun setDeviceLocked(locked: Boolean, message: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_DEVICE_LOCKED] = locked
            if (locked && message != null) {
                prefs[KEY_LOCK_MESSAGE] = message
                prefs[KEY_LOCK_TIMESTAMP] = System.currentTimeMillis()
            } else if (!locked) {
                prefs.remove(KEY_LOCK_MESSAGE)
                prefs.remove(KEY_LOCK_TIMESTAMP)
            }
        }
    }

    val lockMessage: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCK_MESSAGE]
    }

    suspend fun getLockMessage(): String? {
        return context.dataStore.data.first()[KEY_LOCK_MESSAGE]
    }

    // Backend URL
    val backendUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKEND_URL]
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKEND_URL] = url
        }
    }

    suspend fun getBackendUrl(): String? {
        return context.dataStore.data.first()[KEY_BACKEND_URL]
    }

    // Auth Token
    val authToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTH_TOKEN]
    }

    suspend fun setAuthToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
        }
    }

    suspend fun getAuthToken(): String? {
        return context.dataStore.data.first()[KEY_AUTH_TOKEN]
    }

    // Device Info
    suspend fun setDeviceInfo(imei: String?, serialNumber: String?) {
        context.dataStore.edit { prefs ->
            imei?.let { prefs[KEY_IMEI] = it }
            serialNumber?.let { prefs[KEY_SERIAL_NUMBER] = it }
        }
    }

    suspend fun setImei(imei: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IMEI] = imei
        }
    }

    suspend fun getImei(): String? {
        return context.dataStore.data.first()[KEY_IMEI]
    }

    val imei: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_IMEI]
    }

    // Shop Info
    suspend fun setShopInfo(shopId: String, shopName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOP_ID] = shopId
            prefs[KEY_SHOP_NAME] = shopName
        }
    }

    val shopId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOP_ID]
    }

    // Hidden Mode
    val isHidden: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_HIDDEN] ?: false
    }

    suspend fun setHidden(hidden: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_HIDDEN] = hidden
        }
    }

    // Clear all data (for testing only)
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
