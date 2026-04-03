package com.glassvpn.app

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * Bridges the Android VPN TUN fd to Xray's SOCKS5 proxy via a tun2socks subprocess.
 *
 * Android sets FD_CLOEXEC on all VPN file descriptors, which closes them during
 * fork+exec. The fix: ParcelFileDescriptor.dup() calls dup() under the hood, which
 * does NOT set FD_CLOEXEC on the new fd — so the duplicated fd survives exec() and
 * is valid inside the tun2socks process as fd://N.
 */
object Tun2SocksManager {

    private const val TAG = "Tun2SocksManager"

    @Volatile private var process: Process? = null
    @Volatile private var dupPfd: ParcelFileDescriptor? = null

    private fun extractBinary(context: Context): File? {
        val abi = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64-v8a"
            Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> "arm64-v8a"
        }
        val dest = File(context.filesDir, "tun2socks")
        return try {
            context.assets.open("tun2socks/$abi").use { it.copyTo(dest.outputStream()) }
            dest.setExecutable(true, false)
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Binary extract failed ($abi)", e)
            null
        }
    }

    fun start(context: Context, tunPfd: ParcelFileDescriptor, socksPort: Int = XrayManager.SOCKS_PORT): Boolean {
        stop()
        val binary = extractBinary(context) ?: return false

        // dup() does NOT set FD_CLOEXEC on the new fd (unlike open() which sets it by default
        // on Android 8+). The duplicated fd therefore survives fork+exec into the tun2socks
        // subprocess, making fd://N valid when tun2socks opens it.
        val dup = try {
            ParcelFileDescriptor.dup(tunPfd.fileDescriptor)
        } catch (e: Exception) {
            Log.e(TAG, "dup() failed", e)
            return false
        }
        dupPfd = dup
        val fdInt = dup.fd
        Log.i(TAG, "TUN fd=${tunPfd.fd} → dup fd=$fdInt (no CLOEXEC, survives exec)")

        return try {
            val pb = ProcessBuilder(
                binary.absolutePath,
                "-device", "fd://$fdInt",
                "-proxy", "socks5://127.0.0.1:$socksPort",
                "-loglevel", "warning"
            ).redirectErrorStream(true).start()

            process = pb

            Thread {
                try { pb.inputStream.bufferedReader().forEachLine { Log.d(TAG, it) } }
                catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            Thread.sleep(500)
            if (!pb.isAlive) {
                Log.e(TAG, "tun2socks died instantly (exit ${pb.exitValue()})")
                dup.close()
                dupPfd = null
                return false
            }
            Log.i(TAG, "tun2socks up: fd://$fdInt → socks5://127.0.0.1:$socksPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed", e)
            dup.close()
            dupPfd = null
            false
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
        try { dupPfd?.close() } catch (_: Exception) {}
        dupPfd = null
    }

    fun isRunning() = process?.isAlive == true
}
