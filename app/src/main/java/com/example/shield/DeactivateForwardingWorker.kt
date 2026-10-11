package com.example.shield

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.calls.CallForwardingHelper

class DeactivateForwardingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        CallForwardingHelper.deactivateForwarding(applicationContext)
        return Result.success()
    }
}
