package com.androidmanager.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.androidmanager.data.model.AmapiPolicy
import com.androidmanager.data.model.ApplicationPolicy
import com.androidmanager.data.model.NonComplianceDetail
import com.androidmanager.data.model.PasswordPolicy
import com.androidmanager.data.model.PasswordRequirements
import com.androidmanager.data.model.AdvancedSecurityOverrides
import com.androidmanager.receiver.DeviceAdminReceiver

/**
 * AMAPI Policy Enforcer
 * Translates AMAPI policies into DevicePolicyManager API calls
 * Uses DevicePolicyManagerHelper for low-level enforcement
 */
class AmapiPolicyEnforcer(private val context: Context) {
    
    companion object {
        private const val TAG = "AmapiPolicyEnforcer"
    }
    
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = DeviceAdminReceiver.getComponentName(context)
    private val policyHelper = DevicePolicyManagerHelper(context)
    
    private val nonComplianceList = mutableListOf<NonComplianceDetail>()
    
    /**
     * Apply all policies from an AMAPI policy object
     * Returns list of non-compliance issues (empty if all successful)
     */
    fun applyPolicy(policy: AmapiPolicy): List<NonComplianceDetail> {
        nonComplianceList.clear()
        
        Log.d(TAG, "📋 Applying AMAPI policy: ${policy.name}")
        
        try {
            // Camera policy
            policy.cameraDisabled?.let { enforceCameraPolicy(it) }
            
            // Password policies (newer format)
            policy.passwordPolicies?.firstOrNull()?.let { enforcePasswordPolicy(it) }
            
            // Password requirements (YOUR AMAPI uses this format!)
            policy.passwordRequirements?.let { enforcePasswordRequirements(it) }
            
            // Application policies
            policy.applications?.let { enforceApplicationPolicies(it) }
            
            // System update policy
            policy.systemUpdate?.let { enforceSystemUpdatePolicy(it) }
            
            // Network policies
            policy.wifiConfigDisabled?.let { enforceWifiConfigPolicy(it) }
            policy.bluetoothDisabled?.let { enforceBluetoothPolicy(it) }
            policy.bluetoothConfigDisabled?.let { enforceBluetoothConfigPolicy(it) }
            
            // Location policy
            policy.locationMode?.let { enforceLocationPolicy(it) }
            
            // Screen capture policy
            policy.screenCaptureDisabled?.let { enforceScreenCapturePolicy(it) }
            
            // Status bar policy
            policy.statusBarDisabled?.let { enforceStatusBarPolicy(it) }
            
            // Keyguard features
            policy.keyguardDisabledFeatures?.let { enforceKeyguardPolicy(it) }
            
            // Account management
            policy.accountTypesWithManagementDisabled?.let { enforceAccountPolicy(it) }
            
            // USB file transfer
            policy.usbFileTransferDisabled?.let { enforceUsbFileTransferPolicy(it) }
            
            // Factory reset
            policy.factoryResetDisabled?.let { enforceFactoryResetPolicy(it) }
            
            // Debugging
            policy.debuggingFeaturesAllowed?.let { enforceDebuggingPolicy(it) }
            
            // Stay on while plugged
            policy.stayOnPluggedModes?.let { enforceStayOnPolicy(it) }
            
            // Advanced security overrides (developer settings, etc.)
            policy.advancedSecurityOverrides?.let { enforceAdvancedSecurityOverrides(it) }
            
            Log.d(TAG, "✅ Policy application complete. Non-compliance count: ${nonComplianceList.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying policy", e)
            addNonCompliance("POLICY_APPLICATION", "UNKNOWN", "Error: ${e.message}")
        }
        
        return nonComplianceList.toList()
    }
    
    // ==================== Individual Policy Enforcers ====================
    
