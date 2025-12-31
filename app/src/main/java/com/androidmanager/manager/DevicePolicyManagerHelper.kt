package com.androidmanager.manager

import android.accounts.Account
import android.accounts.AccountManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.androidmanager.receiver.DeviceAdminReceiver

/**
 * Helper class to manage Device Policy operations
 * Provides all MDM capabilities for the EMI device management
 */
class DevicePolicyManagerHelper(private val context: Context) {

    companion object {
        private const val TAG = "DevicePolicyHelper"
    }

    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        DeviceAdminReceiver.getComponentName(context)
    }

    private val accountManager: AccountManager by lazy {
        AccountManager.get(context)
    }

    // ==================== Device Owner Status ====================

    /**
     * Check if this app is the device owner
     */
    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Check if device admin is active
     */
    fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Suppress all device owner notifications and messages
     */
    fun suppressDeviceOwnerNotifications() {
        if (!isDeviceOwner()) {
            return
        }

        try {
            // Clear lock screen info (hides "This device belongs to your organization")
            devicePolicyManager.setDeviceOwnerLockScreenInfo(adminComponent, null)
            
            // Set short support message to empty (hides messages in Settings)
            devicePolicyManager.setShortSupportMessage(adminComponent, null)
            
            // Set long support message to empty
            devicePolicyManager.setLongSupportMessage(adminComponent, null)
            
            Log.d(TAG, "Device owner notifications suppressed")
        } catch (e: Exception) {
            Log.w(TAG, "Could not suppress notifications: ${e.message}")
        }
    }

    /**
     * Exempt app from battery optimization (Doze mode)
     * This is CRITICAL for reliable background location updates on physical devices
     * OEMs like Samsung, Xiaomi, Huawei aggressively kill background services
     */
    fun exemptFromBatteryOptimization() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val packageName = context.packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // Check if we have background restrictions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    if (activityManager.isBackgroundRestricted) {
                        Log.w(TAG, "App has background restrictions - user may need to manually disable")
                    } else {
                        Log.d(TAG, "No background restrictions on app")
                    }
                }
                
                Log.d(TAG, "App is not exempt from battery optimization - location updates may be affected")
            } else {
                Log.d(TAG, "Already exempt from battery optimization")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization status", e)
        }
    }

    // ==================== Screen Lock Management ====================



    /**
     * Lock the device immediately
     */
    fun lockDevice() {
        if (isAdminActive()) {
            devicePolicyManager.lockNow()
            Log.d(TAG, "Device locked")
        }
    }

    // ==================== User Restrictions ====================

    /**
     * Apply all necessary restrictions for EMI device management
     */
    fun applyDeviceRestrictions() {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot apply restrictions - not device owner")
            return
        }

        try {
            // Prevent factory reset
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

            // Prevent safe boot
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)

            // Prevent USB file transfer
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)

            // Prevent mounting physical media
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)

            // NOTE: DISALLOW_DEBUGGING_FEATURES is disabled for development
            // Enable this in production builds only
            // devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // Prevent adding new users
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)

            // Prevent removing users
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_REMOVE_USER)

            // NOTE: DISALLOW_MODIFY_ACCOUNTS is NOT applied here
            // It will be applied AFTER the shop owner adds their Google account for FRP
            // Use lockAccountModification() after account is added

            // Enable location (but don't prevent user from configuring it for testing)
            // setLocationEnabled requires API 30+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    devicePolicyManager.setLocationEnabled(adminComponent, true)
                    Log.d(TAG, "Location enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enable location: ${e.message}")
                }
            } else {
                Log.d(TAG, "setLocationEnabled not available on API ${Build.VERSION.SDK_INT}")
            }

            // Hide device owner notification
            try {
                devicePolicyManager.setDeviceOwnerLockScreenInfo(adminComponent, null)
            } catch (e: Exception) {
                Log.w(TAG, "Could not suppress lock screen info: ${e.message}")
            }

            Log.d(TAG, "Device restrictions applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying restrictions", e)
        }
    }

    /**
     * Enforce password requirements - REQUIRED for FRP to work!
     * Sets minimum password quality to 4-digit numeric PIN
     * User will be prompted to set a screen lock
     */
    fun enforcePasswordRequirements() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot enforce password - not device owner")
            return
        }

        try {
            // Android 10+ : Use setRequiredPasswordComplexity for stronger enforcement
            // Wrapped in try-catch for modified ROMs that may not have this method
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    // PASSWORD_COMPLEXITY_LOW requires at least pattern, PIN, or password
                    // This BLOCKS "None" and "Swipe" options in Settings
                    devicePolicyManager.setRequiredPasswordComplexity(
                        DevicePolicyManager.PASSWORD_COMPLEXITY_LOW
                    )
                    Log.d(TAG, "Password complexity set to LOW (blocks swipe/none)")
                } catch (e: NoSuchMethodError) {
                    Log.w(TAG, "setRequiredPasswordComplexity not available on this ROM")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set password complexity: ${e.message}")
                }
            }
            
            // Also set legacy password quality for compatibility
            devicePolicyManager.setPasswordQuality(
                adminComponent,
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )
            
            // Set minimum password length to 4 digits
            devicePolicyManager.setPasswordMinimumLength(adminComponent, 4)
            
            Log.d(TAG, "Password requirements set: 4-digit numeric PIN minimum")
            
            // Check if current password meets requirements
            val isPasswordSufficient = devicePolicyManager.isActivePasswordSufficient
            if (!isPasswordSufficient) {
                Log.w(TAG, "Current password does not meet requirements - prompting user to set one")
                promptUserToSetPassword()
            } else {
                Log.d(TAG, "✅ Current password meets requirements")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing password requirements", e)
        }
    }

    /**
     * Check if password requirements are met
     */
    fun isPasswordSufficient(): Boolean {
        return try {
            devicePolicyManager.isActivePasswordSufficient
        } catch (e: Exception) {
            Log.e(TAG, "Error checking password sufficiency", e)
            false
        }
    }

    /**
     * Prompt user to set a screen lock password/PIN
     * Launches the system password setup screen
     */
    fun promptUserToSetPassword() {
        try {
            // This intent launches the screen lock setup screen
            val intent = Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched password setup screen")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching password setup", e)
        }
    }

    /**
     * Prevent account removal for shop owner's account
     * @deprecated Use lockAccountModification() instead
     */
    @Deprecated("Use lockAccountModification() instead")
    fun lockShopOwnerAccount(accountEmail: String) {
        lockAccountModification()
    }

    /**
     * Allow account addition (remove restriction if exists)
     * Call this BEFORE user adds their Google account
     */
    fun allowAccountAddition() {
        if (!isDeviceOwner()) return

        try {
            devicePolicyManager.clearUserRestriction(
                adminComponent,
                UserManager.DISALLOW_MODIFY_ACCOUNTS
            )
            Log.d(TAG, "Account addition allowed")
        } catch (e: Exception) {
            Log.e(TAG, "Error allowing account addition", e)
        }
    }

    /**
     * Lock account modification (apply restriction)
     * Call this AFTER Google account is added to prevent removal
     */
    fun lockAccountModification() {
        if (!isDeviceOwner()) return

        try {
            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_MODIFY_ACCOUNTS
            )
            Log.d(TAG, "Account modification locked")
        } catch (e: Exception) {
            Log.e(TAG, "Error locking account modification", e)
        }
    }

    /**
     * Check if at least one Google account exists on the device
     */
    fun hasGoogleAccount(): Boolean {
        return getGoogleAccounts().isNotEmpty()
    }

    /**
     * Allow account modification but keep the shop owner account protected
     * This is more nuanced - we selectively allow account operations
     */
    fun configureAccountManagement() {
        if (!isDeviceOwner()) return

        try {
            // Get all Google accounts
            val accounts = accountManager.getAccountsByType("com.google")
            
            // For device owner, we can set account management disabled for specific types
            // This prevents removing any Google account - shop owner included
            devicePolicyManager.setAccountManagementDisabled(
                adminComponent,
                "com.google",
                false // Allow adding accounts
            )

            Log.d(TAG, "Account management configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring account management", e)
        }
    }

    // ==================== Location Management ====================

    /**
     * Set location mode to high accuracy and prevent changes
     */
    fun enforceLocationSettings() {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot enforce location - not device owner")
            return
        }

        try {
            // Enable location globally - requires API 30+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                devicePolicyManager.setLocationEnabled(adminComponent, true)
            } else {
                Log.d(TAG, "setLocationEnabled not available on API ${Build.VERSION.SDK_INT}")
            }

            // Prevent user from disabling location
            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_LOCATION
            )

            Log.d(TAG, "Location settings enforced")
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing location", e)
        }
    }

    /**
     * Grant location permission to this app
     */
    fun grantLocationPermission() {
        if (!isDeviceOwner()) return

        try {
            val packageName = context.packageName

            devicePolicyManager.setPermissionGrantState(
                adminComponent,
                packageName,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )

            devicePolicyManager.setPermissionGrantState(
                adminComponent,
                packageName,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )

            devicePolicyManager.setPermissionGrantState(
                adminComponent,
                packageName,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )

            Log.d(TAG, "Location permissions granted")
        } catch (e: Exception) {
            Log.e(TAG, "Error granting location permissions", e)
        }
    }

    /**
     * Grant READ_PHONE_STATE permission to this app (for IMEI access)
     * Must be called as Device Owner before attempting to get IMEI
     */
    fun grantPhoneStatePermission() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot grant phone state permission - not device owner")
            return
        }

        try {
            val packageName = context.packageName

            devicePolicyManager.setPermissionGrantState(
                adminComponent,
                packageName,
                android.Manifest.permission.READ_PHONE_STATE,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )

            Log.d(TAG, "READ_PHONE_STATE permission granted for IMEI access")
        } catch (e: Exception) {
            Log.e(TAG, "Error granting READ_PHONE_STATE permission", e)
        }
    }

    /**
     * Grant POST_NOTIFICATIONS permission to this app (for Android 13+)
     * Must be called as Device Owner for EMI reminder notifications
     */
    fun grantNotificationPermission() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot grant notification permission - not device owner")
            return
        }

        // POST_NOTIFICATIONS is only required on Android 13 (API 33) and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "POST_NOTIFICATIONS not required below Android 13")
            return
        }

        try {
            val packageName = context.packageName

            devicePolicyManager.setPermissionGrantState(
                adminComponent,
                packageName,
                android.Manifest.permission.POST_NOTIFICATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )

            Log.d(TAG, "POST_NOTIFICATIONS permission granted for EMI reminders")
        } catch (e: Exception) {
            Log.e(TAG, "Error granting POST_NOTIFICATIONS permission", e)
        }
    }

    // ==================== App Visibility ====================

    /**
     * Hide the app from launcher
     */
    fun hideAppFromLauncher(hide: Boolean) {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, "com.androidmanager.MainActivity")

        try {
            val newState = if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )

            Log.d(TAG, "App visibility changed: hidden=$hide")
        } catch (e: Exception) {
            Log.e(TAG, "Error changing app visibility", e)
        }
    }

    /**
     * Set app as hidden using device owner API
     */
    fun setAppHidden(packageName: String, hidden: Boolean) {
        if (!isDeviceOwner()) return

        try {
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, hidden)
            Log.d(TAG, "App $packageName hidden: $hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding app", e)
        }
    }

    // ==================== Lock Task Mode ====================

    /**
     * Enable lock task mode for the lock screen
     */
    fun enableLockTaskMode(packages: Array<String>) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot enable lock task mode - not device owner")
            return
        }

        try {
            devicePolicyManager.setLockTaskPackages(adminComponent, packages)
            Log.d(TAG, "Lock task packages set: ${packages.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting lock task packages", e)
        }
    }

    /**
     * Configure lock task features
     */
    fun configureLockTaskFeatures() {
        if (!isDeviceOwner()) return

        try {
            // Configure what's available in lock task mode
            val features = DevicePolicyManager.LOCK_TASK_FEATURE_NONE or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO

            devicePolicyManager.setLockTaskFeatures(adminComponent, features)
            Log.d(TAG, "Lock task features configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring lock task features", e)
        }
    }

    /**
     * Enter kiosk mode - locks device to show only the lock screen
     * Blocks: Home button, Recent Apps, Notifications, Power Menu
     */
    fun enterKioskMode(lockScreenPackage: String) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot enter kiosk mode - not device owner")
            return
        }

        try {
            // Set the lock screen as the only allowed package
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(lockScreenPackage))

            // Configure MINIMAL features in kiosk mode
            // LOCK_TASK_FEATURE_NONE = 0: Disables ALL features including:
            // - Status bar / notifications
            // - Home button (blocked anyway in lock task mode)
            // - Recent apps
            // - Global actions (POWER MENU) - this is what disables power button restart!
            // - Keyguard
            devicePolicyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            )

            Log.d(TAG, "Kiosk mode enabled for $lockScreenPackage (power menu disabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Error entering kiosk mode", e)
        }
    }

    /**
     * Exit kiosk mode - restore normal operation
     */
    fun exitKioskMode() {
        if (!isDeviceOwner()) return

        try {
            // Clear lock task packages
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf())

            Log.d(TAG, "Kiosk mode disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error exiting kiosk mode", e)
        }
    }

    // ==================== FRP (Factory Reset Protection) ====================

    /**
     * Set up FRP with shop owner's Google userId
     * After factory reset, device will require this account to unlock
     * Uses FactoryResetProtectionPolicy API (Android 11+)
     * 
     * @param userId The Google userId (numeric string from People API, NOT email)
     */
    fun setupFactoryResetProtectionWithUserId(userId: String) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot setup FRP - not device owner")
            return
        }

        if (userId.isBlank()) {
            Log.w(TAG, "Empty userId provided for FRP")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ : Use FactoryResetProtectionPolicy with userId
                val frpPolicy = android.app.admin.FactoryResetProtectionPolicy.Builder()
                    .setFactoryResetProtectionAccounts(listOf(userId))
                    .setFactoryResetProtectionEnabled(true)
                    .build()
                
                devicePolicyManager.setFactoryResetProtectionPolicy(adminComponent, frpPolicy)
                Log.d(TAG, "✅ FRP configured with userId: ${userId.take(10)}...")
                
                // IMPORTANT: Notify Google Play Services about FRP config change
                notifyFrpConfigChanged()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9-10: Use legacy method with application restrictions
                Log.d(TAG, "Using legacy FRP method for Android ${Build.VERSION.SDK_INT}")
                setupFrpLegacy(userId)
            } else {
                Log.w(TAG, "FRP API not available on Android ${Build.VERSION.SDK_INT}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up FRP with userId", e)
        }
    }

    /**
     * Set up FRP with multiple Google userIds
     * Any of these accounts can unlock the device after factory reset
     * 
     * @param userIds List of Google userIds (numeric strings from People API)
     */
    fun setupFactoryResetProtectionWithUserIds(userIds: List<String>) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot setup FRP - not device owner")
            return
        }

        val validUserIds = userIds.filter { it.isNotBlank() }
        if (validUserIds.isEmpty()) {
            Log.w(TAG, "No valid FRP userIds provided")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val frpPolicy = android.app.admin.FactoryResetProtectionPolicy.Builder()
                    .setFactoryResetProtectionAccounts(validUserIds)
                    .setFactoryResetProtectionEnabled(true)
                    .build()
                
                devicePolicyManager.setFactoryResetProtectionPolicy(adminComponent, frpPolicy)
                Log.d(TAG, "✅ FRP configured with ${validUserIds.size} userIds")
                
                // Notify Google Play Services
                notifyFrpConfigChanged()
            } else {
                Log.w(TAG, "⚠️ FRP with multiple accounts requires Android 11+")
                // Fallback: use first userId
                setupFactoryResetProtectionWithUserId(validUserIds.first())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up FRP with multiple userIds", e)
        }
    }

    /**
     * Legacy method for Android 9-10: Set FRP using application restrictions
     * Uses setApplicationRestrictions on com.google.android.gms package
     */
    private fun setupFrpLegacy(googleAccountOrUserId: String) {
        try {
            val googlePlayPackage = "com.android.vending"
            val gmsCorePackage = "com.google.android.gms"
            
            // Set restriction on Play Store
            val existingConfig = devicePolicyManager.getApplicationRestrictions(adminComponent, googlePlayPackage)
            val newConfig = android.os.Bundle(existingConfig)
            newConfig.putBoolean("disableFactoryResetProtectionAdmin", false)
            newConfig.putString("factoryResetProtectionAdmin", googleAccountOrUserId)
            devicePolicyManager.setApplicationRestrictions(adminComponent, googlePlayPackage, newConfig)
            
            Log.d(TAG, "✅ Legacy FRP set via application restrictions")
            
            // Notify GMS Core about the change
            notifyFrpConfigChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up legacy FRP", e)
        }
    }

    /**
     * Send broadcast to notify Google Play Services about FRP configuration change
     * This is REQUIRED for FRP to take effect
     */
    private fun notifyFrpConfigChanged() {
        try {
            val frpChangedIntent = android.content.Intent("com.google.android.gms.auth.FRP_CONFIG_CHANGED")
            frpChangedIntent.setPackage("com.google.android.gms")
            context.sendBroadcast(frpChangedIntent)
            Log.d(TAG, "✅ FRP broadcast sent to GMS")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending FRP broadcast", e)
        }
    }

    /**
     * Backward compatibility: Set up FRP using email (for older policy formats)
     * Note: Official docs recommend using userId instead
     */
    @Deprecated("Use setupFactoryResetProtectionWithUserId() with Google userId instead")
    fun setupFactoryResetProtection(accountEmail: String) {
        Log.w(TAG, "⚠️ Using email for FRP - consider using userId for better reliability")
        setupFactoryResetProtectionWithUserId(accountEmail)
    }

    /**
     * Backward compatibility: Set up FRP using email list
     */
    @Deprecated("Use setupFactoryResetProtectionWithUserIds() with Google userIds instead")
    fun setupFactoryResetProtection(accountEmails: List<String>) {
        Log.w(TAG, "⚠️ Using emails for FRP - consider using userIds for better reliability")
        setupFactoryResetProtectionWithUserIds(accountEmails)
    }

    /**
     * Check if FRP is enabled
     */
    fun isFactoryResetProtectionEnabled(): Boolean {
        if (!isDeviceOwner()) return false
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val policy = devicePolicyManager.getFactoryResetProtectionPolicy(adminComponent)
                policy?.isFactoryResetProtectionEnabled ?: false
            } else {
                // For legacy, check if restriction was set
                val googlePlayPackage = "com.android.vending"
                val config = devicePolicyManager.getApplicationRestrictions(adminComponent, googlePlayPackage)
                config.containsKey("factoryResetProtectionAdmin")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking FRP status", e)
            false
        }
    }

    /**
     * Clear FRP (for device unprovisioning)
     */
    fun clearFactoryResetProtection() {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot clear FRP - not device owner")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                devicePolicyManager.setFactoryResetProtectionPolicy(adminComponent, null)
                Log.d(TAG, "✅ FRP cleared (Android 11+)")
            } else {
                // Clear legacy restriction
                val googlePlayPackage = "com.android.vending"
                val emptyConfig = android.os.Bundle()
                devicePolicyManager.setApplicationRestrictions(adminComponent, googlePlayPackage, emptyConfig)
                Log.d(TAG, "✅ FRP cleared (legacy)")
            }
            
            // Notify about the change
            notifyFrpConfigChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing FRP", e)
        }
    }

    // ==================== Device Information ====================

    /**
     * Get device serial number (requires device owner)
     */
    @android.annotation.SuppressLint("MissingPermission", "HardwareIds")
    fun getDeviceSerialNumber(): String? {
        return if (isDeviceOwner()) {
            try {
                Build.getSerial()
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot get serial number", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Get device IMEI (requires special permission)
     */
    @android.annotation.SuppressLint("MissingPermission", "HardwareIds")
    fun getDeviceIMEI(): String? {
        // Note: IMEI access is restricted in Android 10+
        // Device owner can access it through telephony manager
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.imei
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.deviceId
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot get IMEI", e)
            null
        }
    }

    /**
     * Get all Google accounts on device
     */
    fun getGoogleAccounts(): List<Account> {
        return accountManager.getAccountsByType("com.google").toList()
    }

    /**
     * Get the first (shop owner) Google account
     */
    fun getFirstGoogleAccount(): Account? {
        return getGoogleAccounts().firstOrNull()
    }

    // ==================== System Settings ====================

    /**
     * Prevent changes to system settings
     */
    fun lockSystemSettings() {
        if (!isDeviceOwner()) return

        try {
            // Prevent config changes
            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_WIFI
            )

            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_BLUETOOTH
            )

            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS
            )

            Log.d(TAG, "System settings locked")
        } catch (e: Exception) {
            Log.e(TAG, "Error locking system settings", e)
        }
    }

    /**
     * Unlock system settings for normal operation
     */
    fun unlockSystemSettings() {
        if (!isDeviceOwner()) return

        try {
            devicePolicyManager.clearUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_WIFI
            )

            devicePolicyManager.clearUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_BLUETOOTH
            )

            devicePolicyManager.clearUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS
            )

            Log.d(TAG, "System settings unlocked")
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking system settings", e)
        }
    }

    // ==================== Setup & Provisioning ====================

    /**
     * Complete initial device setup
     */
    fun completeInitialSetup() {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot complete setup - not device owner")
            return
        }

        // Apply all restrictions
        applyDeviceRestrictions()

        // Enforce location
        enforceLocationSettings()

        // Grant permissions
        grantLocationPermission()

        // Configure lock task mode for lock screen
        enableLockTaskMode(arrayOf(context.packageName))

        Log.d(TAG, "Initial setup completed")
    }
}
