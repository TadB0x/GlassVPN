package com.glassvpn.app

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * Wraps libhev_socks5_tunnel.so (heiher/hev-socks5-tunnel) via JNI.
 *
 * The .so is compiled with -DPKGNAME=com/glassvpn/app -DCLSNAME=TProxyService,
 * so JNI_OnLoad registers the native methods against this exact class.
 *
 * Architecture (all in-process, no subprocess):
 *   Android TUN fd  →  hev-socks5-tunnel (lwip TCP/IP stack)
 *                   →  SOCKS5 127.0.0.1:10808  →  Xray  →  VLESS+REALITY server
 *
 * Passing the TUN fd directly to the native library avoids all FD_CLOEXEC
 * and subprocess fd-inheritance issues entirely.
 */
object TProxyService {

    private const val TAG = "TProxyService"

    @JvmStatic private external fun TProxyStartService(configPath: String, fd: Int)
    @JvmStatic private external fun TProxyStopService()
    @JvmStatic private external fun TProxyGetStats(): LongArray?

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    fun start(context: Context, tunPfd: ParcelFileDescriptor, socksPort: Int = XrayManager.SOCKS_PORT) {
        val config = buildConfig(socksPort)
        val configFile = File(context.filesDir, "hev-socks5-tunnel.yml").apply {
            writeText(config)
        }
        Log.d(TAG, "Config:\n$config")
        TProxyStartService(configFile.absolutePath, tunPfd.fd)
        Log.i(TAG, "hev-socks5-tunnel started (fd=${tunPfd.fd})")
    }

    fun stop() {
        TProxyStopService()
        Log.i(TAG, "hev-socks5-tunnel stopped")
    }

    /**
     * Returns [txPackets, txBytes, rxPackets, rxBytes] or null.
     * Index 1 = upload bytes (tx), index 3 = download bytes (rx).
     */
    fun getStats(): LongArray? = TProxyGetStats()

    private fun buildConfig(socksPort: Int): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: 1500")
        appendLine("  ipv4: '10.0.0.2'")
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: '127.0.0.1'")
        appendLine("  udp: 'udp'")
        appendLine("misc:")
        appendLine("  task-stack-size: 20480")
        appendLine("  connect-timeout: 5000")
        appendLine("  read-write-timeout: 60000")
        appendLine("  log-level: 'warn'")
    }
}