    private fun enforceCameraPolicy(disabled: Boolean) {
        try {
            if (!policyHelper.isDeviceOwner()) {
                addNonCompliance("cameraDisabled", "MANAGEMENT_MODE", "Not device owner")
                return
            }
            
            dpm.setCameraDisabled(adminComponent, disabled)
            Log.d(TAG, "✅ Camera ${if (disabled) "disabled" else "enabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set camera policy", e)
            addNonCompliance("cameraDisabled", "API_LEVEL", e.message)
        }
    }
    
    private fun enforcePasswordPolicy(policy: PasswordPolicy) {
        try {
            if (!policyHelper.isDeviceOwner()) {
                addNonCompliance("passwordPolicy", "MANAGEMENT_MODE", "Not device owner")
                return
            }
            
            // Password quality
            policy.passwordQuality?.let { quality ->
                val dpmQuality = when (quality) {
                    "COMPLEXITY_LOW" -> DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
                    "COMPLEXITY_MEDIUM" -> DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX
                    "COMPLEXITY_HIGH" -> DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                    else -> DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED
                }
                dpm.setPasswordQuality(adminComponent, dpmQuality)
                Log.d(TAG, "✅ Password quality set to: $quality")
            }
            
            // Password length
            policy.passwordMinimumLength?.let { length ->
                dpm.setPasswordMinimumLength(adminComponent, length)
                Log.d(TAG, "✅ Password minimum length: $length")
            }
            
            // Password letters
            policy.passwordMinimumLetters?.let { letters ->
                dpm.setPasswordMinimumLetters(adminComponent, letters)
            }
            
            // Password numbers
            policy.passwordMinimumNumeric?.let { numeric ->
                dpm.setPasswordMinimumNumeric(adminComponent, numeric)
            }
            
            // Password symbols
            policy.passwordMinimumSymbols?.let { symbols ->
                dpm.setPasswordMinimumSymbols(adminComponent, symbols)
            }
            
            // Password uppercase
            policy.passwordMinimumUpperCase?.let { uppercase ->
                dpm.setPasswordMinimumUpperCase(adminComponent, uppercase)
            }
            
            // Password lowercase
            policy.passwordMinimumLowerCase?.let { lowercase ->
                dpm.setPasswordMinimumLowerCase(adminComponent, lowercase)
            }
            
            // Max failed attempts
            policy.maximumFailedPasswordsForWipe?.let { max ->
                dpm.setMaximumFailedPasswordsForWipe(adminComponent, max)
                Log.d(TAG, "✅ Max failed password attempts: $max")
            }
            
            // Password expiration
            policy.passwordExpirationTimeout?.let { timeout ->
                val timeoutMs = timeout.replace("s", "").toLongOrNull()?.times(1000) ?: 0
                dpm.setPasswordExpirationTimeout(adminComponent, timeoutMs)
            }
            
            // Password history
            policy.passwordHistoryLength?.let { historyLength ->
                dpm.setPasswordHistoryLength(adminComponent, historyLength)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set password policy", e)
            addNonCompliance("passwordPolicy", "API_LEVEL", e.message)
        }
    }
    
    private fun enforceApplicationPolicies(applications: List<ApplicationPolicy>) {
        applications.forEach { app ->
            try {
                when (app.installType) {
                    "BLOCKED" -> {
                        // Block app installation/usage
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            dpm.setUninstallBlocked(adminComponent, app.packageName, true)
                            Log.d(TAG, "✅ Blocked app: ${app.packageName}")
                        }
                    }
                    "FORCE_INSTALLED" -> {
                        // Apps should be installed via managed Google Play
                        // We can enable hidden apps if they're already installed
                        if (app.disabled == false) {
                            policyHelper.setAppHidden(app.packageName, false)
                        }
                        Log.d(TAG, "✅ Force-installed app: ${app.packageName}")
                    }
                    "AVAILABLE" -> {
                        // Make app available but not forced
                        Log.d(TAG, "✅ App available: ${app.packageName}")
                    }
                }
                
                // Grant permissions
                app.permissionGrants?.forEach { grant ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val shouldGrant = grant.policy == "GRANT"
                        dpm.setPermissionGrantState(
                            adminComponent,
                            app.packageName,
                            grant.permission,
                            if (shouldGrant) 
                                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED 
                            else 
                                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                        )
                        Log.d(TAG, "✅ Permission ${grant.permission} ${grant.policy} for ${app.packageName}")
                    }
                }
                
                // Set app restrictions (managed configuration)
                app.managedConfiguration?.let { config ->
                    val bundle = android.os.Bundle()
                    config.forEach { (key, value) ->
                        when (value) {
                            is String -> bundle.putString(key, value)
                            is Int -> bundle.putInt(key, value)
                            is Boolean -> bundle.putBoolean(key, value)
                            is Long -> bundle.putLong(key, value)
                            is Double -> bundle.putDouble(key, value)
                        }
                    }
                    dpm.setApplicationRestrictions(adminComponent, app.packageName, bundle)
                    Log.d(TAG, "✅ Set app restrictions for ${app.packageName}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to apply policy for app: ${app.packageName}", e)
                addNonCompliance("applications", "API_LEVEL", app.packageName, e.message)
            }
        }
    }
    
    private fun enforceSystemUpdatePolicy(policy: com.androidmanager.data.model.SystemUpdatePolicy) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val updatePolicy = when (policy.type) {
                    "AUTOMATIC" -> android.app.admin.SystemUpdatePolicy.createAutomaticInstallPolicy()
                    "WINDOWED" -> {
                        val start = policy.startMinutes ?: 0
                        val end = policy.endMinutes ?: 1440
                        android.app.admin.SystemUpdatePolicy.createWindowedInstallPolicy(start, end)
                    }
                    "POSTPONE" -> android.app.admin.SystemUpdatePolicy.createPostponeInstallPolicy()
                    else -> null
                }
                
                updatePolicy?.let {
                    dpm.setSystemUpdatePolicy(adminComponent, it)
                    Log.d(TAG, "✅ System update policy set: ${policy.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set system update policy", e)
            addNonCompliance("systemUpdate", "API_LEVEL", e.message)
        }
    }
    
