package com.driverdrowsiness.app

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MetricsSocket {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    var state: String = "Disconnected"
        private set

    fun connect(url: String) {
        disconnect()
        state = "Connecting"
        val request = Request.Builder().url(url.trim()).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { state = "Connected" }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "driver_metrics") {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { MainActivity.liveMetrics.value = DriverMetrics.fromJson(json) }
                    }
                } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                state = "Error: ${t.message ?: "connection failed"}"
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                state = "Disconnected"
            }
        })
    }

    fun send(metrics: DriverMetrics) { socket?.send(metrics.toJson().toString()) }
    fun disconnect() { socket?.close(1000, "User disconnected"); socket = null; state = "Disconnected" }
}
