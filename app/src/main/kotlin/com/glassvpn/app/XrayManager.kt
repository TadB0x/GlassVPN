package com.glassvpn.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wraps AndroidLibXrayLite (libXray) to start/stop the Xray VLESS+REALITY core.
 * The core listens on a local SOCKS5 inbound (127.0.0.1:10808) which tun2socks bridges.
 */
object XrayManager {

    private const val TAG = "XrayManager"
    const val SOCKS_PORT = 10808

    private var isRunning = false

    fun buildConfig(): String {
        val config = JSONObject()

        // Log
        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // Inbounds - SOCKS5 for tun2socks
        config.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("port", SOCKS_PORT)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                    })
                })
            })
        })

        // Outbounds - VLESS+REALITY
        config.put("outbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "proxy")
                put("protocol", "vless")
                put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", "83.228.227.239")
                            put("port", 443)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", "080abcaa-d514-4637-af0d-15be3ad1f539")
                                    put("flow", "xtls-rprx-vision")
                                    put("encryption", "none")
                                })
                            })
                        })
                    })
                })
                put("streamSettings", JSONObject().apply {
                    put("network", "tcp")
                    put("security", "reality")
                    put("realitySettings", JSONObject().apply {
                        put("serverName", "www.google.com")
                        put("fingerprint", "chrome")
                        put("publicKey", "wifAhkRevxiq97-dATR3rI-3T0WsSP_egwTtuIw5E3U")
                        put("shortId", "b7b398f6d5208bd1")
                    })
                })
            })
            // Direct outbound for local/bypass
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
            })
            // Block outbound
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
            })
        })

        // Routing - route everything through proxy
        config.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // Block ads/telemetry (optional, keeps routing clean)
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                    put("network", "tcp,udp")
                })
            })
        })

        return config.toString(2)
    }

    suspend fun start(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.w(TAG, "Xray already running")
            return@withContext true
        }
        try {
            val configJson = buildConfig()
            val configFile = File(context.filesDir, "xray_config.json")
            configFile.writeText(configJson)

            val assetDir = File(context.filesDir, "xray_assets")
            assetDir.mkdirs()

            // Copy geo data assets if available
            copyAssetIfExists(context, "geoip.dat", File(assetDir, "geoip.dat"))
            copyAssetIfExists(context, "geosite.dat", File(assetDir, "geosite.dat"))

            // Set asset path for geoip/geosite resolution
            libXray.LibXray.setAssetPath(assetDir.absolutePath)

            val result = libXray.LibXray.runXray(configFile.absolutePath)
            if (result == null || result.isEmpty()) {
                isRunning = true
                Log.i(TAG, "Xray started successfully")
                true
            } else {
                Log.e(TAG, "Xray start failed: $result")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Xray start exception", e)
            false
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        if (!isRunning) return@withContext
        try {
            libXray.LibXray.stopXray()
            isRunning = false
            Log.i(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Xray stop exception", e)
            isRunning = false
        }
    }

    fun isRunning() = isRunning

    private fun copyAssetIfExists(context: Context, assetName: String, dest: File) {
        try {
            context.assets.open(assetName).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
            // Asset not bundled - geoip/geosite optional for basic routing
        }
    }
}
