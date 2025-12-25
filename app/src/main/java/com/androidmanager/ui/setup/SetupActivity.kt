package com.androidmanager.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.remote.NetworkModule
import com.androidmanager.data.repository.DeviceRepository
import com.androidmanager.manager.DevicePolicyManagerHelper
import com.androidmanager.service.DeviceMonitorService
import com.androidmanager.ui.theme.AndroidManagerTheme
import com.androidmanager.util.Constants
import com.androidmanager.util.DeviceUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Setup Activity - Automatic device provisioning
 * No user input required - everything is configured automatically
 */
class SetupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SetupActivity"
    }

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var policyHelper: DevicePolicyManagerHelper
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        policyHelper = DevicePolicyManagerHelper(this)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize network with hardcoded URL
        NetworkModule.initialize(Constants.BACKEND_URL)
        deviceRepository = DeviceRepository(preferencesManager)

        setContent {
            AndroidManagerTheme {
                AutoSetupScreen()
            }
        }
    }

    @Composable
    private fun AutoSetupScreen() {
        var setupState by remember { mutableStateOf(SetupState.INITIALIZING) }
        var statusMessage by remember { mutableStateOf("Initializing...") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var generatedPin by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            runAutoSetup(
                onStateChange = { state, message ->
                    setupState = state
                    statusMessage = message
                },
                onPinGenerated = { pin ->
                    generatedPin = pin
                },
                onError = { error ->
                    errorMessage = error
                    setupState = SetupState.ERROR
                },
                onComplete = {
                    setupState = SetupState.COMPLETE
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                when (setupState) {
                    SetupState.INITIALIZING,
                    SetupState.APPLYING_RESTRICTIONS,
                    SetupState.GENERATING_PIN,
                    SetupState.LOCKING_ACCOUNT,
                    SetupState.REGISTERING -> {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "Setting Up Device",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = statusMessage,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    SetupState.WAITING_FOR_ACCOUNT -> {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "Google Account Required",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Please add your shop's Google account for FRP protection.\n\n" +
                                  "1. Tap the button below\n" +
                                  "2. Sign in with Google\n" +
                                  "3. Setup will continue automatically\n\n" +
                                  "This account cannot be removed later.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
                                        putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to open account settings", e)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("Add Google Account")
                        }
                    }
                    

                    
                    SetupState.COMPLETE -> {
                        // Auto-finish after showing confirmation briefly
                        LaunchedEffect(Unit) {
                            delay(2000) // Show message for 2 seconds
                            finishSetup()
                        }
                        
                        Text(
                            text = "✓",
                            fontSize = 72.sp,
                            color = Color(0xFF4CAF50)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Device Ready",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    SetupState.ERROR -> {
                        Text(
                            text = "⚠",
                            fontSize = 72.sp,
                            color = Color(0xFFE94560)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Setup Failed",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = errorMessage ?: "Unknown error occurred",
                            color = Color(0xFFE94560),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = { 
                                errorMessage = null
                                lifecycleScope.launch {
                                    runAutoSetup(
                                        onStateChange = { state, message ->
                                            setupState = state
                                            statusMessage = message
                                        },
                                        onPinGenerated = { pin ->
                                            generatedPin = pin
                                        },
                                        onError = { error ->
                                            errorMessage = error
                                            setupState = SetupState.ERROR
                                        },
                                        onComplete = {
                                            setupState = SetupState.COMPLETE
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("Retry")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(
                            onClick = { finishSetup() }
                        ) {
                            Text("Skip and Continue", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    private suspend fun runAutoSetup(
        onStateChange: (SetupState, String) -> Unit,
        onPinGenerated: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        try {
            // Detect if device is enterprise-provisioned
            val isEnterpriseDevice = policyHelper.isDeviceOwner()
            
            Log.d(TAG, "Setup mode: ${if (isEnterpriseDevice) "ENTERPRISE" else "MANUAL"}")
            
            if (isEnterpriseDevice) {
                // Enterprise provisioning - simplified flow
                // Managed account, permissions, and restrictions handled by cloud policy
                runEnterpriseSetup(onStateChange, onError, onComplete)
            } else {
                // Manual setup - full flow (for testing/development)
                runManualSetup(onStateChange, onPinGenerated, onError, onComplete)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Setup failed", e)
            onError(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Enterprise provisioning setup
     * Device is already provisioned via Android Management API:
     * - App is force-installed
     * - Permissions auto-granted  
     * - Managed account created automatically
     * - Screen lock enforced by policy
     * - FRP protection via managed account
     */
    private suspend fun runEnterpriseSetup(
        onStateChange: (SetupState, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        Log.d(TAG, "Starting enterprise provisioning setup")
        
        // Step 1: Initialize device info
        onStateChange(SetupState.INITIALIZING, "Initializing enterprise device...")
        delay(500)
        
        val deviceId = DeviceUtils.generateDeviceId(this@SetupActivity)
        preferencesManager.setDeviceId(deviceId)
        
        preferencesManager.setShopInfo(Constants.SHOP_ID, Constants.SHOP_NAME)
        preferencesManager.setBackendUrl(Constants.BACKEND_URL)
        
        Log.d(TAG, "Device ID: $deviceId")
        
        // Step 2: Get device identifiers
        onStateChange(SetupState.REGISTERING, "Collecting device information...")
        delay(500)
        
        val serialNumber = policyHelper.getDeviceSerialNumber()
        var imei = policyHelper.getDeviceIMEI()
        
        // Handle IMEI for emulator vs real device
        if (imei == null || imei == "000000000000000") {
            val isEmulator = android.os.Build.FINGERPRINT.contains("generic") || 
                             android.os.Build.MODEL.contains("Emulator") ||
                             android.os.Build.PRODUCT.contains("sdk")
            
            if (isEmulator) {
                imei = "123456789012345"
                Log.w(TAG, "Running on emulator - using test IMEI: $imei")
            } else {
                Log.e(TAG, "Failed to get IMEI from real device")
                throw Exception("IMEI required for device registration.")
            }
        }
        
        preferencesManager.setImei(imei)
        Log.d(TAG, "IMEI: $imei, Serial: $serialNumber")
        
        // Step 3: Get location if available (permissions auto-granted by policy)
        var currentLocation: Location? = null
        try {
            if (ActivityCompat.checkSelfPermission(
                    this@SetupActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                currentLocation = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    object : CancellationToken() {
                        override fun onCanceledRequested(p0: OnTokenCanceledListener) = CancellationTokenSource().token
                        override fun isCancellationRequested() = false
                    }
                ).await()
                Log.d(TAG, "Location: ${currentLocation?.latitude}, ${currentLocation?.longitude}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get location: ${e.message}")
        }
        
        // Step 4: Get FCM token (may be null initially)
        val fcmToken = preferencesManager.getFcmToken()
        
        if (fcmToken != null) {
            Log.d(TAG, "✅ FCM Token available: ${fcmToken.take(20)}...")
        } else {
            Log.w(TAG, "⚠️ FCM Token not available yet")
            Log.w(TAG, "→ Device will register without FCM token")
            Log.w(TAG, "→ Token will be sent to backend automatically when generated")
            Log.w(TAG, "→ Management API provides fallback for device control")
        }
        
        // Step 5: Register with backend (NO PIN - managed account handles FRP)
        // FCM token can be null - it will be updated later via onNewToken()
        try {
            val result = deviceRepository.registerDevice(
                deviceId = deviceId,
                serialNumber = serialNumber,
                imei = imei,
                fcmToken = fcmToken,  // ← Can be null, will update later
                shopId = Constants.SHOP_ID,
                shopOwnerEmail = null,  // Managed account email handled by enterprise
                devicePin = null,  // NO PIN - managed account + policy handles FRP
                latitude = currentLocation?.latitude,
                longitude = currentLocation?.longitude
            )
            
            result.onSuccess {
                Log.d(TAG, "✅ Enterprise device registered successfully")
            }.onFailure { error ->
                Log.e(TAG, "❌ Registration failed: ${error.message}")
                throw error
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend registration failed", e)
            onError("Failed to register device: ${e.message}")
            return
        }
        
        // Step 6: Apply device-specific restrictions
        onStateChange(SetupState.APPLYING_RESTRICTIONS, "Finalizing device setup...")
        delay(500)
        
        // Suppress device owner notifications
        policyHelper.suppressDeviceOwnerNotifications()
        
        // Apply any additional app-specific restrictions
        // (Cloud policy handles most restrictions, this is for app-specific ones)
        policyHelper.applyDeviceRestrictions()
        
        // Step 7: Mark setup complete and start background service
        preferencesManager.setSetupComplete(true)
        Log.d(TAG, "Setup marked as complete")
        
        // Start background monitoring service
        try {
            val serviceIntent = Intent(this@SetupActivity, DeviceMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "DeviceMonitorService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start DeviceMonitorService", e)
        }
        
        // Setup complete!
        onComplete()
        Log.d(TAG, "✅ Enterprise setup completed successfully")
    }
    
    /**
     * Manual setup flow (fallback for testing/development)
     * Requires manual Google account addition and PIN setup
     */
    private suspend fun runManualSetup(
        onStateChange: (SetupState, String) -> Unit,
        onPinGenerated: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        Log.d(TAG, "Starting manual setup flow")
        
        try {
            // Step 1: Initialize
            onStateChange(SetupState.INITIALIZING, "Preparing device...")
            delay(500)

            val deviceId = DeviceUtils.generateDeviceId(this@SetupActivity)
            preferencesManager.setDeviceId(deviceId)
            Log.d(TAG, "Device ID: $deviceId")

            preferencesManager.setShopInfo(Constants.SHOP_ID, Constants.SHOP_NAME)
            preferencesManager.setBackendUrl(Constants.BACKEND_URL)

            // Step 2: Apply restrictions
            onStateChange(SetupState.APPLYING_RESTRICTIONS, "Applying device protection...")
            delay(500)

            if (policyHelper.isDeviceOwner()) {
                // Suppress all device owner notifications first
                policyHelper.suppressDeviceOwnerNotifications()
                
                // Then apply restrictions
                policyHelper.completeInitialSetup()
                Log.d(TAG, "Device restrictions applied")
            } else {
                Log.w(TAG, "Not device owner - restrictions not applied")
            }


            // Step 3: Collect location and register
            onStateChange(SetupState.COLLECTING_LOCATION, "Getting location...")
            delay(500)

            // Step 4: Register with backend
            onStateChange(SetupState.REGISTERING, "Registering with server...")

            val serialNumber = policyHelper.getDeviceSerialNumber()
            var imei = policyHelper.getDeviceIMEI()
            
            // Handle IMEI for emulator vs real device
            if (imei == null || imei == "000000000000000") {
                // Check if running on emulator
                val isEmulator = android.os.Build.FINGERPRINT.contains("generic") || 
                                 android.os.Build.MODEL.contains("Emulator") ||
                                 android.os.Build.PRODUCT.contains("sdk")
                
                if (isEmulator) {
                    // Use test IMEI for emulator
                    imei = "123456789012345"
                    Log.w(TAG, "Running on emulator - using test IMEI: $imei")
                } else {
                    // Real device without IMEI - error
                    Log.e(TAG, "Failed to get IMEI from real device")
                    throw Exception("IMEI required for device registration. Please ensure telephony permissions are granted.")
                }
            }
            
            // Save IMEI to preferences
            preferencesManager.setImei(imei)
            Log.d(TAG, "IMEI: $imei")

            // Note: firstAccount is already handled in FRP section above

            // Get current location if available
            var currentLocation: Location? = null
            try {
                if (ActivityCompat.checkSelfPermission(
                        this@SetupActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    currentLocation = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        object : CancellationToken() {
                            override fun onCanceledRequested(p0: OnTokenCanceledListener) = CancellationTokenSource().token
                            override fun isCancellationRequested() = false
                        }
                    ).await()
                    Log.d(TAG, "Got location: ${currentLocation?.latitude}, ${currentLocation?.longitude}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get location: ${e.message}")
            }

            // Try to get FCM token if available
            val fcmToken = preferencesManager.getFcmToken()
            Log.d(TAG, "FCM Token available: ${fcmToken != null}")

            val result = deviceRepository.registerDevice(
                deviceId = deviceId,
                serialNumber = serialNumber,
                imei = imei,
                fcmToken = fcmToken,  // May be null if not yet generated
                shopId = Constants.SHOP_ID,
                shopOwnerEmail = policyHelper.getFirstGoogleAccount()?.name,
                devicePin = null,  // No PIN needed
                latitude = currentLocation?.latitude,
                longitude = currentLocation?.longitude
            )

            result.onSuccess { response ->
                Log.d(TAG, "Device registered: ${response.message}")
            }

            result.onFailure { error ->
                Log.e(TAG, "Registration failed: ${error.message}")
                throw error
            }


            // Step 5: Complete
            preferencesManager.setSetupComplete(true)
            
            delay(500)
            onComplete()

        } catch (e: Exception) {
            Log.e(TAG, "Setup error", e)
            onError(e.message ?: "Unknown error")
        }
    }



    private fun finishSetup() {
        try {
            val serviceIntent = Intent(this, DeviceMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "DeviceMonitorService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start DeviceMonitorService", e)
            // Continue anyway - service will start on next boot
        }
        finish()
    }

    private enum class SetupState {
        INITIALIZING,
        GENERATING_PIN,
        APPLYING_RESTRICTIONS,
        WAITING_FOR_ACCOUNT,
        LOCKING_ACCOUNT,
        REGISTERING,
        COMPLETE,
        ERROR
    }
}
