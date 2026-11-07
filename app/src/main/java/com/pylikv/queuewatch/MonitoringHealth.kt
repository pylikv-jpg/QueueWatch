package com.pylikv.queuewatch

enum class MonitoringHealth { STOPPED, UNCONFIRMED, RETRYING, SEARCHING, RUNNING }

fun monitoringHealth(active: Boolean, heartbeat: Long, now: Long, state: String): MonitoringHealth = when {
    !active -> MonitoringHealth.STOPPED
    heartbeat <= 0L || now - heartbeat !in 0L..90_000L -> MonitoringHealth.UNCONFIRMED
    state in setOf("SOURCE_ERROR", "STALE") -> MonitoringHealth.RETRYING
    state !in setOf("IN_QUEUE", "CALLED") -> MonitoringHealth.SEARCHING
    else -> MonitoringHealth.RUNNING
}
