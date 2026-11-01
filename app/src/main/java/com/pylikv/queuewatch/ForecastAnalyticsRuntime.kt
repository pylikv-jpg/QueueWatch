package com.pylikv.queuewatch

import android.content.Context
import com.pylikv.queuewatch.forecast.*
import java.time.Instant
import java.time.ZoneId

internal fun analyticsConsent(context: Context): AnalyticsConsent {
    val app = context.applicationContext
    val consent = app.getSharedPreferences(AnalyticsConsent.PREFERENCES, Context.MODE_PRIVATE)
    val data = app.getSharedPreferences(AnalyticsConsent.DATA_PREFERENCES, Context.MODE_PRIVATE)
    return AnalyticsConsent(SharedPreferencesForecastStorage(consent), clearPending = {
        // Keep the random code solely so the user can identify past records for a deletion request.
        val installId = data.getString("forecast_install_id", null)
        data.edit().clear().apply {
            if (installId != null) putString("forecast_install_id", installId)
        }.commit()
    })
}

/** Optional analytics never controls tracking, position notifications or checkpoint-call alerts. */
internal class ForecastAnalyticsRuntime(context: Context) {
    private val app = context.applicationContext
    private val consent = analyticsConsent(app)
    private val rawStorage = SharedPreferencesForecastStorage(
        app.getSharedPreferences(AnalyticsConsent.DATA_PREFERENCES, Context.MODE_PRIVATE))
    private data class Active(
        val grant: String, val storage: ConsentGuardedForecastStorage, val session: ForecastAnalyticsSession
    )
    private var active: Active? = null
    private val history by lazy { HistoricalBaselineRepository(app) }

    @Synchronized
    private fun current(): Active? {
        val currentGrant = consent.currentGrant()
        if (currentGrant == null) {
            active = null
            return null
        }
        if (active?.grant != currentGrant) {
            val guarded = ConsentGuardedForecastStorage(rawStorage, currentGrant, consent::currentGrant)
            val client = ForecastTelemetryClient(ForecastTelemetryStore(guarded),
                ForecastTelemetryHttpTransport(app.getString(R.string.forecast_telemetry_endpoint),
                    app.getString(R.string.forecast_telemetry_public_key)))
            active = Active(currentGrant, guarded, ForecastAnalyticsSession(guarded, client))
        }
        return active
    }

    private fun localKey(car: String, checkpoint: String) = car.trim().uppercase() + "|" + checkpoint

    fun snapshot(car: String, checkpoint: String, vehicle: QueueVehicle?, vehicles: List<QueueVehicle>, analyzer: QueueAnalyzer) {
        val target = current()?.session ?: return
        val at = System.currentTimeMillis()
        val key = localKey(car, checkpoint)
        if (vehicle == null) { target.unavailable(key, checkpoint, at, "MISSING"); return }
        when (analyzer.determineState(vehicle)) {
            VehicleState.IN_QUEUE -> {
                val position = vehicle.position?.takeIf { it > 0 }
                if (position == null) { target.unavailable(key, checkpoint, at, "UNKNOWN"); return }
                val queue = vehicles.filter { it.vehicleType == vehicle.vehicleType &&
                    analyzer.determineState(it) == VehicleState.IN_QUEUE }
                val positions = queue.filter { (it.position ?: 0) > 0 }
                    .associate { it.regnum.trim().uppercase() to it.position!! }
                val hour = Instant.ofEpochMilli(at).atZone(ZoneId.of("Europe/Minsk")).hour
                target.observe(key, checkpoint, vehicle.vehicleType, at, position, queue.size,
                    positions, history.find(checkpoint, vehicle.vehicleType, hour))
            }
            VehicleState.CALLED -> target.called(key, checkpoint, at)
            VehicleState.UNKNOWN -> target.unavailable(key, checkpoint, at, "UNKNOWN")
        }
    }

    fun sourceError(car: String, checkpoint: String) {
        current()?.session?.unavailable(localKey(car, checkpoint), checkpoint, System.currentTimeMillis(), "SOURCE_ERROR")
    }

    fun flush(canContinue: () -> Boolean) {
        val target = current() ?: return
        target.session.flush(System.currentTimeMillis()) { canContinue() && target.storage.allowed() }
    }
}
