package com.androidmanager.data.repository

import android.os.Build
import android.util.Log
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.model.*
import com.androidmanager.data.remote.ApiService
import com.androidmanager.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for device-related operations
 */
class DeviceRepository(
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "DeviceRepository"
    }

    private val apiService: ApiService
        get() = NetworkModule.getApiService()

    /**
     * Register FCM token with backend (using IMEI)
     * Optionally includes location for complete registration
     */
    suspend fun registerFcmToken(
        fcmToken: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<FcmTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val imei = preferencesManager.getImei() ?: return@withContext Result.failure(
                Exception("IMEI not found")
            )

            val request = FcmTokenRequest(
                fcmToken = fcmToken,
                imei1 = imei,
                latitude = latitude,
                longitude = longitude
            )

            Log.d(TAG, "Registering FCM token with backend (Location: ${latitude != null})")

            val response = apiService.registerFcmToken(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "FCM token registered: ${body.message}")
                
                // Save FCM token locally
                preferencesManager.setFcmToken(fcmToken)
                
                Result.success(body)
            } else {
                Log.e(TAG, "FCM token registration failed: ${response.code()}")
                Result.failure(Exception("FCM token registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Send lock/unlock response to backend
     */
    suspend fun sendLockResponse(
        action: String,
        success: Boolean,
        errorMessage: String?
    ): Result<LockResponseResponse> = withContext(Dispatchers.IO) {
        try {
            val imei = preferencesManager.getImei() ?: return@withContext Result.failure(
                Exception("IMEI not found")
            )

            val request = LockResponseRequest(
                imei1 = imei,
                lockSuccess = success,
                action = action,
                errorMessage = errorMessage
            )

            val response = apiService.sendLockResponse(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "Lock response sent: ${body.message}")
                Result.success(body)
            } else {
                Log.e(TAG, "Lock response failed: ${response.code()}")
                Result.failure(Exception("Lock response failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending lock response", e)
            Result.failure(e)
        }
    }

    /**
     * Get device status from backend
     */
    suspend fun getDeviceStatus(): Result<DeviceStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val imei = preferencesManager.getImei() ?: return@withContext Result.failure(
                Exception("IMEI not found")
            )

            val response = apiService.getDeviceStatus(imei)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "Device status fetched: ${body.message}")
                Result.success(body)
            } else {
                Log.e(TAG, "Get status failed: ${response.code()}")
                Result.failure(Exception("Get status failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device status", e)
            Result.failure(e)
        }
    }

    /**
     * Register device with backend
     * Now includes location for complete registration
     * Supports AMAPI enrollment data
     */
    suspend fun registerDevice(
        deviceId: String,
        serialNumber: String?,
        imei: String?,
        fcmToken: String?,
        shopId: String?,
        shopOwnerEmail: String?,
        latitude: Double? = null,
        longitude: Double? = null,
        customerId: String? = null,           // AMAPI customer ID
        enrollmentToken: String? = null        // AMAPI enrollment token
    ): Result<FcmTokenResponse> = withContext(Dispatchers.IO) {
        try {
            // Save IMEI if provided
            imei?.let { preferencesManager.setImei(it) }
            
            // Log AMAPI enrollment status
            if (customerId != null && enrollmentToken != null) {
                Log.d(TAG, "🔐 AMAPI enrollment data present")
                Log.d(TAG, "  Customer ID: $customerId")
                Log.d(TAG, "  Enrollment Token: ${enrollmentToken.take(20)}...")
            }
            
            // Register FCM token if available, including location
            if (fcmToken != null) {
                return@withContext registerFcmToken(
                    fcmToken = fcmToken,
                    latitude = latitude,
                    longitude = longitude
                )
            } else {
                Log.w(TAG, "No FCM token provided during registration - will register later when FCM initializes")
                Result.success(FcmTokenResponse(
                    success = false,
                    message = "No FCM token yet - will register when available",
                    data = null
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering device", e)
            Result.failure(e)
        }
    }

    /**
     * Update device status (STUB - Backend doesn't have this endpoint)
     */
    suspend fun updateDeviceStatus(
        latitude: Double?,
        longitude: Double?,
        batteryLevel: Int?,
        isLocked: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.w(TAG, "updateDeviceStatus: Backend endpoint not implemented")
        Result.success(Unit)
    }

    /**
     * Send location update to backend (Customer Device API)
     */
    suspend fun sendLocationUpdate(
        latitude: Double,
        longitude: Double
    ): Result<LocationUpdateResponse> = withContext(Dispatchers.IO) {
        try {
            val imei = preferencesManager.getImei() ?: return@withContext Result.failure(
                Exception("IMEI not found")
            )

            val request = LocationUpdateRequest(
                imei1 = imei,
                latitude = latitude,
                longitude = longitude
            )

            val response = apiService.updateCustomerLocation(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "Location updated: ${body.message}")
                Result.success(body)
            } else {
                Log.e(TAG, "Location update failed: ${response.code()}")
                Result.failure(Exception("Location update failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending location update", e)
            Result.failure(e)
        }
    }

    @Suppress("UNREACHABLE_CODE")
    private suspend fun sendLocationUpdateOld(
        latitude: Double,
        longitude: Double,
        accuracy: Float
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceId = preferencesManager.getDeviceIdSync() ?: return@withContext Result.failure(
                Exception("Device ID not found")
            )
            val authToken = preferencesManager.getAuthToken() ?: return@withContext Result.failure(
                Exception("Auth token not found")
            )

            val location = LocationData(
                deviceId = deviceId,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                timestamp = System.currentTimeMillis()
            )

            val response = apiService.sendLocationUpdate(deviceId, location, "Bearer $authToken")

            if (response.isSuccessful) {
                Log.d(TAG, "Location sent successfully")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Location update failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending location", e)
            Result.failure(e)
        }
    }

    /**
     * Acknowledge command execution (STUB - Backend doesn't have this endpoint)
     */
    suspend fun acknowledgeCommand(
        commandId: String,
        success: Boolean,
        errorMessage: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.w(TAG, "acknowledgeCommand: Backend endpoint not implemented")
        return@withContext Result.success(Unit)
    }

    @Suppress("UNREACHABLE_CODE")
    private suspend fun acknowledgeCommandOld(
        commandId: String,
        success: Boolean,
        errorMessage: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceId = preferencesManager.getDeviceIdSync() ?: return@withContext Result.failure(
                Exception("Device ID not found")
            )
            val authToken = preferencesManager.getAuthToken() ?: return@withContext Result.failure(
                Exception("Auth token not found")
            )

            val ack = CommandAck(
                commandId = commandId,
                deviceId = deviceId,
                success = success,
                errorMessage = errorMessage,
                timestamp = System.currentTimeMillis()
            )

            val response = apiService.acknowledgeCommand(commandId, ack, "Bearer $authToken")

            if (response.isSuccessful) {
                Log.d(TAG, "Command acknowledged: $commandId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Acknowledgment failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acknowledging command", e)
            Result.failure(e)
        }
    }

    /**
     * Get pending commands from backend (STUB - Backend doesn't have this endpoint)
     */
    suspend fun getPendingCommands(): Result<List<DeviceCommand>> = withContext(Dispatchers.IO) {
        Log.w(TAG, "getPendingCommands: Backend endpoint not implemented")
        return@withContext Result.success(emptyList())
    }

    @Suppress("UNREACHABLE_CODE")
    private suspend fun getPendingCommandsOld(): Result<List<DeviceCommand>> = withContext(Dispatchers.IO) {
        try {
            val deviceId = preferencesManager.getDeviceIdSync() ?: return@withContext Result.failure(
                Exception("Device ID not found")
            )
            val authToken = preferencesManager.getAuthToken() ?: return@withContext Result.failure(
                Exception("Auth token not found")
            )

            val response = apiService.getPendingCommands(deviceId, "Bearer $authToken")

            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Got ${response.body()!!.size} pending commands")
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get commands"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting commands", e)
            Result.failure(e)
        }
    }

    /**
     * Update FCM token on backend
     */
    suspend fun updateFcmToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = registerFcmToken(token)
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to update FCM token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Send heartbeat/activity to backend
     * @deprecated No longer used - location updates provide implicit device activity tracking
     */
    @Deprecated("No longer used - location updates provide implicit device activity tracking")
    suspend fun sendHeartbeat(): Result<Unit> = withContext(Dispatchers.IO) {
        // Heartbeat removed - location updates provide implicit device activity tracking
        Log.d(TAG, "Heartbeat is deprecated - location updates now track device activity")
        return@withContext Result.success(Unit)
    }
    
    @Deprecated("Old heartbeat implementation - not used")
    private suspend fun sendHeartbeatOld(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceId = preferencesManager.getDeviceIdSync() ?: return@withContext Result.failure(
                Exception("Device ID not found")
            )
            val authToken = preferencesManager.getAuthToken() ?: return@withContext Result.failure(
                Exception("Auth token not found")
            )

            val response = apiService.sendHeartbeat(deviceId, "Bearer $authToken")

            if (response.isSuccessful) {
                Log.d(TAG, "Heartbeat sent")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Heartbeat failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending heartbeat", e)
            Result.failure(e)
        }
    }

    /**
     * Report lock status change
     */
    suspend fun reportLockStatus(isLocked: Boolean, message: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceId = preferencesManager.getDeviceIdSync() ?: return@withContext Result.failure(
                Exception("Device ID not found")
            )
            val authToken = preferencesManager.getAuthToken() ?: return@withContext Result.failure(
                Exception("Auth token not found")
            )

            val status = mutableMapOf<String, Any>(
                "isLocked" to isLocked,
                "timestamp" to System.currentTimeMillis()
            )
            message?.let { status["message"] = it }

            val response = apiService.reportLockStatus(deviceId, status, "Bearer $authToken")

            if (response.isSuccessful) {
                Log.d(TAG, "Lock status reported: isLocked=$isLocked")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Lock status report failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting lock status", e)
            Result.failure(e)
        }
    }

    /**
     * Get retailer shop information for the device
     */
    suspend fun getRetailerShop(): Result<RetailerShopResponse> = withContext(Dispatchers.IO) {
        try {
            val imei = preferencesManager.getImei() ?: return@withContext Result.failure(
                Exception("IMEI not found")
            )

            val response = apiService.getRetailerShop(imei)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "Retailer shop fetched: ${body.data?.shopName}")
                Result.success(body)
            } else {
                Log.e(TAG, "Get retailer shop failed: ${response.code()}")
                Result.failure(Exception("Get retailer shop failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting retailer shop", e)
            Result.failure(e)
        }
    }
}
