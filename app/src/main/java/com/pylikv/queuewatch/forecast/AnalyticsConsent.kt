package com.pylikv.queuewatch.forecast

import org.json.JSONObject
import java.util.UUID

/** Participation is separate from tracking; an unresolved welcome never sends data. */
class AnalyticsConsent(
    private val storage: ForecastStorage,
    private val clearPending: () -> Unit,
    private val grantId: () -> String = { UUID.randomUUID().toString() }
) {
    companion object {
        internal val LOCK = Any()
        const val PREFERENCES = "queuewatch_analytics_consent"
        const val DATA_PREFERENCES = "queuewatch_analytics_data"
        private const val KEY = "analytics_consent_v1"
    }

    private fun configuredGrant(): String? = runCatching {
        val value = JSONObject(storage.read(KEY) ?: return@runCatching null)
        val token = value.opt("grant")
        if (value.optInt("version") == 1 && token is String && token.isNotBlank()) token else null
    }.getOrNull()

    fun currentGrant(): String? = synchronized(LOCK) {
        if (welcomePending()) null else configuredGrant()
    }

    fun isEnabled(): Boolean = synchronized(LOCK) { configuredGrant() != null }

    fun welcomePending(): Boolean = synchronized(LOCK) {
        runCatching { JSONObject(storage.read(KEY) ?: return@synchronized false)
            .optBoolean("welcome_pending", false) }.getOrDefault(false)
    }

    fun initializeDefaults(legacySettingsExist: Boolean) = synchronized(LOCK) {
        // Old disable() removed KEY, leaving an empty preferences file. Treat
        // that file as a refusal; absence of the grant alone is ambiguous.
        if (runCatching { storage.read(KEY) }.getOrElse { return@synchronized } != null) return@synchronized
        val value = JSONObject().put("version", 1)
        if (legacySettingsExist) value.put("grant", JSONObject.NULL)
        else value.put("grant", grantId()).put("welcome_pending", true)
        storage.write(KEY, value.toString())
    }

    fun enable() = synchronized(LOCK) {
        if (currentGrant() != null) return@synchronized
        clearPending()
        storage.write(KEY, JSONObject().put("version", 1).put("grant", grantId()).toString())
    }

    fun disable() = synchronized(LOCK) {
        // Revoke before clearing, so in-flight old clients cannot recreate a queue.
        storage.write(KEY, JSONObject().put("version", 1).put("grant", JSONObject.NULL).toString())
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
    override fun read(key: String): String? = synchronized(AnalyticsConsent.LOCK) {
        if (allowed()) underlying.read(key) else null
    }
    override fun write(key: String, value: String) = synchronized(AnalyticsConsent.LOCK) {
        if (allowed()) underlying.write(key, value)
    }
    override fun remove(keys: Set<String>) = synchronized(AnalyticsConsent.LOCK) {
        if (allowed()) underlying.remove(keys)
    }
}
