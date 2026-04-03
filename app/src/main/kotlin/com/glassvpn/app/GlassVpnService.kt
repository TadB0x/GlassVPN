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
        const val ACTION_STOP  = "com.glassvpn.app.STOP"
        const val ACTION_STATE_CHANGED = "com.glassvpn.app.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val STATE_CONNECTED    = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val STATE_ERROR        = "error"

        @Volatile var isRunning = false
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
            ACTION_STOP -> { stopVpn(); START_NOT_STICKY }
            ACTION_START -> {
                startForeground(VpnNotification.NOTIFICATION_ID,
                    VpnNotification.build(this, false, -1, 0, 0))
                serviceScope.launch { startVpn() }
                START_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private suspend fun startVpn() {
        try {
            Log.i(TAG, "Starting VPN...")

            // 1. Start Xray (SOCKS5 inbound on 127.0.0.1:10808)
            if (!XrayManager.start(this)) {
                Log.e(TAG, "Xray failed to start"); broadcastState(STATE_ERROR); return
            }
            delay(600)

            // 2. Establish TUN interface
            val tun = Builder()
                .setSession("GlassVPN")
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .addDisallowedApplication(packageName) // exclude self → Xray bypasses tunnel
                .establish()

            if (tun == null) {
                Log.e(TAG, "TUN establish failed"); XrayManager.stop(); broadcastState(STATE_ERROR); return
            }
            tunInterface = tun
            Log.i(TAG, "TUN established fd=${tun.fd}")

            // 3. Start hev-socks5-tunnel in-process (same fd, no CLOEXEC issue)
            TProxyService.start(this, tun)

            // 4. Stats (uses hev native stats for accuracy)
            val sm = StatsManager()
            statsManager = sm
            sm.start(serviceScope)

            isRunning = true
            broadcastState(STATE_CONNECTED)

            // 5. Notification update loop
            notificationJob = serviceScope.launch {
                while (isActive) {
                    val s = sm.stats.value
                    VpnNotification.update(this@GlassVpnService, true,
                        s.pingMs, s.downloadSpeedBps, s.uploadSpeedBps)
                    delay(1000)
                }
            }

            Log.i(TAG, "VPN connected")

        } catch (e: Exception) {
            Log.e(TAG, "startVpn failed", e)
            broadcastState(STATE_ERROR)
            stopVpn()
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN...")
        isRunning = false
        notificationJob?.cancel(); notificationJob = null
        statsManager?.stop(); statsManager = null
        TProxyService.stop()
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null
        serviceScope.launch { XrayManager.stop() }
        broadcastState(STATE_DISCONNECTED)
        VpnNotification.update(this, false, -1, 0, 0)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun broadcastState(state: String) {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, state); setPackage(packageName)
        })
    }

    override fun onDestroy() { serviceScope.cancel(); stopVpn(); super.onDestroy() }
    override fun onRevoke() { stopVpn(); super.onRevoke() }
}
