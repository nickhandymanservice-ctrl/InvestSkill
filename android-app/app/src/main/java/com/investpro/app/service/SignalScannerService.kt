package com.investpro.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.investpro.app.MainActivity
import com.investpro.app.R
import com.investpro.app.data.api.InvestProApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class SignalScannerService : Service() {

    @Inject
    lateinit var api: InvestProApi

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scanInterval = 5 * 60 * 1000L // 5 minutes

    companion object {
        const val CHANNEL_ID = "signal_scanner"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, SignalScannerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SignalScannerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Scanning markets..."))
        startScanning()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startScanning() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val signals = api.scanMarket()
                    val strongSignals = signals.filter { it.confidence > 0.65 }

                    if (strongSignals.isNotEmpty()) {
                        val top = strongSignals.first()
                        val type = top.signalType.replace("_", " ").uppercase()
                        sendSignalNotification(
                            "${top.symbol}: $type",
                            "${top.reasoning} (${(top.confidence * 100).toInt()}% confidence)"
                        )
                    }

                    updateNotification("Last scan: ${java.time.LocalTime.now().toString().take(5)} | ${signals.size} signals found")
                } catch (e: Exception) {
                    updateNotification("Scan error - retrying...")
                }

                delay(scanInterval)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Signal Scanner",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background market signal scanning"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("InvestPro Scanner")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun sendSignalNotification(title: String, body: String) {
        val channelId = "signal_alerts"
        val channel = NotificationChannel(channelId, "Signal Alerts", NotificationManager.IMPORTANCE_HIGH)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
