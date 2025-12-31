package com.androidmanager.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.*
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.remote.NetworkModule
import com.androidmanager.data.repository.DeviceRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Worker for periodic location updates to backend (every 15 minutes)
 */
class LocationUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "LocationUpdateWorker"
        private const val WORK_NAME = "location_update_work"

        /**
         * Schedule periodic location updates (every 15 minutes)
         * Also runs an immediate location update on first call
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // First, run an IMMEDIATE one-time location update
            val immediateRequest = OneTimeWorkRequestBuilder<LocationUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(immediateRequest)
            Log.d(TAG, "Immediate location update triggered")

            // Then schedule the periodic updates
            val workRequest = PeriodicWorkRequestBuilder<LocationUpdateWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES  // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    2, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Periodic location update worker scheduled (every 15 minutes)")
        }

        /**
         * Cancel scheduled location updates
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running location update")

        val preferencesManager = PreferencesManager(applicationContext)
        
        try {
            // Check if location permission is granted
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Location permission not granted")
                return Result.failure()
            }

            // Get current location
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            
            // Try to get current location first
            var location: android.location.Location? = null
            
            try {
                Log.d(TAG, "Requesting current location...")
                location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    object : CancellationToken() {
                        override fun onCanceledRequested(p0: OnTokenCanceledListener) = CancellationTokenSource().token
                        override fun isCancellationRequested() = false
                    }
                ).await()
                
                if (location != null) {
                    Log.d(TAG, "Got current location: ${location.latitude}, ${location.longitude}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get current location: ${e.message}")
            }

            // If current location failed, try last known location
            if (location == null) {
                Log.d(TAG, "Trying last known location...")
                try {
                    location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        Log.d(TAG, "Got last known location: ${location.latitude}, ${location.longitude}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get last known location: ${e.message}")
                }
            }

            if (location == null) {
                Log.e(TAG, "Could not get any location - retrying later")
                return Result.retry()
            }

            Log.d(TAG, "Using location: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")

            // Initialize network if needed
            val backendUrl = preferencesManager.getBackendUrl()
            if (backendUrl != null && !NetworkModule.isInitialized()) {
                NetworkModule.initialize(backendUrl)
            }

            if (!NetworkModule.isInitialized()) {
                Log.w(TAG, "Network not initialized")
                return Result.retry()
            }

            // Send location update to backend
            val repository = DeviceRepository(preferencesManager)
            val result = repository.sendLocationUpdate(
                latitude = location.latitude,
                longitude = location.longitude
            )

            result.onSuccess { response ->
                Log.d(TAG, "Location updated successfully: ${response.message}")
            }

            result.onFailure { error ->
                Log.e(TAG, "Failed to update location: ${error.message}")
                return Result.retry()
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Location update failed", e)
            return Result.retry()
        }
    }
}
