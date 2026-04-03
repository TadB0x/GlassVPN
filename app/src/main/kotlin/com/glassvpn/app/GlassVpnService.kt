package com.glassvpn.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GlassVpnService : VpnService() {

    companion object {
        private const val TAG = "GlassVpnService"

        const val ACTION_START = "com.glassvpn.app.START"
        const val ACTION_STOP = "com.glassvpn.app.STOP"
        const val ACTION_STATE_CHANGED = "com.glassvpn.app.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val STATE_ERROR = "error"

        @Volatile
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var statsManager: StatsManager? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        VpnNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(
                    VpnNotification.NOTIFICATION_ID,
                    VpnNotification.build(this, false, -1, 0, 0)
                )
                serviceScope.launch { startVpn() }
                START_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private suspend fun startVpn() {
        try {
            Log.i(TAG, "Starting VPN tunnel...")

            val builder = Builder()
                .setSession("GlassVPN")
                .addAddress("10.0.0.1", 24)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .addDisallowedApplication(packageName)

            tunInterface = builder.establish()
            if (tunInterface == null) {
                Log.e(TAG, "TUN establish returned null")
                broadcastState(STATE_ERROR)
                return
            }

            val tunFd = tunInterface!!.fd
            Log.i(TAG, "TUN established, fd=$tunFd")

            // Pass TUN fd directly to xray-core — no tun2socks bridge needed
            val xrayStarted = XrayManager.start(this, tunFd)
            if (!xrayStarted) {
                Log.e(TAG, "Xray failed to start")
                cleanupTun()
                broadcastState(STATE_ERROR)
                return
            }

            val sm = StatsManager(applicationInfo.uid)
            statsManager = sm
            sm.start(serviceScope)

            isRunning = true
            broadcastState(STATE_CONNECTED)

            notificationJob = serviceScope.launch {
                while (isActive) {
                    val s = sm.stats.value
                    VpnNotification.update(
                        this@GlassVpnService, true,
                        s.pingMs, s.downloadSpeedBps, s.uploadSpeedBps
                    )
                    delay(1000L)
                }
            }

            Log.i(TAG, "VPN fully connected")

        } catch (e: Exception) {
            Log.e(TAG, "startVpn exception", e)
            broadcastState(STATE_ERROR)
            stopVpn()
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN...")
        isRunning = false
        notificationJob?.cancel()
        notificationJob = null
        statsManager?.stop()
        statsManager = null
        serviceScope.launch { XrayManager.stop() }
        cleanupTun()
        broadcastState(STATE_DISCONNECTED)
        VpnNotification.update(this, false, -1, 0, 0)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun cleanupTun() {
        try { tunInterface?.close() } catch (e: Exception) { Log.e(TAG, "TUN close error", e) }
        tunInterface = null
    }

    private fun broadcastState(state: String) {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, state)
            setPackage(packageName)
        })
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
