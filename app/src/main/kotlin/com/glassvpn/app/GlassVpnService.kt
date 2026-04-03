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
            Log.i(TAG, "Starting VPN...")

            // 1. Start Xray core (SOCKS5 inbound on 127.0.0.1:10808)
            val xrayOk = XrayManager.start(this)
            if (!xrayOk) {
                Log.e(TAG, "Xray failed to start")
                broadcastState(STATE_ERROR)
                return
            }

            // Wait for Xray to bind its port
            delay(600L)

            // 2. Establish TUN interface
            val tun = Builder()
                .setSession("GlassVPN")
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                // Exclude our own app so Xray's outbound bypasses the tunnel
                .addDisallowedApplication(packageName)
                .establish()

            if (tun == null) {
                Log.e(TAG, "TUN establish returned null — permission denied?")
                XrayManager.stop()
                broadcastState(STATE_ERROR)
                return
            }
            tunInterface = tun
            Log.i(TAG, "TUN established fd=${tun.fd}")

            // 3. Start tun2socks: TUN fd → Xray SOCKS5 127.0.0.1:10808
            // Pass the ParcelFileDescriptor so we can clear FD_CLOEXEC before exec
            val t2sOk = Tun2SocksManager.start(this, tun)
            if (!t2sOk) {
                Log.e(TAG, "tun2socks failed to start")
                cleanupTun()
                XrayManager.stop()
                broadcastState(STATE_ERROR)
                return
            }

            // 4. Stats
            val sm = StatsManager(applicationInfo.uid)
            statsManager = sm
            sm.start(serviceScope)

            isRunning = true
            broadcastState(STATE_CONNECTED)

            // 5. Notification loop
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

            Log.i(TAG, "VPN fully up")

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
        Tun2SocksManager.stop()
        cleanupTun()
        serviceScope.launch { XrayManager.stop() }
        broadcastState(STATE_DISCONNECTED)
        VpnNotification.update(this, false, -1, 0, 0)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun cleanupTun() {
        try { tunInterface?.close() } catch (e: Exception) { Log.e(TAG, "TUN close", e) }
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
