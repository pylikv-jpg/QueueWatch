package com.pylikv.queuewatch

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class QueueApiTest {
    @Test
    fun authorizedRequestReturnsVehiclesInsteadOfSilentlyEmptyQueue() = runBlocking {
        withSourceServer { api, _ ->
            assertEquals(QUEUE_WITH_VEHICLE, api.getMonitoring("checkpoint").getOrThrow())
        }
    }

    @Test
    fun authorizationRemainsValidUntilEndOfMinute() = runBlocking {
        withSourceServer { api, now ->
            now.set(1_793_809_859_999L)
            assertEquals(QUEUE_WITH_VEHICLE, api.getMonitoring("checkpoint").getOrThrow())
        }
    }

    @Test
    fun nextPollRefreshesAuthorizationWhenMinuteChanges() = runBlocking {
        withSourceServer { api, now ->
            assertEquals(QUEUE_WITH_VEHICLE, api.getMonitoring("checkpoint").getOrThrow())
            now.set(1_793_809_860_000L)
            assertEquals(QUEUE_WITH_VEHICLE, api.getMonitoring("checkpoint").getOrThrow())
        }
    }

    @Test
    fun httpFailureIsStillReportedAsFailure() = runBlocking {
        withSourceServer(status = 503) { api, _ ->
            val result = api.getMonitoring("checkpoint")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 503"))
        }
    }

    private suspend fun withSourceServer(
        status: Int = 200,
        block: suspend (QueueApi, AtomicLong) -> Unit
    ) {
        val now = AtomicLong(1_793_809_800_000L)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/info/monitoring-new") { exchange ->
            // Fixed reference signatures from the official client protocol,
            // independently calculated for 2026-11-04 16:30 and 16:31 UTC.
            val expectedToken = if (now.get() < 1_793_809_860_000L) {
                "e5305e70688fa1a7723ea208797c3269"
            } else {
                "591eef17ac1da3cb4457629d8cff8196"
            }
            val authorized =
                exchange.requestHeaders.getFirst("X-Api-Key") == PUBLIC_CLIENT_KEY &&
                    exchange.requestHeaders.getFirst("X-Api-Token") == expectedToken
            // The actual source returns HTTP 200 and empty arrays when these
            // public-client headers are missing, rather than an HTTP 401.
            val body = (if (authorized) QUEUE_WITH_VEHICLE else EMPTY_QUEUE).toByteArray()
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val api = QueueApi(clock = now::get, openConnection = { requested ->
                URL("http://127.0.0.1:${server.address.port}${requested.file}")
                    .openConnection() as HttpURLConnection
            })
            block(api, now)
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        // Published by the official site's JavaScript; not a user credential.
        const val PUBLIC_CLIENT_KEY =
            "3224b44dfe045c3db9f69fe4d582dba41f7266f6fdabe764ad13a39befd89cf"
        const val EMPTY_QUEUE = """{"truckLiveQueue":[],"carLiveQueue":[],"busLiveQueue":[],"motorcycleLiveQueue":[]}"""
        const val QUEUE_WITH_VEHICLE = """{"truckLiveQueue":[{"regnum":"TEST1001","order_id":266,"status":2,"type_queue":3}],"carLiveQueue":[],"busLiveQueue":[],"motorcycleLiveQueue":[]}"""
    }
}
