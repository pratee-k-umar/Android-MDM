package com.androidmanager.data.model

import kotlinx.serialization.Serializable

/**
 * FCM token registration request
 */
@Serializable
data class FcmTokenRequest(
    val fcmToken: String,
    val imei1: String,
    val latitude: Double? = null,    // Optional: Device latitude
    val longitude: Double? = null    // Optional: Device longitude
)

/**
 * FCM token registration response
 */
@Serializable
data class FcmTokenResponse(
    val success: Boolean,
    val message: String,
    val data: FcmTokenData?
)

@Serializable
data class FcmTokenData(
    val customerId: String,
    val customerName: String,
    val isLocked: Boolean,
    val location: LocationInfo? = null,  // Optional location info
    val updatedAt: String
)

@Serializable
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val lastUpdated: String
)

/**
 * Lock/Unlock response request
 */
@Serializable
data class LockResponseRequest(
    val imei1: String,
    val lockSuccess: Boolean,
    val action: String, // "LOCK_DEVICE" or "UNLOCK_DEVICE"
    val errorMessage: String? = null
)

/**
 * Lock response from backend
 */
@Serializable
data class LockResponseResponse(
    val success: Boolean,
    val message: String,
    val data: LockResponseData?
)

@Serializable
data class LockResponseData(
    val customerId: String,
    val customerName: String,
    val currentLockStatus: Boolean,
    val responseProcessed: Boolean
)

/**
 * Device status response
 */
@Serializable
data class DeviceStatusResponse(
    val success: Boolean,
    val message: String,
    val data: DeviceStatusData?
)

@Serializable
data class DeviceStatusData(
    val customerId: String,
    val customerName: String,
    val mobileNumber: String,
    val isLocked: Boolean,
    val hasPendingEmis: Boolean,
    val pendingEmiCount: Int,
    val registeredAt: String,
    val lastUpdated: String
)

/**
 * Device status update request
 */
@Serializable
data class DeviceStatusUpdate(
    val deviceId: String,
    val latitude: Double?,
    val longitude: Double?,
    val batteryLevel: Int?,
    val isLocked: Boolean,
    val timestamp: Long
)

/**
 * Command from backend
 */
@Serializable
data class DeviceCommand(
    val commandId: String,
    val commandType: CommandType,
    val payload: CommandPayload?,
    val timestamp: Long
)

@Serializable
enum class CommandType {
    LOCK,
    UNLOCK,
    WIPE,
    UPDATE_MESSAGE,
    GET_LOCATION,
    PING
}

@Serializable
data class CommandPayload(
    val message: String? = null,
    val pin: String? = null,
    val wipeData: Boolean = false
)

/**
 * Command acknowledgment
 */
@Serializable
data class CommandAck(
    val commandId: String,
    val deviceId: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long
)

/**
 * Location data
 */
@Serializable
data class LocationData(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)

/**
 * FCM message data
 */
data class FCMCommandMessage(
    val commandType: String,
    val commandId: String,
    val message: String?,
    val payload: String?
)

/**
 * Device info for display/logging
 */
data class DeviceInfo(
    val deviceId: String,
    val serialNumber: String?,
    val imei: String?,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val isDeviceOwner: Boolean,
    val isLocked: Boolean,
    val shopOwnerEmail: String?
)

/**
 * Setup configuration from QR code
 */
@Serializable
data class QRSetupConfig(
    val backendUrl: String,
    val shopId: String,
    val shopName: String,
    val apiKey: String?
)

/**
 * Lock screen state
 */
data class LockScreenState(
    val isLocked: Boolean,
    val message: String,
    val shopName: String?,
    val contactNumber: String?,
    val lockTimestamp: Long
)
/**
 * Location update request to backend
 */
@Serializable
data class LocationUpdateRequest(
    val imei1: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Location update response
 */
@Serializable
data class LocationUpdateResponse(
    val success: Boolean,
    val message: String,
    val data: LocationUpdateData?
)

@Serializable
data class LocationUpdateData(
    val customerId: String,
    val customerName: String,
    val location: LocationInfo
)
