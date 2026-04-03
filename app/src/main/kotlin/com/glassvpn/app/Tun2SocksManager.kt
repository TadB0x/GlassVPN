package com.glassvpn.app

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File

/**
 * Bridges the Android VPN TUN fd to Xray's SOCKS5 proxy via tun2socks subprocess.
 *
 * Critical: Android sets FD_CLOEXEC on VPN file descriptors, which closes them during
 * fork+exec. We call Os.fcntl(F_SETFD, 0) to clear CLOEXEC before spawning, so the
 * fd remains valid inside the tun2socks process.
 */
object Tun2SocksManager {

    private const val TAG = "Tun2SocksManager"

    @Volatile
    private var process: Process? = null

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

        // Clear FD_CLOEXEC so the fd number survives fork+exec into tun2socks.
        // Without this, Android's O_CLOEXEC default causes the fd to be invalid in the child.
        try {
            Os.fcntl(tunPfd.fileDescriptor, OsConstants.F_SETFD, 0)
            Log.i(TAG, "Cleared FD_CLOEXEC on TUN fd ${tunPfd.fd}")
        } catch (e: Exception) {
            Log.e(TAG, "fcntl F_SETFD failed — fd will likely be invalid in subprocess", e)
            return false
        }

        val fdInt = tunPfd.fd

        return try {
            val pb = ProcessBuilder(
                binary.absolutePath,
                "-device", "fd://$fdInt",
                "-proxy", "socks5://127.0.0.1:$socksPort",
                "-loglevel", "warning"
            ).redirectErrorStream(true).start()

            process = pb

            // Drain output in background for debugging
            Thread {
                try { pb.inputStream.bufferedReader().forEachLine { Log.d(TAG, it) } }
                catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            // Give it 400ms to start or crash
            Thread.sleep(400)
            if (!pb.isAlive) {
                Log.e(TAG, "tun2socks died instantly (exit ${pb.exitValue()})")
                return false
            }
            Log.i(TAG, "tun2socks running on fd://$fdInt → socks5://127.0.0.1:$socksPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "tun2socks launch failed", e)
            false
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
    }

    fun isRunning() = process?.isAlive == true
}
