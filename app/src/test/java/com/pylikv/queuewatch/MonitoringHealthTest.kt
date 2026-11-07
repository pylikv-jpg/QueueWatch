package com.pylikv.queuewatch

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringHealthTest {
    @Test fun savedActiveFlagCannotClaimTheProcessIsAlive() {
        assertEquals(MonitoringHealth.UNCONFIRMED, monitoringHealth(true, 0, 200_000, "IN_QUEUE"))
        assertEquals(MonitoringHealth.UNCONFIRMED, monitoringHealth(true, 100_000, 200_000, "IN_QUEUE"))
        assertEquals(MonitoringHealth.UNCONFIRMED, monitoringHealth(true, 200_001, 200_000, "IN_QUEUE"))
    }
    @Test fun runningProcessCanRetryAnUnavailableSource() {
        assertEquals(MonitoringHealth.RETRYING, monitoringHealth(true, 199_000, 200_000, "SOURCE_ERROR"))
        assertEquals(MonitoringHealth.RETRYING, monitoringHealth(true, 199_000, 200_000, "STALE"))
    }
    @Test fun stoppedSearchingAndConfirmedStatesRemainDistinct() {
        assertEquals(MonitoringHealth.STOPPED, monitoringHealth(false, 199_000, 200_000, "IN_QUEUE"))
        assertEquals(MonitoringHealth.SEARCHING, monitoringHealth(true, 199_000, 200_000, "MISSING"))
        assertEquals(MonitoringHealth.RUNNING, monitoringHealth(true, 199_000, 200_000, "IN_QUEUE"))
    }
}
