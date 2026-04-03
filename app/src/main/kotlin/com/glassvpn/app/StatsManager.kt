package com.glassvpn.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NetworkStats(
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val sessionDurationMs: Long = 0L,
    val pingMs: Long = -1L,
    val downloadHistory: List<Float> = List(60) { 0f },
    val uploadHistory: List<Float> = List(60) { 0f },
)

/**
 * Polls hev-socks5-tunnel's native stats every 500ms.
 * TProxyGetStats() returns [txPackets, txBytes, rxPackets, rxBytes].
 * txBytes = upload (device→internet), rxBytes = download (internet→device).
 */
class StatsManager {

    private val _stats = MutableStateFlow(NetworkStats())
    val stats: StateFlow<NetworkStats> = _stats

    private var pollingJob: Job? = null
    private var pingJob: Job? = null
    private var sessionStartMs = 0L

    private var lastTxBytes = 0L
    private var lastRxBytes = 0L
    private var lastSampleMs = 0L

    private val downloadHistory = ArrayDeque<Float>(60).also { q -> repeat(60) { q.addLast(0f) } }
    private val uploadHistory   = ArrayDeque<Float>(60).also { q -> repeat(60) { q.addLast(0f) } }
    private var accumDown = 0L; private var accumUp = 0L; private var accumN = 0
    private var secondTimer = 0L

    fun start(scope: CoroutineScope) {
        sessionStartMs = System.currentTimeMillis()
        lastSampleMs   = sessionStartMs
        secondTimer    = sessionStartMs

        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(500)
                sample()
            }
        }
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val ping = PingManager.measurePing()
                _stats.value = _stats.value.copy(pingMs = ping)
                delay(3000)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel(); pingJob?.cancel()
        pollingJob = null; pingJob = null
    }

    private fun sample() {
        val now = System.currentTimeMillis()
        // [txPackets, txBytes, rxPackets, rxBytes]
        val hevStats = TProxyService.getStats()
        val txBytes = hevStats?.getOrNull(1) ?: 0L  // upload
        val rxBytes = hevStats?.getOrNull(3) ?: 0L  // download

        val dtMs = (now - lastSampleMs).coerceAtLeast(1)
        val dTx = (txBytes - lastTxBytes).coerceAtLeast(0)
        val dRx = (rxBytes - lastRxBytes).coerceAtLeast(0)
        val upBps   = dTx * 1000L / dtMs
        val downBps = dRx * 1000L / dtMs

        lastTxBytes = txBytes; lastRxBytes = rxBytes; lastSampleMs = now

        accumDown += downBps; accumUp += upBps; accumN++

        if (now - secondTimer >= 1000) {
            val avgDown = if (accumN > 0) accumDown / accumN else 0L
            val avgUp   = if (accumN > 0) accumUp   / accumN else 0L
            if (downloadHistory.size >= 60) downloadHistory.removeFirst()
            if (uploadHistory.size   >= 60) uploadHistory.removeFirst()
            downloadHistory.addLast(avgDown.toFloat())
            uploadHistory.addLast(avgUp.toFloat())
            accumDown = 0; accumUp = 0; accumN = 0; secondTimer = now
        }

        _stats.value = _stats.value.copy(
            downloadSpeedBps   = downBps,
            uploadSpeedBps     = upBps,
            totalDownloadBytes = rxBytes,
            totalUploadBytes   = txBytes,
            sessionDurationMs  = now - sessionStartMs,
            downloadHistory    = downloadHistory.toList(),
            uploadHistory      = uploadHistory.toList(),
        )
    }
}

fun Long.formatSpeed(): String = when {
    this >= 1_000_000L -> "%.1f MB/s".format(this / 1_000_000.0)
    this >= 1_000L     -> "%.0f KB/s".format(this / 1_000.0)
    else               -> "$this B/s"
}

fun Long.formatBytes(): String = when {
    this >= 1_073_741_824L -> "%.2f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576L     -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024L         -> "%.0f KB".format(this / 1_024.0)
    else                   -> "$this B"
}

fun Long.formatDuration(): String {
    val s = this / 1000L
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
