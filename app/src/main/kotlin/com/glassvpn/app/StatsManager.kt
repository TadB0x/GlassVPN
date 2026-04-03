package com.glassvpn.app

import android.net.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NetworkStats(
    val downloadSpeedBps: Long = 0L,   // bytes per second
    val uploadSpeedBps: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val sessionDurationMs: Long = 0L,
    val pingMs: Long = -1L,
    // Ring buffer of last 60 speed samples (one per second)
    val downloadHistory: List<Float> = List(60) { 0f },
    val uploadHistory: List<Float> = List(60) { 0f },
)

/**
 * Polls TrafficStats every 500ms to produce real-time network stats.
 * Uses the app's own UID for traffic accounting.
 */
class StatsManager(private val uid: Int) {

    private val _stats = MutableStateFlow(NetworkStats())
    val stats: StateFlow<NetworkStats> = _stats

    private var pollingJob: Job? = null
    private var pingJob: Job? = null
    private var sessionStartMs: Long = 0L

    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastSampleTime: Long = 0L

    private var sessionBaseRx: Long = 0L
    private var sessionBaseTx: Long = 0L

    // 1-second averaged speed for graph
    private var downloadHistory = ArrayDeque<Float>(60)
    private var uploadHistory = ArrayDeque<Float>(60)
    private var secondAccumDownload: Long = 0L
    private var secondAccumUpload: Long = 0L
    private var secondSamples: Int = 0
    private var secondTimer: Long = 0L

    init {
        repeat(60) {
            downloadHistory.addLast(0f)
            uploadHistory.addLast(0f)
        }
    }

    fun start(scope: CoroutineScope) {
        sessionStartMs = System.currentTimeMillis()
        lastRxBytes = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
        lastTxBytes = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
        sessionBaseRx = lastRxBytes
        sessionBaseTx = lastTxBytes
        lastSampleTime = System.currentTimeMillis()
        secondTimer = lastSampleTime

        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(500L)
                sample()
            }
        }

        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val ping = PingManager.measurePing()
                _stats.value = _stats.value.copy(pingMs = ping)
                delay(3000L)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pingJob?.cancel()
        pollingJob = null
        pingJob = null
    }

    private fun sample() {
        val now = System.currentTimeMillis()
        val currentRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
        val currentTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)

        val dtMs = (now - lastSampleTime).coerceAtLeast(1L)
        val dRx = (currentRx - lastRxBytes).coerceAtLeast(0L)
        val dTx = (currentTx - lastTxBytes).coerceAtLeast(0L)

        val downloadBps = (dRx * 1000L) / dtMs
        val uploadBps = (dTx * 1000L) / dtMs

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastSampleTime = now

        // Accumulate for 1-second graph bucket
        secondAccumDownload += downloadBps
        secondAccumUpload += uploadBps
        secondSamples++

        if (now - secondTimer >= 1000L) {
            val avgDown = if (secondSamples > 0) secondAccumDownload / secondSamples else 0L
            val avgUp = if (secondSamples > 0) secondAccumUpload / secondSamples else 0L

            if (downloadHistory.size >= 60) downloadHistory.removeFirst()
            if (uploadHistory.size >= 60) uploadHistory.removeFirst()
            downloadHistory.addLast(avgDown.toFloat())
            uploadHistory.addLast(avgUp.toFloat())

            secondAccumDownload = 0L
            secondAccumUpload = 0L
            secondSamples = 0
            secondTimer = now
        }

        val totalRx = (currentRx - sessionBaseRx).coerceAtLeast(0L)
        val totalTx = (currentTx - sessionBaseTx).coerceAtLeast(0L)

        _stats.value = _stats.value.copy(
            downloadSpeedBps = downloadBps,
            uploadSpeedBps = uploadBps,
            totalDownloadBytes = totalRx,
            totalUploadBytes = totalTx,
            sessionDurationMs = now - sessionStartMs,
            downloadHistory = downloadHistory.toList(),
            uploadHistory = uploadHistory.toList(),
        )
    }
}

// Formatting helpers
fun Long.formatSpeed(): String {
    return when {
        this >= 1_000_000L -> "%.1f MB/s".format(this / 1_000_000.0)
        this >= 1_000L -> "%.0f KB/s".format(this / 1_000.0)
        else -> "$this B/s"
    }
}

fun Long.formatBytes(): String {
    return when {
        this >= 1_073_741_824L -> "%.2f GB".format(this / 1_073_741_824.0)
        this >= 1_048_576L -> "%.1f MB".format(this / 1_048_576.0)
        this >= 1_024L -> "%.0f KB".format(this / 1_024.0)
        else -> "$this B"
    }
}

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000L
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
