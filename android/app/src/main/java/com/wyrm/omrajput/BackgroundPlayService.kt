package com.wyrm.omrajput

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Background play (OM, 2026-10-13): a match left in the background (recents,
 * screen off) keeps running. The engine keeps the socket, the game and the
 * bot going without drawing (main.c `wyrm_background_tick`); this service
 * keeps the process, the CPU and Wi-Fi awake meanwhile, with a quiet
 * notification that says so. Started by WyrmActivity.onStop while a match is
 * on, stopped by onStart or when the match ends.
 */
class BackgroundPlayService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        }.onFailure { Log.w(TAG, "foreground refused", it) }
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wyrm:background-play")
                ?.apply { setReferenceCounted(false); acquire(MAX_HOLD_MS) }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Wyrm:background-play")
                ?.apply { setReferenceCounted(false); runCatching { acquire() } }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager?.getNotificationChannel(CHANNEL) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Playing in the background", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a match keeps running with Wyrm in the background."
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, WyrmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your match is still running")
            .setContentText("Wyrm keeps playing in the background. Tap to come back.")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "WyrmBackgroundPlay"
        private const val CHANNEL = "background_play"
        private const val NOTIFICATION_ID = 7310
        /** A wake lock always has an end: 6 hours. */
        private const val MAX_HOLD_MS = 6L * 60 * 60 * 1000

        @JvmStatic
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, BackgroundPlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure { Log.w(TAG, "could not start", it) }
        }

        @JvmStatic
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BackgroundPlayService::class.java)) }
        }
    }
}
