package com.mux.android.airshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mux.android.airshare.data.Air

/** Keeps the process alive (with a notification) while a file is being sent or received. */
class AirService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!Air.busy()) {
                stopForeground(true)
                stopSelf()
                return
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, build(Air.summary()))
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "File transfers", NotificationManager.IMPORTANCE_LOW))
        }
        val type = if (Build.VERSION.SDK_INT >= 29) 16 else 0      // FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        try {
            ServiceCompat.startForeground(this, ID, build(Air.summary()), type)
        } catch (e: Exception) {
            // not allowed to run in foreground right now: the transfer still works while the app is open
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun build(text: String): Notification {
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_share)
            .setContentTitle("Air Share")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val ID = 3001
        private const val CHANNEL = "airshare_transfer"

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, AirService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
