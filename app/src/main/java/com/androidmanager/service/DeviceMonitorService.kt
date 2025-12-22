package com.androidmanager.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.androidmanager.R
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.remote.NetworkModule
import com.androidmanager.data.repository.DeviceRepository
import com.androidmanager.manager.DevicePolicyManagerHelper
import com.androidmanager.receiver.ScreenStateReceiver
import com.androidmanager.worker.LocationUpdateWorker
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Background service for device monitoring
 * - Location tracking
 * - Heartbeat to backend
 * - Lock state monitoring
 * - Command polling (backup to FCM)
 */
class DeviceMonitorService : LifecycleService() {

    companion object {
        private const val TAG = "DeviceMonitorService"
        private const val NOTIFICATION_CHANNEL_ID = "emi_device_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_INTERVAL = 15 * 60 * 1000L // 15 minutes
        private const val HEARTBEAT_INTERVAL = 5 * 60 * 1000L // 5 minutes
        private const val COMMAND_POLL_INTERVAL = 60 * 1000L // 1 minute (backup to FCM)
    }

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var policyHelper: DevicePolicyManagerHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var screenStateReceiver: ScreenStateReceiver? = null
    private var locationCallback: LocationCallback? = null
    
    private var heartbeatJob: Job? = null
    private var commandPollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DeviceMonitorService created")

        preferencesManager = PreferencesManager(this)
        deviceRepository = DeviceRepository(preferencesManager)
        policyHelper = DevicePolicyManagerHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Service started in foreground successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            // Continue without foreground - will work in background
        }

        registerScreenStateReceiver()
        initializeService()
    }

    private fun initializeService() {
        lifecycleScope.launch {
            // Wait for setup to be complete
            val isSetupComplete = preferencesManager.isSetupComplete.first()
            if (!isSetupComplete) {
                Log.d(TAG, "Setup not complete, waiting...")
                return@launch
            }

            // Initialize network if needed
            val backendUrl = preferencesManager.getBackendUrl()
            if (backendUrl != null && !NetworkModule.isInitialized()) {
                NetworkModule.initialize(backendUrl)
            }

            // Start location tracking via BackgroundWorker (more reliable than service)
            LocationUpdateWorker.schedule(this@DeviceMonitorService)
            Log.d(TAG, "Location update worker scheduled")

            // Also start direct location tracking from service (legacy/backup)
            startLocationTracking()

            // Start heartbeat
            startHeartbeat()

            // Command polling disabled - using FCM push notifications instead
            // startCommandPolling()

            // Check lock state
            checkAndEnforceLockState()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Device Manager",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "EMI Device Management Service"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Device Manager")
            .setContentText("Device protection active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = ScreenStateReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun startLocationTracking() {
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                LOCATION_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(LOCATION_INTERVAL / 2)
                setWaitForAccurateLocation(false)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        sendLocationToBackend(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            Log.d(TAG, "Location tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
        }
    }

    private fun sendLocationToBackend(location: Location) {
        lifecycleScope.launch {
            try {
                val result = deviceRepository.sendLocationUpdate(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                result.onSuccess { response ->
                    Log.d(TAG, "Location sent successfully: ${response.message}")
                }
                result.onFailure { error ->
                    Log.w(TAG, "Failed to send location: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending location", e)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    deviceRepository.sendHeartbeat()
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }

    private fun startCommandPolling() {
        commandPollJob?.cancel()
        commandPollJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val result = deviceRepository.getPendingCommands()
                    result.onSuccess { commands ->
                        commands.forEach { command ->
                            CommandExecutorService.executeCommand(
                                this@DeviceMonitorService,
                                command
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Command polling failed", e)
                }
                delay(COMMAND_POLL_INTERVAL)
            }
        }
    }

    private fun checkAndEnforceLockState() {
        lifecycleScope.launch {
            val isLocked = preferencesManager.isDeviceLockedSync()
            if (isLocked) {
                // Launch lock screen
                val intent = Intent(this@DeviceMonitorService, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service start command received")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DeviceMonitorService destroyed")

        // Unregister receivers
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }

        // Stop location updates
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }

        // Cancel jobs
        heartbeatJob?.cancel()
        commandPollJob?.cancel()

        // Restart service (it should be persistent)
        val restartIntent = Intent(this, DeviceMonitorService::class.java)
        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
