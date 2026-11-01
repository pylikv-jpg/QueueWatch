package com.pylikv.queuewatch.forecast

import org.json.JSONObject
import java.util.UUID

/** An explicit grant is separate from tracking, installation identity and UI visibility. */
class AnalyticsConsent(
    private val storage: ForecastStorage,
    private val clearPending: () -> Unit,
    private val grantId: () -> String = { UUID.randomUUID().toString() }
) {
    companion object {
        const val PREFERENCES = "queuewatch_analytics_consent"
        const val DATA_PREFERENCES = "queuewatch_analytics_data"
        private const val KEY = "analytics_consent_v1"
    }

    fun currentGrant(): String? = runCatching {
        val value = JSONObject(storage.read(KEY) ?: return@runCatching null)
        val token = value.opt("grant")
        if (value.optInt("version") == 1 && token is String && token.isNotBlank()) token else null
    }.getOrNull()

    fun enable() {
        if (currentGrant() != null) return
        clearPending()
        storage.write(KEY, JSONObject().put("version", 1).put("grant", grantId()).toString())
    }

    fun disable() {
        // Revoke before clearing, so in-flight old clients cannot recreate a queue.
        storage.remove(setOf(KEY))
        clearPending()
    }
}

/** Captured grant identity also prevents old requests from resurfacing after re-consent. */
class ConsentGuardedForecastStorage(
    private val underlying: ForecastStorage,
    private val grant: String,
    private val currentGrant: () -> String?
) : ForecastStorage {
    fun allowed(): Boolean = currentGrant() == grant
    override fun read(key: String): String? = if (allowed()) underlying.read(key) else null
    override fun write(key: String, value: String) { if (allowed()) underlying.write(key, value) }
    override fun remove(keys: Set<String>) { if (allowed()) underlying.remove(keys) }
}
