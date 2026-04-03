package com.glassvpn.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object XrayManager {

    private const val TAG = "XrayManager"

    @Volatile
    private var controller: CoreController? = null

    fun buildConfig(): String {
        val config = JSONObject()

        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // Stats API — needed for queryStats()
        config.put("stats", JSONObject())
        config.put("policy", JSONObject().apply {
            put("levels", JSONObject().apply {
                put("8", JSONObject().apply {
                    put("connIdle", 300)
                    put("uplinkOnly", 1)
                    put("downlinkOnly", 1)
                    put("statsUserUplink", true)
                    put("statsUserDownlink", true)
                })
            })
            put("system", JSONObject().apply {
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        })

        // DNS
        config.put("dns", JSONObject().apply {
            put("hosts", JSONObject().apply {
                put("domain:googleapis.cn", "googleapis.com")
            })
            put("servers", JSONArray().apply {
                put("8.8.8.8")
                put("8.8.4.4")
                put("localhost")
            })
        })

        // Outbounds
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
                                    put("level", 8)
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
                put("mux", JSONObject().apply {
                    put("enabled", false)
                })
            })
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject())
            })
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject().apply {
                    put("response", JSONObject().apply { put("type", "http") })
                })
            })
        })

        // Routing
        config.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "block")
                    put("domain", JSONArray().apply {
                        put("geosite:category-ads-all")
                    })
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                    put("network", "tcp,udp")
                })
            })
        })

        return config.toString(2)
    }

    suspend fun start(context: Context, tunFd: Int): Boolean = withContext(Dispatchers.IO) {
        if (controller?.isRunning == true) {
            Log.w(TAG, "Xray already running")
            return@withContext true
        }
        try {
            val configJson = buildConfig()
            val configFile = File(context.filesDir, "xray_config.json")
            configFile.writeText(configJson)

            val assetDir = File(context.filesDir, "xray_assets")
            assetDir.mkdirs()

            // Copy geodata from AAR assets if present
            listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat").forEach { name ->
                val dest = File(assetDir, name)
                if (!dest.exists()) {
                    try {
                        context.assets.open(name).use { it.copyTo(dest.outputStream()) }
                    } catch (_: Exception) {}
                }
            }

            Libv2ray.initCoreEnv(assetDir.absolutePath, context.filesDir.absolutePath)

            val handler = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    Log.d(TAG, "Xray status [$p0]: $p1")
                    return 0L
                }
                override fun shutdown(): Long = 0L
                override fun startup(): Long = 0L
            }

            val ctrl = Libv2ray.newCoreController(handler)
            ctrl.startLoop(configFile.absolutePath, tunFd)
            controller = ctrl

            Log.i(TAG, "Xray started with TUN fd=$tunFd")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Xray start exception", e)
            false
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        try {
            controller?.stopLoop()
            Log.i(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Xray stop exception", e)
        } finally {
            controller = null
        }
    }

    fun isRunning(): Boolean = controller?.isRunning == true

    /** Returns total outbound bytes (proxy tag) for download/upload tracking. */
    fun queryDownloadBytes(): Long = try {
        controller?.queryStats("proxy", "downlink") ?: 0L
    } catch (_: Exception) { 0L }

    fun queryUploadBytes(): Long = try {
        controller?.queryStats("proxy", "uplink") ?: 0L
    } catch (_: Exception) { 0L }

    /** Measures server latency using Xray's built-in delay measurement. */
    suspend fun measurePing(): Long = withContext(Dispatchers.IO) {
        try {
            val testConfig = JSONObject().apply {
                put("v", "2")
                put("ps", "ping-test")
                put("add", "83.228.227.239")
                put("port", "443")
                put("id", "080abcaa-d514-4637-af0d-15be3ad1f539")
                put("type", "none")
                put("host", "www.google.com")
            }
            controller?.measureDelay(testConfig.toString()) ?: -1L
        } catch (_: Exception) { -1L }
    }
}
