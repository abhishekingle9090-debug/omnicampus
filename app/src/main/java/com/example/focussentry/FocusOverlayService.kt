package com.example.focussentry

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Service component for FocusSentry overlay and background coordination.
 * Declared in AndroidManifest.xml to ensure the OS recognizes FocusSentry background operations.
 */
class FocusOverlayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "FocusOverlayService started.")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "FocusOverlayService destroyed.")
    }

    companion object {
        private const val TAG = "FocusOverlayService"
    }
}
