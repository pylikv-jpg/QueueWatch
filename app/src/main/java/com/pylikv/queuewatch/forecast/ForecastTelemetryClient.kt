package com.pylikv.queuewatch.forecast

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Best-effort, serialized on a separate IO scope; never awaits in the polling loop. */
class ForecastTelemetryClient(context: Context) {
    private val prefs = context.getSharedPreferences("queuewatch_forecast_telemetry", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sending: Job? = null
    val installId: String = prefs.getString("install_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("install_id", it).commit()
    }
    val enabled: Boolean get() = prefs.getBoolean("enabled", true)
    private fun read(): MutableList<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString("pending", "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
    }.getOrDefault(mutableListOf())
    private fun save(items: List<JSONObject>) { prefs.edit().putString("pending", JSONArray(items).toString()).commit() }

    @Synchronized fun enqueue(event: JSONObject) {
        if (!enabled) return
        runCatching {
            val items = read()
            items.add(JSONObject().put("event", event).put("attempt", 0).put("next", 0L))
            while (items.size > 500) {
                val index = items.indexOfFirst { it.getJSONObject("event").optString("event_type") == "prediction" }
                items.removeAt(if (index >= 0) index else 0)
            }
            save(items)
        }
    }

    @Synchronized fun flush() {
        if (!enabled) { save(emptyList()); return }
        if (sending?.isActive == true) return
        sending = scope.launch {
            repeat(15) {
                if (!enabled) return@launch
                val now = System.currentTimeMillis()
                val entry = synchronized(this@ForecastTelemetryClient) {
                    read().firstOrNull { it.optLong("next") <= now }
                } ?: return@launch
                val event = entry.getJSONObject("event")
                val code = runCatching {
                    val connection = URL(ForecastEndpoint.URL).openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 8_000; connection.readTimeout = 8_000
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Authorization", "Bearer ${ForecastEndpoint.ANON_KEY}")
                        connection.setRequestProperty("apikey", ForecastEndpoint.ANON_KEY)
                        connection.outputStream.use { it.write(event.toString().toByteArray(Charsets.UTF_8)) }
                        connection.responseCode
                    } finally { connection.disconnect() }
                }.getOrDefault(0)
                synchronized(this@ForecastTelemetryClient) {
                    val items = read()
                    val index = items.indexOfFirst { it.getJSONObject("event").optString("event_id") == event.optString("event_id") }
                    if (index >= 0) {
                        if (code in 200..299 || code in listOf(400, 413, 415)) {
                            items.removeAt(index)
                            if (code in 200..299) prefs.edit().putLong("last_sent", now).apply()
                        } else {
                            val attempt = entry.optInt("attempt") + 1
                            val delays = longArrayOf(60_000, 300_000, 900_000, 3_600_000, 21_600_000)
                            items[index].put("attempt", attempt).put("next", now + delays[(attempt - 1).coerceAtMost(4)])
                        }
                        save(items)
                    }
                }
                if (code !in 200..299 && code !in listOf(400, 413, 415)) return@launch
            }
        }
    }

    fun close() { scope.cancel() }
}
