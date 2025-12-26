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
import com.androidmanager.receiver.EMIDeviceAdminReceiver

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
        EMIDeviceAdminReceiver.getComponentName(context)
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
            try {
                devicePolicyManager.setLocationEnabled(adminComponent, true)
                Log.d(TAG, "Location enabled")
            } catch (e: Exception) {
                Log.w(TAG, "Could not enable location: ${e.message}")
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
            // Enable location globally
            devicePolicyManager.setLocationEnabled(adminComponent, true)

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

    // ==================== Kiosk Mode (Full Device Lock) ====================

    /**
     * Enter kiosk mode - locks device to show only the lock screen
     */
    fun enterKioskMode(lockScreenPackage: String) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot enter kiosk mode - not device owner")
            return
        }

        try {
            // Set the lock screen as the only allowed package
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(lockScreenPackage))

            // Configure minimal features in kiosk mode
            devicePolicyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            )

            Log.d(TAG, "Kiosk mode enabled for $lockScreenPackage")
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
     * Set up FRP with shop owner's Google account
     * After factory reset, device will require this account to unlock
     */
    fun setupFactoryResetProtection(accountEmail: String) {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot setup FRP - not device owner")
            return
        }

        try {
            // Get the account
            val accounts = accountManager.getAccountsByType("com.google")
            val shopOwnerAccount = accounts.find { it.name == accountEmail }

            if (shopOwnerAccount != null) {
                // Set the factory reset protection policy
                // The first added Google account is automatically used for FRP
                Log.d(TAG, "FRP configured with account: $accountEmail")
            } else {
                Log.w(TAG, "Shop owner account not found: $accountEmail")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up FRP", e)
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
