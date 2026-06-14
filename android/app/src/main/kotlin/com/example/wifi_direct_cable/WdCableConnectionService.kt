package com.example.wifi_direct_cable

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.wifi_direct_cable.diagnostics.DiagnosticsLogger

class WdCableConnectionService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val wakeLockRefresh = object : Runnable {
        override fun run() {
            acquireWakeLockWindow()
            mainHandler.postDelayed(this, WAKE_LOCK_REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wasRunning = isRunning
        startForegroundCompat()
        isRunning = true

        if (intent?.action == ACTION_STOP) {
            DiagnosticsLogger.log("service", "Foreground service stop requested from notification")
            WdCableRuntime.handleNotificationStop()
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWifiLock()
        acquireWakeLockWindow()
        mainHandler.removeCallbacks(wakeLockRefresh)
        mainHandler.postDelayed(wakeLockRefresh, WAKE_LOCK_REFRESH_MS)

        WdCableRuntime.registerReceiver(WdCableRuntime.ReceiverOwner.SERVICE)
        if (!wasRunning) {
            WdCableRuntime.requestConnectionInfoOnce(
                reason = "foreground_service_start",
                dispatchToListener = true
            )
        }

        DiagnosticsLogger.log("service", "Foreground service running", mapOf("wasRunning" to wasRunning))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(wakeLockRefresh)
        WdCableRuntime.unregisterReceiver(WdCableRuntime.ReceiverOwner.SERVICE)
        releaseLocks()
        isRunning = false
        DiagnosticsLogger.log("service", "Foreground service destroyed")
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WdCableConnectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WD Cable connected")
            .setContentText("Keeping the active Wi-Fi Direct session available")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WD Cable connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active Wi-Fi Direct connection status"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "WDCable::WifiDirectSession"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            wifiLockHeld = wifiLock?.isHeld == true
            DiagnosticsLogger.log("service", "Wi-Fi lock acquired", mapOf("held" to wifiLockHeld))
        } catch (exception: Exception) {
            wifiLockHeld = false
            DiagnosticsLogger.log("service", "Wi-Fi lock acquire failed", mapOf("error" to exception.message))
        }
    }

    private fun acquireWakeLockWindow() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val activeWakeLock = wakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WDCable::SessionKeepalive"
            ).apply {
                setReferenceCounted(false)
                wakeLock = this
            }
            activeWakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
            wakeLockHeld = activeWakeLock.isHeld
            DiagnosticsLogger.log("service", "Wake lock refreshed", mapOf("held" to wakeLockHeld))
        } catch (exception: Exception) {
            wakeLockHeld = false
            DiagnosticsLogger.log("service", "Wake lock acquire failed", mapOf("error" to exception.message))
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        } finally {
            wakeLock = null
            wakeLockHeld = false
        }

        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        } finally {
            wifiLock = null
            wifiLockHeld = false
        }
    }

    companion object {
        private const val CHANNEL_ID = "wd_cable_connection"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_START = "com.example.wifi_direct_cable.action.START_CONNECTION_SERVICE"
        private const val ACTION_STOP = "com.example.wifi_direct_cable.action.STOP_CONNECTION_SERVICE"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MS = 5 * 60 * 1000L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var wifiLockHeld: Boolean = false
            private set

        @Volatile
        var wakeLockHeld: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, WdCableConnectionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, WdCableConnectionService::class.java)
            )
        }
    }
}
