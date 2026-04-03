package com.glassvpn.app

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Manages a bundled tun2socks process that bridges the Android TUN fd to Xray's SOCKS5 proxy.
 * Uses xjasonlyu/tun2socks v2.6.0 static Linux ARM binaries bundled as assets.
 *
 * Invocation: tun2socks -device fd://N -proxy socks5://127.0.0.1:10808 -loglevel error
 */
object Tun2SocksManager {

    private const val TAG = "Tun2SocksManager"
    private const val BINARY_NAME = "tun2socks"

    @Volatile
    private var process: Process? = null

    /** Extracts the binary for the current ABI and returns its path, or null on failure. */
    private fun extractBinary(context: Context): File? {
        val abi = selectAbi()
        val assetPath = "tun2socks/$abi"
        val destFile = File(context.filesDir, BINARY_NAME)

        return try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile.setExecutable(true, false)
            Log.i(TAG, "Extracted $assetPath → ${destFile.absolutePath}")
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract tun2socks binary for $abi", e)
            null
        }
    }

    private fun selectAbi(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> "arm64-v8a" // fallback
        }
    }

    /**
     * Starts tun2socks bridging [tunFd] to Xray's SOCKS5 on port [socksPort].
     * Returns true if the process started successfully.
     */
    fun start(context: Context, tunFd: Int, socksPort: Int = XrayManager.SOCKS_PORT): Boolean {
        stop() // ensure clean state

        val binary = extractBinary(context) ?: return false

        return try {
            val cmd = arrayOf(
                binary.absolutePath,
                "-device", "fd://$tunFd",
                "-proxy", "socks5://127.0.0.1:$socksPort",
                "-loglevel", "error"
            )
            Log.i(TAG, "Starting: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()

            process = pb

            // Read stdout/stderr in background for logging
            Thread {
                try {
                    pb.inputStream.bufferedReader().forEachLine { line ->
                        Log.d(TAG, line)
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                start()
            }

            // Short wait then check it's still alive
            Thread.sleep(300)
            val alive = pb.isAlive
            if (!alive) {
                Log.e(TAG, "tun2socks exited immediately (exit=${pb.exitValue()})")
            } else {
                Log.i(TAG, "tun2socks running (pid via process handle)")
            }
            alive
        } catch (e: Exception) {
            Log.e(TAG, "tun2socks start failed", e)
            false
        }
    }

    fun stop() {
        process?.let { p ->
            try {
                p.destroy()
                Log.i(TAG, "tun2socks stopped")
            } catch (e: Exception) {
                Log.e(TAG, "tun2socks stop error", e)
            }
        }
        process = null
    }

    fun isRunning(): Boolean = process?.isAlive == true
}
