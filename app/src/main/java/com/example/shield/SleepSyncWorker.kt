package com.example.shield

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ShieldApplication
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

class SleepSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settingsRepo = (applicationContext as ShieldApplication).container.settingsRepository
            val isSleepEnabled = settingsRepo.sleepModeEnabled.first()
            
            if (!isSleepEnabled) {
                return@withContext Result.success()
            }
            
            val startHour = settingsRepo.sleepStartHour.first()
            val startMin = settingsRepo.sleepStartMinute.first()
            val endHour = settingsRepo.sleepEndHour.first()
            val endMin = settingsRepo.sleepEndMinute.first()
            
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMin = now.get(Calendar.MINUTE)
            
            val currentMins = currentHour * 60 + currentMin
            val startMins = startHour * 60 + startMin
            val endMins = endHour * 60 + endMin
            
            val isSleeping = if (startMins < endMins) {
                // Sleep window is within the same day (e.g. 1 PM to 4 PM)
                currentMins in startMins..endMins
            } else {
                // Sleep window crosses midnight (e.g. 10 PM to 7 AM)
                currentMins >= startMins || currentMins <= endMins
            }
            
            val ghostModeBase = settingsRepo.ghostMode.first()
            val wasActivatedBySleep = settingsRepo.sleepGhostModeActive.first()
            
            if (isSleeping) {
                if (!ghostModeBase) {
                    Log.d("SleepSyncWorker", "Sleep time started. Activating Ghost Mode.")
                    settingsRepo.updateBoolean(SettingsRepository.GHOST_MODE, true)
                    settingsRepo.updateBoolean(SettingsRepository.SLEEP_GHOST_MODE_ACTIVE, true)
                }
            } else {
                // If sleep time ended and Sleep mode was the one that activated Ghost Mode, turn it off
                if (wasActivatedBySleep) {
                    settingsRepo.updateBoolean(SettingsRepository.SLEEP_GHOST_MODE_ACTIVE, false)
                    val isCalendarGhostMode = settingsRepo.calendarGhostModeActive.first()
                    if (!isCalendarGhostMode) {
                        Log.d("SleepSyncWorker", "Sleep time ended. Deactivating Ghost Mode.")
                        settingsRepo.updateBoolean(SettingsRepository.GHOST_MODE, false)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SleepSyncWorker", "Error syncing sleep schedule", e)
            Result.failure()
        }
    }
}
