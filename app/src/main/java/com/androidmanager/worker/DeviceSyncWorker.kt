package com.androidmanager.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.androidmanager.data.local.PreferencesManager
import com.androidmanager.data.remote.NetworkModule
import com.androidmanager.data.repository.DeviceRepository
import java.util.concurrent.TimeUnit

/**
 * Worker for periodic device sync with backend
 */
class DeviceSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DeviceSyncWorker"
        private const val WORK_NAME = "device_sync_work"

        /**
         * Schedule periodic sync
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DeviceSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Device sync worker scheduled")
        }

        /**
         * Cancel scheduled sync
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running device sync")

        val preferencesManager = PreferencesManager(applicationContext)
        
        try {
            // Initialize network if needed
            val backendUrl = preferencesManager.getBackendUrl()
            if (backendUrl != null && !NetworkModule.isInitialized()) {
                NetworkModule.initialize(backendUrl)
            }

            if (!NetworkModule.isInitialized()) {
                Log.w(TAG, "Network not initialized")
                return Result.retry()
            }

            val repository = DeviceRepository(preferencesManager)
            
            // Heartbeat removed - location updates provide implicit device activity tracking

            // Check for pending commands
            val commandsResult = repository.getPendingCommands()
            commandsResult.onSuccess { commands ->
                Log.d(TAG, "Got ${commands.size} pending commands")
                // Commands will be processed by the repository
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            return Result.retry()
        }
    }
}
