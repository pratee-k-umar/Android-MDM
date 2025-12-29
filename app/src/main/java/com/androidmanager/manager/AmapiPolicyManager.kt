package com.androidmanager.manager

import android.content.Context
import android.util.Log
import com.androidmanager.data.api.AmapiClient
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.model.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AMAPI Policy Manager
 * Main orchestrator for:
 * 1. Fetching policies from AMAPI
 * 2. Enforcing policies on device
 * 3. Reporting compliance back to AMAPI
 */
class AmapiPolicyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AmapiPolicyManager"
        private const val SYNC_INTERVAL_MS = 1000 * 60 * 60 // 1 hour
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesManager = PreferencesManager(context)
    private val apiClient = AmapiClient()
    private val policyEnforcer = AmapiPolicyEnforcer(context)
    
    private var currentPolicyVersion: String? = null
    private var lastSyncTimestamp: Long = 0
    
    /**
     * Initialize policy manager and fetch policies if AMAPI provisioned
     */
    fun initialize() {
        scope.launch {
            try {
                val isAmapiProvisioned = preferencesManager.isAmapiProvisionedSync()
                
                if (isAmapiProvisioned) {
                    Log.d(TAG, "📱 Device is AMAPI provisioned - initializing policy sync")
                    syncPolicies()
                } else {
                    Log.d(TAG, "⚠️ Device not AMAPI provisioned - skipping policy sync")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing policy manager", e)
            }
        }
    }
    
    /**
     * Fetch and apply policies from AMAPI
     * This should be called:
     * - On app start
     * - Periodically (e.g., every hour)
     * - When triggered by Google Play Services policy update
     */
    suspend fun syncPolicies() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Syncing policies from AMAPI...")
                
                // Get AMAPI configuration
                val enterpriseId = preferencesManager.getEnterpriseId()
                val deviceName = preferencesManager.getAmapiDeviceName()
                val enrollmentToken = preferencesManager.getEnrollmentToken()
                
                if (enterpriseId == null || enrollmentToken == null) {
                    Log.w(TAG, "⚠️ Missing AMAPI credentials - cannot sync policies")
                    return@withContext
                }
                
                // Construct device resource name
                val deviceResourceName = deviceName ?: "$enterpriseId/devices/unknown"
                
                Log.d(TAG, "📡 Fetching policy for device: $deviceResourceName")
                
                // Fetch policy from AMAPI
                val policy = apiClient.fetchDevicePolicy(deviceResourceName, enrollmentToken)
                
                if (policy != null) {
                    Log.d(TAG, "✅ Policy fetched successfully: ${policy.name}")
                    currentPolicyVersion = policy.version
                    
                    // Apply policy
                    val nonCompliance = policyEnforcer.applyPolicy(policy)
                    
                    // Report compliance
                    reportCompliance(deviceResourceName, enrollmentToken, nonCompliance)
                    
                    lastSyncTimestamp = System.currentTimeMillis()
                    
                    Log.d(TAG, "✅ Policy sync complete")
                } else {
                    Log.w(TAG, "⚠️ No policy retrieved from AMAPI")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing policies", e)
            }
        }
    }
    
    /**
     * Report device compliance status to AMAPI
     */
    private suspend fun reportCompliance(
        deviceName: String,
        accessToken: String,
        nonCompliance: List<com.androidmanager.data.model.NonComplianceDetail>
    ) {
        try {
            val deviceState = DeviceState(
                appliedPolicyVersion = currentPolicyVersion,
                policyCompliant = nonCompliance.isEmpty(),
                nonComplianceDetails = if (nonCompliance.isEmpty()) null else nonCompliance
            )
            
            val success = apiClient.reportCompliance(deviceName, accessToken, deviceState)
            
            if (success) {
                Log.d(TAG, "✅ Compliance reported - Status: ${if (deviceState.policyCompliant == true) "COMPLIANT" else "NON-COMPLIANT"}")
                if (nonCompliance.isNotEmpty()) {
                    Log.w(TAG, "⚠️ Non-compliance issues: ${nonCompliance.size}")
                    nonCompliance.forEach { issue ->
                        Log.w(TAG, "  - ${issue.settingName}: ${issue.nonComplianceReason}")
                    }
                }
            } else {
                Log.e(TAG, "❌ Failed to report compliance")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reporting compliance", e)
        }
    }
    
    /**
     * Force immediate policy sync
     * Useful when triggered by push notification or admin action
     */
    fun forcePolicySync() {
        scope.launch {
            syncPolicies()
        }
    }
    
    /**
     * Check if policy sync is needed based on time interval
     */
    suspend fun syncPoliciesIfNeeded() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSyncTimestamp > SYNC_INTERVAL_MS) {
            syncPolicies()
        } else {
            Log.d(TAG, "⏭️ Skipping policy sync - synced recently")
        }
    }
    
    /**
     * Handle managed configuration changes
     * Called when app receives new restrictions from Google Play Services
     */
    suspend fun onManagedConfigurationChanged() {
        Log.d(TAG, "📱 Managed configuration changed - syncing policies")
        syncPolicies()
    }
    
    /**
     * Get last sync timestamp
     */
    fun getLastSyncTime(): Long = lastSyncTimestamp
    
    /**
     * Get current policy version
     */
    fun getCurrentPolicyVersion(): String? = currentPolicyVersion
}
