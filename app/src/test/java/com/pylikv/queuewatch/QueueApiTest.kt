package com.pylikv.queuewatch

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URL
import java.util.Locale
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
        // Use standard sockets: Android's test compiler does not expose the
        // JDK-only com.sun.net.httpserver module.
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val serving = executor.submit {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: SocketException) {
                    if (server.isClosed) break else throw e
                }
                socket.use {
                    it.soTimeout = 3_000
                    val reader = it.getInputStream().bufferedReader(Charsets.UTF_8)
                    reader.readLine() // Request line; the fixture only serves GETs.
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine()?.takeIf(String::isNotEmpty) ?: break
                        val separator = line.indexOf(':')
                        if (separator > 0) {
                            headers[line.substring(0, separator).lowercase(Locale.ROOT)] =
                                line.substring(separator + 1).trim()
                        }
                    }
                    // Independent reference signatures for 16:30/16:31 UTC.
                    val expectedToken = if (now.get() < 1_793_809_860_000L) {
                        "e5305e70688fa1a7723ea208797c3269"
                    } else {
                        "591eef17ac1da3cb4457629d8cff8196"
                    }
                    val word = headers["x-api-word"].orEmpty()
                    val wordPrefix = word.take(8)
                    val wordAt = word.drop(8).take(13).toLongOrNull()
                    val nonce = word.drop(21).toLongOrNull()
                    val wordHash = if (wordAt != null && nonce != null) {
                        MessageDigest.getInstance("SHA-256").digest(
                            "SemenSemenych${wordAt / 60_000L}/info/monitoring-new?token=test&checkpointId=checkpoint$wordPrefix$nonce".toByteArray())
                    } else null
                    val authorized = wordHash?.get(0) == 0.toByte() && wordHash?.get(1) == 0.toByte() &&
                        headers["x-api-key"] == PUBLIC_CLIENT_KEY &&
                        headers["x-api-token"] == expectedToken
                    // The source returns HTTP 200 and empty arrays for a
                    // request missing the official public-client headers.
                    val body = (if (authorized) QUEUE_WITH_VEHICLE else EMPTY_QUEUE)
                        .toByteArray(Charsets.UTF_8)
                    val reason = if (status == 200) "OK" else "Service Unavailable"
                    val response = "HTTP/1.1 $status $reason\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    it.getOutputStream().apply {
                        write(response.toByteArray(Charsets.US_ASCII))
                        write(body)
                        flush()
                    }
                }
            }
        }
        try {
            val api = QueueApi(clock = now::get, openConnection = { requested ->
                URL("http://127.0.0.1:${server.localPort}${requested.file}")
                    .openConnection() as HttpURLConnection
            })
            block(api, now)
        } finally {
            server.close()
            try {
                serving.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
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
