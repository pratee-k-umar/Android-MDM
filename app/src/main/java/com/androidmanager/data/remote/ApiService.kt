package com.androidmanager.data.remote

import com.androidmanager.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * API Service for backend communication
 */
interface ApiService {

    /**
     * Register/Update FCM token for customer device
     */
    @PUT("api/customer/device/fcm-token")
    suspend fun registerFcmToken(
        @Body request: FcmTokenRequest
    ): Response<FcmTokenResponse>

    /**
     * Update device status
     */
    @POST("api/v1/devices/{deviceId}/status")
    suspend fun updateDeviceStatus(
        @Path("deviceId") deviceId: String,
        @Body status: DeviceStatusUpdate,
        @Header("Authorization") authToken: String
    ): Response<Unit>

    /**
     * Send location update
     */
    @POST("api/v1/devices/{deviceId}/location")
    suspend fun sendLocationUpdate(
        @Path("deviceId") deviceId: String,
        @Body location: LocationData,
        @Header("Authorization") authToken: String
    ): Response<Unit>

    /**
     * Acknowledge command execution
     */
    @POST("api/v1/commands/{commandId}/ack")
    suspend fun acknowledgeCommand(
        @Path("commandId") commandId: String,
        @Body ack: CommandAck,
        @Header("Authorization") authToken: String
    ): Response<Unit>

    /**
     * Get pending commands for device
     */
    @GET("api/v1/devices/{deviceId}/commands/pending")
    suspend fun getPendingCommands(
        @Path("deviceId") deviceId: String,
        @Header("Authorization") authToken: String
    ): Response<List<DeviceCommand>>

    /**
     * Send lock/unlock response to backend
     */
    @POST("api/customer/device/lock-response")
    suspend fun sendLockResponse(
        @Body request: LockResponseRequest
    ): Response<LockResponseResponse>

    /**
     * Get customer device status
     */
    @GET("api/customer/device/status/{imei1}")
    suspend fun getDeviceStatus(
        @Path("imei1") imei: String
    ): Response<DeviceStatusResponse>

    /**
     * Heartbeat/Ping
     */
    @POST("api/v1/devices/{deviceId}/heartbeat")
    suspend fun sendHeartbeat(
        @Path("deviceId") deviceId: String,
        @Header("Authorization") authToken: String
    ): Response<Unit>

    /**
     * Report device lock status change
     */
    @POST("api/v1/devices/{deviceId}/lock-status")
    suspend fun reportLockStatus(
        @Path("deviceId") deviceId: String,
        @Body status: Map<String, Any>,
        @Header("Authorization") authToken: String
    ): Response<Unit>

    /**
     * Update customer device location (Customer Device API)
     */
    @POST("api/customer/device/location")
    suspend fun updateCustomerLocation(
        @Body request: LocationUpdateRequest
    ): Response<LocationUpdateResponse>
}
