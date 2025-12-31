package com.androidmanager.data.model

import com.google.gson.annotations.SerializedName

/**
 * AMAPI Policy data models
 * Based on Android Management API v1 schema
 */

/**
 * Main policy object from AMAPI
 */
data class AmapiPolicy(
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("version")
    val version: String? = null,
    
    // Camera
    @SerializedName("cameraDisabled")
    val cameraDisabled: Boolean? = null,
    
    // Password policies (newer format)
    @SerializedName("passwordPolicies")
    val passwordPolicies: List<PasswordPolicy>? = null,
    
    // Password requirements (older/alternative format - YOUR AMAPI uses this!)
    @SerializedName("passwordRequirements")
    val passwordRequirements: PasswordRequirements? = null,
    
    // Applications
    @SerializedName("applications")
    val applications: List<ApplicationPolicy>? = null,
    
    // System updates
    @SerializedName("systemUpdate")
    val systemUpdate: SystemUpdatePolicy? = null,
    
    // Network configuration
    @SerializedName("wifiConfigsLockdownEnabled")
    val wifiConfigsLockdownEnabled: Boolean? = null,
    
    @SerializedName("wifiConfigDisabled")
    val wifiConfigDisabled: Boolean? = null,
    
    // Factory reset protection
    @SerializedName("factoryResetDisabled")
    val factoryResetDisabled: Boolean? = null,
    
    // FRP Admin Emails (for Factory Reset Protection) - Legacy format
    @SerializedName("frpAdminEmails")
    val frpAdminEmails: List<String>? = null,
    
    // FRP Admin User IDs (Recommended - from Google People API)
    @SerializedName("frpAdminUserIds")
    val frpAdminUserIds: List<String>? = null,
    
    // Location
    @SerializedName("locationMode")
    val locationMode: String? = null, // LOCATION_USER_CHOICE, LOCATION_DISABLED, LOCATION_ENFORCED
    
    // Debugging
    @SerializedName("debuggingFeaturesAllowed")
    val debuggingFeaturesAllowed: Boolean? = null,
    
    // Screen capture
    @SerializedName("screenCaptureDisabled")
    val screenCaptureDisabled: Boolean? = null,
    
    // Status bar
    @SerializedName("statusBarDisabled")
    val statusBarDisabled: Boolean? = null,
    
    // Keyguard features
    @SerializedName("keyguardDisabledFeatures")
    val keyguardDisabledFeatures: List<String>? = null,
    
    // Account management
    @SerializedName("accountTypesWithManagementDisabled")
    val accountTypesWithManagementDisabled: List<String>? = null,
    
    // Stay on while plugged in
    @SerializedName("stayOnPluggedModes")
    val stayOnPluggedModes: List<String>? = null,
    
    // Bluetooth
    @SerializedName("bluetoothDisabled")
    val bluetoothDisabled: Boolean? = null,
    
    @SerializedName("bluetoothConfigDisabled")
    val bluetoothConfigDisabled: Boolean? = null,
    
    // USB
    @SerializedName("usbFileTransferDisabled")
    val usbFileTransferDisabled: Boolean? = null,
    
    // Network escape hatch
    @SerializedName("networkEscapeHatchEnabled")
    val networkEscapeHatchEnabled: Boolean? = null,
    
    // Status reporting
    @SerializedName("statusReportingSettings")
    val statusReportingSettings: StatusReportingSettings? = null,
    
    // Advanced security overrides (developer settings, etc.)
    @SerializedName("advancedSecurityOverrides")
    val advancedSecurityOverrides: AdvancedSecurityOverrides? = null
)

/**
 * Password policy requirements (newer format)
 */
data class PasswordPolicy(
    @SerializedName("passwordMinimumLength")
    val passwordMinimumLength: Int? = null,
    
    @SerializedName("passwordQuality")
    val passwordQuality: String? = null, // COMPLEXITY_LOW, COMPLEXITY_MEDIUM, COMPLEXITY_HIGH
    
    @SerializedName("passwordMinimumLetters")
    val passwordMinimumLetters: Int? = null,
    
    @SerializedName("passwordMinimumNumeric")
    val passwordMinimumNumeric: Int? = null,
    
    @SerializedName("passwordMinimumSymbols")
    val passwordMinimumSymbols: Int? = null,
    
    @SerializedName("passwordMinimumUpperCase")
    val passwordMinimumUpperCase: Int? = null,
    
    @SerializedName("passwordMinimumLowerCase")
    val passwordMinimumLowerCase: Int? = null,
    
    @SerializedName("maximumFailedPasswordsForWipe")
    val maximumFailedPasswordsForWipe: Int? = null,
    
    @SerializedName("passwordExpirationTimeout")
    val passwordExpirationTimeout: String? = null, // Duration in seconds
    
    @SerializedName("passwordHistoryLength")
    val passwordHistoryLength: Int? = null
)

/**
 * Password requirements (alternative format used in YOUR AMAPI policy)
 */