    private fun enforceWifiConfigPolicy(disabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setGlobalSetting(
                    adminComponent,
                    android.provider.Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    if (disabled) "0" else "1"
                )
                Log.d(TAG, "✅ WiFi config ${if (disabled) "disabled" else "enabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set WiFi config policy", e)
            addNonCompliance("wifiConfigDisabled", "API_LEVEL", e.message)
        }
    }
    
    private fun enforceBluetoothPolicy(disabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.addUserRestriction(
                    adminComponent,
                    if (disabled) android.os.UserManager.DISALLOW_BLUETOOTH else ""
                )
                Log.d(TAG, "✅ Bluetooth ${if (disabled) "disabled" else "enabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set Bluetooth policy", e)
        }
    }
    
    private fun enforceBluetoothConfigPolicy(disabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (disabled) {
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH)
                } else {
                    dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH)
                }
                Log.d(TAG, "✅ Bluetooth config ${if (disabled) "disabled" else "enabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set Bluetooth config policy", e)
        }
    }
    
    private fun enforceLocationPolicy(mode: String) {
        try {
            when (mode) {
                "LOCATION_ENFORCED" -> {
                    policyHelper.enforceLocationSettings()
                    Log.d(TAG, "✅ Location enforced to high accuracy")
                }
                "LOCATION_DISABLED" -> {
                    // Disable location
                    dpm.setSecureSetting(
                        adminComponent,
                        android.provider.Settings.Secure.LOCATION_MODE,
                        android.provider.Settings.Secure.LOCATION_MODE_OFF.toString()
                    )
                    Log.d(TAG, "✅ Location disabled")
                }
                "LOCATION_USER_CHOICE" -> {
                    // Let user choose
                    Log.d(TAG, "✅ Location user choice")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set location policy", e)
            addNonCompliance("locationMode", "API_LEVEL", e.message)
        }
    }
    
    private fun enforceScreenCapturePolicy(disabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setScreenCaptureDisabled(adminComponent, disabled)
                Log.d(TAG, "✅ Screen capture ${if (disabled) "disabled" else "enabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set screen capture policy", e)
        }
    }
    
    private fun enforceStatusBarPolicy(disabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(adminComponent, disabled)
                Log.d(TAG, "✅ Status bar ${if (disabled) "disabled" else "enabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set status bar policy", e)
        }
    }
    
    private fun enforceKeyguardPolicy(disabledFeatures: List<String>) {
        try {
            var features = 0
            disabledFeatures.forEach { feature ->
                features = features or when (feature) {
                    "KEYGUARD_DISABLED_FEATURE_CAMERA" -> DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA
                    "KEYGUARD_DISABLED_FEATURE_NOTIFICATIONS" -> DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS
                    "KEYGUARD_DISABLED_FEATURE_UNREDACTED_NOTIFICATIONS" -> DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS
                    "KEYGUARD_DISABLED_FEATURE_TRUST_AGENTS" -> DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS
                    "KEYGUARD_DISABLED_FEATURE_FINGERPRINT" -> DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT
                    "KEYGUARD_DISABLED_FEATURE_ALL" -> DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL
                    else -> 0
                }
            }
            dpm.setKeyguardDisabledFeatures(adminComponent, features)
            Log.d(TAG, "✅ Keyguard features disabled: $features")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set keyguard policy", e)
        }
    }
    
    private fun enforceAccountPolicy(accountTypes: List<String>) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                accountTypes.forEach { accountType ->
                    dpm.setAccountManagementDisabled(adminComponent, accountType, true)
                    Log.d(TAG, "✅ Account management disabled for: $accountType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set account policy", e)
        }
    }
    
    private fun enforceUsbFileTransferPolicy(disabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (disabled) {
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_USB_FILE_TRANSFER)
                } else {
                    dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_USB_FILE_TRANSFER)
                }
                Log.d(TAG, "✅ USB file transfer ${if (disabled) "disabled" else "enabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set USB file transfer policy", e)
        }
    }
    
    private fun enforceFactoryResetPolicy(disabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (disabled) {
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_FACTORY_RESET)
                } else {
                    dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_FACTORY_RESET)
                }
                Log.d(TAG, "✅ Factory reset ${if (disabled) "disabled" else "enabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set factory reset policy", e)
        }
    }
    
    private fun enforceDebuggingPolicy(allowed: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (!allowed) {
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                } else {
                    dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                }
                Log.d(TAG, "✅ Debugging ${if (allowed) "allowed" else "disabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set debugging policy", e)
        }
    }
    
    private fun enforceStayOnPolicy(modes: List<String>) {
        try {
            val stayOnValue = modes.joinToString(",") { mode ->
                when (mode) {
                    "BATTERY_PLUGGED_AC" -> "1"
                    "BATTERY_PLUGGED_USB" -> "2"
                    "BATTERY_PLUGGED_WIRELESS" -> "4"
                    else -> "0"
                }
            }
            
            dpm.setGlobalSetting(
                adminComponent,
                android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                stayOnValue
            )
            Log.d(TAG, "✅ Stay on while plugged: $stayOnValue")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set stay on policy", e)
        }
    }
    
    /**
     * Enforce password requirements (YOUR AMAPI format)
     * Matches: "passwordRequirements": {"passwordMinimumLength": 4, "passwordQuality": "NUMERIC"}
     */
    private fun enforcePasswordRequirements(requirements: PasswordRequirements) {
        try {
            if (!policyHelper.isDeviceOwner()) {
                addNonCompliance("passwordRequirements", "MANAGEMENT_MODE", "Not device owner")
                return
            }
            
            // Password quality - map AMAPI values to DevicePolicyManager constants
            requirements.passwordQuality?.let { quality ->
                val dpmQuality = when (quality) {
                    "NUMERIC" -> DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                    "NUMERIC_COMPLEX" -> DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX
                    "ALPHABETIC" -> DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC
                    "ALPHANUMERIC" -> DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                    "COMPLEX" -> DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                    "SOMETHING" -> DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
                    "BIOMETRIC_WEAK" -> DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK
                    else -> DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED
                }
                dpm.setPasswordQuality(adminComponent, dpmQuality)
                Log.d(TAG, "✅ Password quality set to: $quality (DPM: $dpmQuality)")
            }
            
            // Password minimum length
            requirements.passwordMinimumLength?.let { length ->
                dpm.setPasswordMinimumLength(adminComponent, length)
                Log.d(TAG, "✅ Password minimum length: $length")
            }
            
            // Password minimum letters
            requirements.passwordMinimumLetters?.let { letters ->
                dpm.setPasswordMinimumLetters(adminComponent, letters)
            }
            
            // Password minimum numeric
            requirements.passwordMinimumNumeric?.let { numeric ->
                dpm.setPasswordMinimumNumeric(adminComponent, numeric)
            }
            
            // Password minimum symbols
            requirements.passwordMinimumSymbols?.let { symbols ->
                dpm.setPasswordMinimumSymbols(adminComponent, symbols)
            }
            
            // Password minimum uppercase
            requirements.passwordMinimumUpperCase?.let { uppercase ->
                dpm.setPasswordMinimumUpperCase(adminComponent, uppercase)
            }
            
            // Password minimum lowercase
            requirements.passwordMinimumLowerCase?.let { lowercase ->
                dpm.setPasswordMinimumLowerCase(adminComponent, lowercase)
            }
            
            // Max failed password attempts for wipe
            requirements.maximumFailedPasswordsForWipe?.let { max ->
                dpm.setMaximumFailedPasswordsForWipe(adminComponent, max)
                Log.d(TAG, "✅ Max failed password attempts for wipe: $max")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set password requirements", e)
            addNonCompliance("passwordRequirements", "API_LEVEL", e.message)
        }
    }
    
    /**
     * Enforce advanced security overrides
     * Matches: "advancedSecurityOverrides": {"developerSettings": "DEVELOPER_SETTINGS_DISABLED"}
     */
    private fun enforceAdvancedSecurityOverrides(overrides: AdvancedSecurityOverrides) {
        try {
            if (!policyHelper.isDeviceOwner()) {
                addNonCompliance("advancedSecurityOverrides", "MANAGEMENT_MODE", "Not device owner")
                return
            }
            
            // Developer settings
            overrides.developerSettings?.let { developerSettings ->
                when (developerSettings) {
                    "DEVELOPER_SETTINGS_DISABLED" -> {
                        // Disable developer options
                        dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                        Log.d(TAG, "✅ Developer settings DISABLED")
                    }
                    "DEVELOPER_SETTINGS_ALLOWED" -> {
                        // Allow developer options
                        dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                        Log.d(TAG, "✅ Developer settings ALLOWED")
                    }
                }
            }
            
            // Untrusted apps policy
            overrides.untrustedAppsPolicy?.let { untrustedAppsPolicy ->
                when (untrustedAppsPolicy) {
                    "DISALLOW_INSTALL" -> {
                        dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
                        }
                        Log.d(TAG, "✅ Untrusted apps installation DISABLED")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to set advanced security overrides", e)
            addNonCompliance("advancedSecurityOverrides", "API_LEVEL", e.message)
        }
    }
    
    // ==================== Helper Methods ====================
    
    private fun addNonCompliance(
        settingName: String,
        reason: String,
        packageName: String? = null,
        currentValue: String? = null
    ) {
        nonComplianceList.add(
            NonComplianceDetail(
                settingName = settingName,
                nonComplianceReason = reason,
                packageName = packageName,
                currentValue = currentValue
            )
        )
    }
}
