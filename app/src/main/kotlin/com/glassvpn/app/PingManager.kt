package com.glassvpn.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PingManager {

    private const val SERVER_HOST = "83.228.227.239"
    private const val SERVER_PORT = 443
    private const val TIMEOUT_MS = 5000

    /** TCP connect latency to server (bypasses VPN tunnel for direct RTT). */
    suspend fun measurePing(): Long = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), TIMEOUT_MS)
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        }
    }
}
