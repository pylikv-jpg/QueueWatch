package com.pylikv.queuewatch.forecast

import org.junit.Assert.*
import org.junit.Test

class AnalyticsConsentTest {
    private class Memory : ForecastStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(values::remove) }
    }

    @Test fun freshInstallPreselectsParticipationButWaitsForWelcomeChoice() {
        val prefs = Memory(); val consent = AnalyticsConsent(prefs, {})
        consent.initializeDefaults(legacySettingsExist = false)
        assertTrue(consent.isEnabled())
        assertTrue(consent.welcomePending())
        assertNull(consent.currentGrant())
        consent.enable()
        assertFalse(consent.welcomePending())
        assertNotNull(consent.currentGrant())
    }

    @Test fun oldEmptySettingsAndNewExplicitRefusalStayOffAfterUpdates() {
        val prefs = Memory(); val consent = AnalyticsConsent(prefs, {})
        consent.initializeDefaults(legacySettingsExist = true)
        assertFalse(consent.isEnabled())
        assertFalse(consent.welcomePending())
        consent.initializeDefaults(legacySettingsExist = false)
        assertNull(consent.currentGrant())
        consent.enable()
        consent.disable()
        consent.initializeDefaults(legacySettingsExist = false)
        assertFalse(consent.isEnabled())
    }

    @Test fun migrationPreservesAnExistingGrantAndDoesNotReplaceCorruptSettings() {
        val prefs = Memory(); val consent = AnalyticsConsent(prefs, {})
        consent.enable()
        val grant = consent.currentGrant()
        consent.initializeDefaults(legacySettingsExist = true)
        assertEquals(grant, consent.currentGrant())
        val key = prefs.values.keys.single()
        prefs.values[key] = "corrupt"
        consent.initializeDefaults(legacySettingsExist = false)
        assertNull(consent.currentGrant())
        assertFalse(consent.welcomePending())
    }

    @Test fun unreadableLegacyPreferencesRemainDisabledWithoutCrashingMigration() {
        val corrupt = object : ForecastStorage {
            override fun read(key: String): String? = throw ClassCastException("wrong preference type")
            override fun write(key: String, value: String) { fail("Must not replace unreadable settings") }
            override fun remove(keys: Set<String>) { fail("Must not remove unreadable settings") }
        }
        val consent = AnalyticsConsent(corrupt, {})
        consent.initializeDefaults(legacySettingsExist = true)
        assertFalse(consent.isEnabled())
        assertNull(consent.currentGrant())
    }

    @Test fun consentIsOffByDefaultAndUnknownOrCorruptVersionsFailClosed() {
        val prefs = Memory(); val consent = AnalyticsConsent(prefs, {})
        assertNull(consent.currentGrant())
        consent.enable()
        assertNotNull(consent.currentGrant())
        val key = prefs.values.keys.single()
        prefs.values[key] = "{\"version\":999,\"grant\":\"unexpected\"}"
        assertNull(consent.currentGrant())
        prefs.values[key] = "broken"
        assertNull(consent.currentGrant())
        prefs.values[key] = "{\"version\":1,\"grant\":123}"
        assertNull(consent.currentGrant())
        prefs.values[key] = "{\"version\":1,\"grant\":null}"
        assertNull(consent.currentGrant())
    }

    @Test fun revokeClearsPendingAndPermanentlyInvalidatesTheOldGrant() {
        val prefs = Memory(); val data = Memory(); var grants = 0
        val consent = AnalyticsConsent(prefs, { data.values.clear() }, { "grant-${++grants}" })
        consent.enable()
        val old = ConsentGuardedForecastStorage(data, consent.currentGrant()!!, consent::currentGrant)
        old.write("pending", "event")
        assertEquals("event", old.read("pending"))
        consent.disable()
        assertNull(old.read("pending"))
        old.write("pending", "cannot_reappear")
        assertTrue(data.values.isEmpty())
        consent.enable()
        old.write("pending", "still_invalid_after_reconsent")
        assertTrue(data.values.isEmpty())
        assertFalse(old.allowed())
        assertTrue(ConsentGuardedForecastStorage(data, consent.currentGrant()!!, consent::currentGrant).allowed())
    }

    @Test fun aRetryCompletingAfterRevocationCannotResurrectEventsOrSendThemOnTheNextGrant() {
        val prefs = Memory(); val data = Memory(); var grants = 0
        val consent = AnalyticsConsent(prefs, { data.values.clear() }, { "grant-${++grants}" })
        consent.enable()
        val guarded = ConsentGuardedForecastStorage(data, consent.currentGrant()!!, consent::currentGrant)
        val queue = ForecastTelemetryStore(guarded)
        var requests = 0
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport {
            requests++
            consent.disable()
            consent.enable()
            ForecastTelemetrySendOutcome.RETRYABLE_FAILURE
        })
        client.enqueue(PendingForecastTelemetryEvent("event", ForecastTelemetryEventType.OBSERVATION,
            "{}", 0, 0, 0))
        client.flushPending(0) { guarded.allowed() }
        assertEquals(1, requests)
        assertTrue(data.values.isEmpty())
        client.flushPending(120_000) { guarded.allowed() }
        assertEquals(1, requests)
    }

    @Test fun revokedConsentPreventsTransmissionOfAlreadyQueuedEvents() {
        val prefs = Memory(); val data = Memory()
        val consent = AnalyticsConsent(prefs, { data.values.clear() })
        consent.enable()
        val guarded = ConsentGuardedForecastStorage(data, consent.currentGrant()!!, consent::currentGrant)
        var requests = 0
        val client = ForecastTelemetryClient(ForecastTelemetryStore(guarded), ForecastTelemetryTransport {
            requests++; ForecastTelemetrySendOutcome.SUCCESS
        })
        client.enqueue(PendingForecastTelemetryEvent("event", ForecastTelemetryEventType.OBSERVATION, "{}", 0, 0, 0))
        consent.disable()
        client.flushPending(0) { guarded.allowed() }
        assertEquals(0, requests)
    }
}
