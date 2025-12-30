package com.androidmanager.data.api

import com.androidmanager.data.model.AmapiPolicy
import com.androidmanager.data.model.DeviceState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * AMAPI API Client
 * Communicates with Android Management API to fetch policies and report compliance
 */
class AmapiClient {
    
    companion object {
        private const val TAG = "AmapiClient"
        private const val BASE_URL = "https://androidmanagement.googleapis.com/v1"
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    /**
     * Fetch device details including current policy from AMAPI
     * 
     * @param deviceName Full device resource name (e.g., "enterprises/LC00abc/devices/123")
     * @param accessToken OAuth 2.0 access token or enrollment token
     * @return AmapiPolicy object or null if error
     */
    suspend fun fetchDevicePolicy(deviceName: String, accessToken: String): AmapiPolicy? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$deviceName"
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                
                android.util.Log.d(TAG, "Fetching device policy from: $url")
                
                val response = client.newCall(request).execute()
                
                return@withContext if (response.isSuccessful) {
                    val body = response.body?.string()
                    android.util.Log.d(TAG, "✅ Policy fetched successfully")
                    
                    // Parse the device response which contains appliedPolicyName
                    val deviceData = gson.fromJson(body, Map::class.java)
                    val policyName = deviceData["appliedPolicyName"] as? String
                    
                    if (policyName != null) {
                        // Fetch the actual policy
                        fetchPolicy(policyName, accessToken)
                    } else {
                        android.util.Log.w(TAG, "No applied policy found for device")
                        null
                    }
                } else {
                    android.util.Log.e(TAG, "❌ Failed to fetch policy: ${response.code} - ${response.message}")
                    null
                }
            } catch (e: IOException) {
                android.util.Log.e(TAG, "❌ Network error fetching policy", e)
                null
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error fetching policy", e)
                null
            }
        }
    }
    
    /**
     * Fetch a specific policy by name
     * 
     * @param policyName Full policy resource name (e.g., "enterprises/LC00abc/policies/policy1")
     * @param accessToken OAuth 2.0 access token
     * @return AmapiPolicy object or null if error
     */
    suspend fun fetchPolicy(policyName: String, accessToken: String): AmapiPolicy? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$policyName"
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                
                android.util.Log.d(TAG, "Fetching policy: $url")
                
                val response = client.newCall(request).execute()
                
                return@withContext if (response.isSuccessful) {
                    val body = response.body?.string()
                    android.util.Log.d(TAG, "✅ Policy fetched: ${body?.take(200)}")
                    gson.fromJson(body, AmapiPolicy::class.java)
                } else {
                    android.util.Log.e(TAG, "❌ Failed to fetch policy: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error fetching policy", e)
                null
            }
        }
    }
    
    /**
     * Report device compliance status to AMAPI
     * 
     * @param deviceName Full device resource name
     * @param accessToken OAuth 2.0 access token
     * @param deviceState Current device state and compliance info
     * @return true if successful, false otherwise
     */
    suspend fun reportCompliance(
        deviceName: String,
        accessToken: String,
        deviceState: DeviceState
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$deviceName"
                
                val json = gson.toJson(deviceState)
                val requestBody = json.toRequestBody(jsonMediaType)
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .patch(requestBody)
                    .build()
                
                android.util.Log.d(TAG, "Reporting compliance to: $url")
                android.util.Log.d(TAG, "Compliance data: $json")
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    android.util.Log.d(TAG, "✅ Compliance reported successfully")
                    true
                } else {
                    android.util.Log.e(TAG, "❌ Failed to report compliance: ${response.code} - ${response.message}")
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error reporting compliance", e)
                false
            }
        }
    }
    
    /**
     * Exchange enrollment token for access token
     * This should be called once after provisioning to get a long-lived token
     * 
     * Note: In production, you should implement proper OAuth 2.0 flow with refresh tokens
     */
    suspend fun exchangeEnrollmentToken(enrollmentToken: String): String? {
        // TODO: Implement OAuth exchange
        // For now, enrollment tokens can be used directly for API calls
        // But in production, you should exchange for proper OAuth tokens
        android.util.Log.d(TAG, "Using enrollment token as access token (dev mode)")
        return enrollmentToken
    }
}
