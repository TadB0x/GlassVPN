package com.glassvpn.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures latency to the VPN server via TCP connect timing.
 * Uses the actual VLESS server address (not through the tunnel) for accurate RTT.
 */
object PingManager {

    private const val SERVER_HOST = "83.228.227.239"
    private const val SERVER_PORT = 443
    private const val TIMEOUT_MS = 5000

    /**
     * Returns latency in milliseconds, or -1 on failure.
     */
    suspend fun measurePing(): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), TIMEOUT_MS)
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        }
    }
}
