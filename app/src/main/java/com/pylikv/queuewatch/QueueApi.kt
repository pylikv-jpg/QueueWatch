package com.pylikv.queuewatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/**
 * Сетевой слой QueueWatch.
 *
 * Получает реальный JSON электронной очереди
 * с API Belarusborder.
 */
class QueueApi(
    private val clock: () -> Long = System::currentTimeMillis,
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {

    companion object {

        private const val BASE_URL =
            "https://belarusborder.by/info/monitoring-new"

        private const val DEFAULT_TOKEN =
            "test"

        // Public client protocol used by mon.declarant.by. Without these
        // headers the source responds HTTP 200 with silently empty queues.
        // This key is published in the official site's JavaScript, not a
        // private user credential.
        private const val PUBLIC_CLIENT_KEY =
            "3224b44dfe045c3db9f69fe4d582dba41f7266f6fdabe764ad13a39befd89cf"

        private const val TOKEN_SUFFIX = "SemenSemenych"

        private const val CONNECT_TIMEOUT_MS =
            15_000

        private const val READ_TIMEOUT_MS =
            15_000
    }

    /**
     * Получить актуальный снимок очереди.
     *
     * checkpointId — идентификатор пункта пропуска.
     *
     * ВАЖНО:
     * regnum здесь больше НЕ передаём.
     *
     * Реальный пойманный нами запрос:
     *
     * GET /info/monitoring-new
     *     ?token=...
     *     &checkpointId=...
     */
    suspend fun getMonitoring(
        checkpointId: String,
        token: String = DEFAULT_TOKEN
    ): Result<String> = withContext(Dispatchers.IO) {

        var connection: HttpURLConnection? = null

        try {

            val encodedToken =
                URLEncoder.encode(
                    token,
                    Charsets.UTF_8.name()
                )

            val encodedCheckpoint =
                URLEncoder.encode(
                    checkpointId,
                    Charsets.UTF_8.name()
                )

            val url = URL(
                "$BASE_URL" +
                    "?token=$encodedToken" +
                    "&checkpointId=$encodedCheckpoint"
            )

            // Current official public-client protocol. The word is a small
            // SHA-256 proof bound to this exact path, query and minute.
            val word = createApiWord(url.file)

            connection = openConnection(url)

            connection.requestMethod = "GET"

            connection.connectTimeout =
                CONNECT_TIMEOUT_MS

            connection.readTimeout =
                READ_TIMEOUT_MS

            connection.setRequestProperty(
                "Accept",
                "application/json, text/plain, */*"
            )

            connection.setRequestProperty(
                "User-Agent",
                "QueueWatch/1.0 Android"
            )

            connection.setRequestProperty("X-Api-Key", PUBLIC_CLIENT_KEY)
            val tokenInput = (word.substring(8, 21).toLong() / 60_000L).toString() + TOKEN_SUFFIX
            val requestToken = MessageDigest.getInstance("MD5")
                .digest(tokenInput.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
            connection.setRequestProperty("X-Api-Token", requestToken)
            connection.setRequestProperty("X-Api-Word", word)

            val responseCode =
                connection.responseCode

            val stream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                stream?.use { input ->

                    BufferedReader(
                        InputStreamReader(
                            input,
                            Charsets.UTF_8
                        )
                    ).use { reader ->
                        reader.readText()
                    }

                }.orEmpty()

            if (responseCode !in 200..299) {

                Result.failure(
                    IllegalStateException(
                        buildString {

                            append(
                                "Ошибка сервера: HTTP "
                            )

                            append(responseCode)

                            if (body.isNotBlank()) {
                                append("\n")
                                append(body)
                            }
                        }
                    )
                )

            } else {

                val root = JSONObject(body)
                val queues = listOf("truckLiveQueue", "carLiveQueue", "busLiveQueue", "motorcycleLiveQueue")
                check(queues.all { root.optJSONArray(it) != null }) {
                    "Источник вернул неполные данные очереди."
                }
                // HTTP 200 plus empty arrays is also the source's rejection
                // response. It cannot establish absence or an actual call.
                check(root.keys().asSequence().any { (root.opt(it) as? org.json.JSONArray)?.length()?.let { n -> n > 0 } == true }) {
                    "Источник вернул пустые списки. Состояние очереди не подтверждено."
                }
                Result.success(body)
            }

        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {

            Result.failure(e)

        } finally {

            connection?.disconnect()
        }
    }

    private suspend fun createApiWord(path: String): String {
        val random = SecureRandom()
        val digest = MessageDigest.getInstance("SHA-256")
        while (true) {
            val at = clock()
            val minute = at / 60_000L
            val prefix = ByteArray(4).also(random::nextBytes)
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            var nonce = 0L
            while (clock() / 60_000L == minute) {
                if (nonce % 1024L == 0L) currentCoroutineContext().ensureActive()
                val hash = digest.digest("$TOKEN_SUFFIX$minute$path$prefix$nonce".toByteArray(Charsets.UTF_8))
                if (hash[0] == 0.toByte() && hash[1] == 0.toByte()) return "$prefix$at$nonce"
                nonce++
            }
        }
    }
}
