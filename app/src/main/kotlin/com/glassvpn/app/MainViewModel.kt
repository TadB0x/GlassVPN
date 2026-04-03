package com.glassvpn.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class UiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val pingMs: Long = -1L,
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val sessionDurationMs: Long = 0L,
    val downloadHistory: List<Float> = List(60) { 0f },
    val uploadHistory: List<Float> = List(60) { 0f },
    val vpnPermissionNeeded: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var statsPollingJob: Job? = null
    private var statsManager: StatsManager? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(GlassVpnService.EXTRA_STATE)) {
                GlassVpnService.STATE_CONNECTED -> {
                    _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTED)
                    startStatsPolling()
                }
                GlassVpnService.STATE_DISCONNECTED -> {
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        downloadSpeedBps = 0L,
                        uploadSpeedBps = 0L,
                        pingMs = -1L,
                    )
                    stopStatsPolling()
                }
                GlassVpnService.STATE_ERROR -> {
                    _uiState.value = _uiState.value.copy(connectionState = ConnectionState.ERROR)
                    stopStatsPolling()
                }
            }
        }
    }

    init {
        val filter = IntentFilter(GlassVpnService.ACTION_STATE_CHANGED)
        application.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Sync with running service on startup
        if (GlassVpnService.isRunning) {
            _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTED)
            startStatsPolling()
        }
    }

    fun toggleConnection(context: Context, vpnPermissionLauncher: () -> Unit) {
        when (_uiState.value.connectionState) {
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                // Check VPN permission
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    // Need to request VPN permission
                    _uiState.value = _uiState.value.copy(vpnPermissionNeeded = true)
                    vpnPermissionLauncher()
                } else {
                    startVpn(context)
                }
            }
            ConnectionState.CONNECTED -> stopVpn(context)
            else -> { /* ignore during transitions */ }
        }
    }

    fun onVpnPermissionGranted(context: Context) {
        _uiState.value = _uiState.value.copy(vpnPermissionNeeded = false)
        startVpn(context)
    }

    fun onVpnPermissionDenied() {
        _uiState.value = _uiState.value.copy(vpnPermissionNeeded = false)
    }

    private fun startVpn(context: Context) {
        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTING)
        val intent = Intent(context, GlassVpnService::class.java).apply {
            action = GlassVpnService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    private fun stopVpn(context: Context) {
        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.DISCONNECTING)
        val intent = Intent(context, GlassVpnService::class.java).apply {
            action = GlassVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun startStatsPolling() {
        val app = getApplication<Application>()
        val sm = StatsManager()
        statsManager = sm
        sm.start(viewModelScope)

        statsPollingJob = viewModelScope.launch(Dispatchers.Main) {
            sm.stats.collect { stats ->
                _uiState.value = _uiState.value.copy(
                    pingMs = stats.pingMs,
                    downloadSpeedBps = stats.downloadSpeedBps,
                    uploadSpeedBps = stats.uploadSpeedBps,
                    totalDownloadBytes = stats.totalDownloadBytes,
                    totalUploadBytes = stats.totalUploadBytes,
                    sessionDurationMs = stats.sessionDurationMs,
                    downloadHistory = stats.downloadHistory,
                    uploadHistory = stats.uploadHistory,
                )
            }
        }
    }

    private fun stopStatsPolling() {
        statsPollingJob?.cancel()
        statsPollingJob = null
        statsManager?.stop()
        statsManager = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
        stopStatsPolling()
    }
}
