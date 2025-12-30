package com.androidmanager.data.model

import kotlinx.serialization.Serializable

/**
 * Device registration request with AMAPI support
 */
@Serializable
data class DeviceRegistrationRequest(
    val deviceId: String,
    val serialNumber: String?,
    val imei: String?,
    val fcmToken: String?,
    val shopId: String?,
    val shopOwnerEmail: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    // AMAPI fields
    val customerId: String? = null,
    val enrollmentToken: String? = null,
    val amapiEnrolled: Boolean = false
)

/**
 * Device registration response
 */
@Serializable
data class DeviceRegistrationResponse(
    val success: Boolean,
    val message: String,
    val data: RegistrationData?
)

@Serializable
data class RegistrationData(
    val deviceId: String,
    val customerId: String?,
    val registered: Boolean
)
