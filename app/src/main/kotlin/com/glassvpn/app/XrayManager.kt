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
    const val SOCKS_PORT = 10808

    @Volatile
    private var controller: CoreController? = null

    private fun buildConfig(): String {
        val config = JSONObject()

        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        config.put("stats", JSONObject())
        config.put("policy", JSONObject().apply {
            put("levels", JSONObject().apply {
                put("8", JSONObject().apply {
                    put("connIdle", 300)
                    put("uplinkOnly", 1)
                    put("downlinkOnly", 1)
                })
            })
            put("system", JSONObject().apply {
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        })

        config.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put("8.8.8.8")
                put("8.8.4.4")
            })
        })

        // SOCKS5 inbound — tun2socks will forward TUN traffic here
        config.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "socks")
                put("port", SOCKS_PORT)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                    put("userLevel", 8)
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

        // VLESS+REALITY outbound
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

        config.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
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
        if (controller?.isRunning == true) {
            Log.w(TAG, "Xray already running")
            return@withContext true
        }
        try {
            val assetDir = File(context.filesDir, "xray_assets")
            assetDir.mkdirs()

            // Copy geodata from AAR embedded assets
            listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat").forEach { name ->
                val dest = File(assetDir, name)
                if (!dest.exists()) {
                    try { context.assets.open(name).use { it.copyTo(dest.outputStream()) } }
                    catch (_: Exception) {}
                }
            }

            // initCoreEnv(assetPath, xudpBaseKey)
            Libv2ray.initCoreEnv(assetDir.absolutePath, "")

            val handler = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    Log.d(TAG, "Xray [$p0]: $p1")
                    return 0L
                }
                override fun shutdown(): Long = 0L
                override fun startup(): Long = 0L
            }

            val configJson = buildConfig()
            val ctrl = Libv2ray.newCoreController(handler)
            // startLoop(configContent: String, tunFd: Int)
            // tunFd=0 means no TUN mode — we use SOCKS inbound + tun2socks bridge
            ctrl.startLoop(configJson, 0)
            controller = ctrl

            Log.i(TAG, "Xray started on SOCKS5 port $SOCKS_PORT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Xray start failed", e)
            false
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        try {
            controller?.stopLoop()
            Log.i(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Xray stop error", e)
        } finally {
            controller = null
        }
    }

    fun isRunning(): Boolean = controller?.isRunning == true

    fun queryDownloadBytes(): Long = try {
        controller?.queryStats("proxy", "downlink") ?: 0L
    } catch (_: Exception) { 0L }

    fun queryUploadBytes(): Long = try {
        controller?.queryStats("proxy", "uplink") ?: 0L
    } catch (_: Exception) { 0L }
}