data class PasswordRequirements(
    @SerializedName("passwordMinimumLength")
    val passwordMinimumLength: Int? = null,
    
    @SerializedName("passwordQuality")
    val passwordQuality: String? = null, // NUMERIC, ALPHABETIC, ALPHANUMERIC, COMPLEX, BIOMETRIC_WEAK
    
    @SerializedName("passwordMinimumLetters")
    val passwordMinimumLetters: Int? = null,
    
    @SerializedName("passwordMinimumNumeric")
    val passwordMinimumNumeric: Int? = null,
    
    @SerializedName("passwordMinimumSymbols")
    val passwordMinimumSymbols: Int? = null,
    
    @SerializedName("passwordMinimumUpperCase")
    val passwordMinimumUpperCase: Int? = null,
    
    @SerializedName("passwordMinimumLowerCase")
    val passwordMinimumLowerCase: Int? = null,
    
    @SerializedName("maximumFailedPasswordsForWipe")
    val maximumFailedPasswordsForWipe: Int? = null
)

/**
 * Advanced security overrides
 */
data class AdvancedSecurityOverrides(
    @SerializedName("developerSettings")
    val developerSettings: String? = null, // DEVELOPER_SETTINGS_DISABLED, DEVELOPER_SETTINGS_ALLOWED
    
    @SerializedName("untrustedAppsPolicy")
    val untrustedAppsPolicy: String? = null, // DISALLOW_INSTALL, ALLOW_INSTALL_IN_PERSONAL_PROFILE_ONLY
    
    @SerializedName("googlePlayProtectVerifyApps")
    val googlePlayProtectVerifyApps: String? = null // VERIFY_APPS_ENFORCED, etc.
)

/**
 * Application installation and management policy
 */
data class ApplicationPolicy(
    @SerializedName("packageName")
    val packageName: String,
    
    @SerializedName("installType")
    val installType: String? = null, // FORCE_INSTALLED, BLOCKED, AVAILABLE, REQUIRED_FOR_SETUP
    
    @SerializedName("defaultPermissionPolicy")
    val defaultPermissionPolicy: String? = null, // GRANT, PROMPT, DENY
    
    @SerializedName("managedConfiguration")
    val managedConfiguration: Map<String, Any>? = null,
    
    @SerializedName("delegatedScopes")
    val delegatedScopes: List<String>? = null,
    
    @SerializedName("disabled")
    val disabled: Boolean? = null,
    
    @SerializedName("lockTaskAllowed")
    val lockTaskAllowed: Boolean? = null,
    
    @SerializedName("permissionGrants")
    val permissionGrants: List<PermissionGrant>? = null
)

/**
 * Permission grant for an app
 */
data class PermissionGrant(
    @SerializedName("permission")
    val permission: String,
    
    @SerializedName("policy")
    val policy: String // GRANT, DENY, PROMPT
)

/**
 * System update policy
 */
data class SystemUpdatePolicy(
    @SerializedName("type")
    val type: String? = null, // AUTOMATIC, WINDOWED, POSTPONE
    
    @SerializedName("startMinutes")
    val startMinutes: Int? = null,
    
    @SerializedName("endMinutes")
    val endMinutes: Int? = null,
    
    @SerializedName("freezePeriods")
    val freezePeriods: List<FreezePeriod>? = null
)

/**
 * System update freeze period
 */
data class FreezePeriod(
    @SerializedName("startDate")
    val startDate: String,
    
    @SerializedName("endDate")
    val endDate: String
)

/**
 * Status reporting settings
 */
data class StatusReportingSettings(
    @SerializedName("applicationReportsEnabled")
    val applicationReportsEnabled: Boolean? = null,
    
    @SerializedName("deviceSettingsEnabled")
    val deviceSettingsEnabled: Boolean? = null,
    
    @SerializedName("softwareInfoEnabled")
    val softwareInfoEnabled: Boolean? = null,
    
    @SerializedName("memoryInfoEnabled")
    val memoryInfoEnabled: Boolean? = null,
    
    @SerializedName("networkInfoEnabled")
    val networkInfoEnabled: Boolean? = null,
    
    @SerializedName("displayInfoEnabled")
    val displayInfoEnabled: Boolean? = null,
    
    @SerializedName("powerManagementEventsEnabled")
    val powerManagementEventsEnabled: Boolean? = null,
    
    @SerializedName("hardwareStatusEnabled")
    val hardwareStatusEnabled: Boolean? = null
)

/**
 * Device state for compliance reporting
 */
data class DeviceState(
    @SerializedName("appliedPolicyVersion")
    val appliedPolicyVersion: String? = null,
    
    @SerializedName("policyCompliant")
    val policyCompliant: Boolean? = null,
    
    @SerializedName("nonComplianceDetails")
    val nonComplianceDetails: List<NonComplianceDetail>? = null
)

/**
 * Non-compliance detail
 */
data class NonComplianceDetail(
    @SerializedName("settingName")
    val settingName: String,
    
    @SerializedName("nonComplianceReason")
    val nonComplianceReason: String, // API_LEVEL, MANAGEMENT_MODE, USER_ACTION, etc.
    
    @SerializedName("packageName")
    val packageName: String? = null,
    
    @SerializedName("currentValue")
    val currentValue: String? = null
)
