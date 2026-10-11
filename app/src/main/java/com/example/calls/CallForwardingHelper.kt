package com.example.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object CallForwardingHelper {
    fun activateForwarding(context: Context, targetNumber: String) {
        try {
            val mmiCode = Uri.encode("**21*$targetNumber#")
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$mmiCode")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d("CallForwardingHelper", "Activating call forwarding to $targetNumber")
        } catch (e: Exception) {
            Log.e("CallForwardingHelper", "Failed to activate call forwarding", e)
        }
    }

    fun deactivateForwarding(context: Context) {
        try {
            val mmiCode = Uri.encode("##21#")
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$mmiCode")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d("CallForwardingHelper", "Deactivating call forwarding")
        } catch (e: Exception) {
            Log.e("CallForwardingHelper", "Failed to deactivate call forwarding", e)
        }
    }
}
