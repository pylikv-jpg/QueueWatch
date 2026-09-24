package com.pylikv.queuewatch.forecast

import android.content.SharedPreferences
import java.util.UUID

interface ForecastStorage {

    fun read(
        key: String
    ): String?

    fun write(
        key: String,
        value: String
    )

    fun remove(
        keys: Set<String>
    )
}

class SharedPreferencesForecastStorage(
    private val preferences:
        SharedPreferences
) : ForecastStorage {

    override fun read(
        key: String
    ): String? =
        preferences.getString(
            key,
            null
        )

    override fun write(
        key: String,
        value: String
    ) {
        preferences.edit()
            .putString(
                key,
                value
            )
            .apply()
    }

    override fun remove(
        keys: Set<String>
    ) {
        val editor =
            preferences.edit()

        keys.forEach {
            editor.remove(
                it
            )
        }

        editor.apply()
    }
}

data class StoredForecast(
    val etaMinutes: Double,
    val lowMinutes: Double,
    val highMinutes: Double,
    val confidence: ForecastConfidence,
    val effectivePositionsPerHour: Double,
    val algorithmVersion: String,
    val updatedAtMillis: Long
)

class ForecastSessionStore(
    private val storage:
        ForecastStorage,
    private val sessionIdFactory:
        () -> String = {
            UUID.randomUUID()
                .toString()
        }
) {

    companion object {
        const val KEY_SESSION_ID =
            "forecast_session_id"

        const val KEY_SESSION_CAR_KEY =
            "forecast_session_car_key"

        const val KEY_ETA_MINUTES =
            "forecast_eta_minutes"

        const val KEY_LOW_MINUTES =
            "forecast_low_minutes"

        const val KEY_HIGH_MINUTES =
            "forecast_high_minutes"

        const val KEY_CONFIDENCE =
            "forecast_confidence"

        const val KEY_EFFECTIVE_SPEED =
            "forecast_effective_speed"

        const val KEY_ALGORITHM_VERSION =
            "forecast_algorithm_version"

        const val KEY_UPDATED_AT =
            "forecast_updated_at"

        const val KEY_CALLED_AT =
            "forecast_called_at"

        private val VISIBLE_KEYS =
            setOf(
                KEY_ETA_MINUTES,
                KEY_LOW_MINUTES,
                KEY_HIGH_MINUTES,
                KEY_CONFIDENCE,
                KEY_EFFECTIVE_SPEED,
                KEY_ALGORITHM_VERSION,
                KEY_UPDATED_AT
            )

        private val ALL_KEYS =
            VISIBLE_KEYS +
                setOf(
                    KEY_SESSION_ID,
                    KEY_SESSION_CAR_KEY,
                    KEY_CALLED_AT
                )
    }

    fun ensureSession(
        localCarKey: String
    ): String {
        require(
            localCarKey.isNotBlank()
        )

        val savedCarKey =
            storage.read(
                KEY_SESSION_CAR_KEY
            )

        val savedSessionId =
            storage.read(
                KEY_SESSION_ID
            )

        if (
            savedCarKey ==
                localCarKey &&
            !savedSessionId
                .isNullOrBlank()
        ) {
            return savedSessionId
        }

        clearAll()

        val sessionId =
            sessionIdFactory()

        storage.write(
            KEY_SESSION_CAR_KEY,
            localCarKey
        )

        storage.write(
            KEY_SESSION_ID,
            sessionId
        )

        return sessionId
    }

    fun saveAvailable(
        localCarKey: String,
        result:
            ForecastResult.Available,
        updatedAtMillis: Long
    ) {
        ensureSession(
            localCarKey
        )

        storage.remove(
            setOf(
                KEY_CALLED_AT
            )
        )

        storage.write(
            KEY_ETA_MINUTES,
            result.etaMinutes
                .toString()
        )

        storage.write(
            KEY_LOW_MINUTES,
            result.lowMinutes
                .toString()
        )

        storage.write(
            KEY_HIGH_MINUTES,
            result.highMinutes
                .toString()
        )

        storage.write(
            KEY_CONFIDENCE,
            result.confidence
                .name
        )

        storage.write(
            KEY_EFFECTIVE_SPEED,
            result
                .effectivePositionsPerHour
                .toString()
        )

        storage.write(
            KEY_ALGORITHM_VERSION,
            result.algorithmVersion
        )

        storage.write(
            KEY_UPDATED_AT,
            updatedAtMillis
                .toString()
        )
    }

    fun read(
        localCarKey: String
    ): StoredForecast? {
        if (
            storage.read(
                KEY_SESSION_CAR_KEY
            ) !=
            localCarKey
        ) {
            clearAll()
            return null
        }

        val eta =
            storage.read(
                KEY_ETA_MINUTES
            )
                ?.toDoubleOrNull()
                ?: return null

        val low =
            storage.read(
                KEY_LOW_MINUTES
            )
                ?.toDoubleOrNull()
                ?: return null

        val high =
            storage.read(
                KEY_HIGH_MINUTES
            )
                ?.toDoubleOrNull()
                ?: return null

        val confidence =
            storage.read(
                KEY_CONFIDENCE
            )
                ?.let {
                    runCatching {
                        ForecastConfidence
                            .valueOf(
                                it
                            )
                    }.getOrNull()
                }
                ?: return null

        val effectiveSpeed =
            storage.read(
                KEY_EFFECTIVE_SPEED
            )
                ?.toDoubleOrNull()
                ?: return null

        val algorithmVersion =
            storage.read(
                KEY_ALGORITHM_VERSION
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val updatedAt =
            storage.read(
                KEY_UPDATED_AT
            )
                ?.toLongOrNull()
                ?: return null

        return StoredForecast(
            etaMinutes =
                eta,
            lowMinutes =
                low,
            highMinutes =
                high,
            confidence =
                confidence,
            effectivePositionsPerHour =
                effectiveSpeed,
            algorithmVersion =
                algorithmVersion,
            updatedAtMillis =
                updatedAt
        )
    }

    fun clearVisible(
        localCarKey: String
    ) {
        if (
            storage.read(
                KEY_SESSION_CAR_KEY
            ) ==
            localCarKey
        ) {
            storage.remove(
                VISIBLE_KEYS
            )
        }
    }

    fun markCalled(
        localCarKey: String,
        calledAtMillis: Long
    ) {
        if (
            storage.read(
                KEY_SESSION_CAR_KEY
            ) !=
            localCarKey
        ) {
            return
        }

        if (
            storage.read(
                KEY_SESSION_ID
            ).isNullOrBlank()
        ) {
            return
        }

        storage.remove(
            VISIBLE_KEYS
        )

        storage.write(
            KEY_CALLED_AT,
            calledAtMillis
                .toString()
        )
    }

    fun currentSessionId(
        localCarKey: String
    ): String? {
        if (
            storage.read(
                KEY_SESSION_CAR_KEY
            ) !=
            localCarKey
        ) {
            return null
        }

        return storage.read(
            KEY_SESSION_ID
        )
            ?.takeIf {
                it.isNotBlank()
            }
    }

    fun calledAtMillis(
        localCarKey: String
    ): Long? {
        if (
            storage.read(
                KEY_SESSION_CAR_KEY
            ) !=
            localCarKey
        ) {
            return null
        }

        return storage.read(
            KEY_CALLED_AT
        )
            ?.toLongOrNull()
    }

    private fun clearAll() {
        storage.remove(
            ALL_KEYS
        )
    }
}
