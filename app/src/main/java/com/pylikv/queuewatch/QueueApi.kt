package com.pylikv.queuewatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Сетевой слой QueueWatch.
 *
 * Получает реальный JSON электронной очереди
 * с API Belarusborder.
 */
class QueueApi {

    companion object {

        private const val BASE_URL =
            "https://belarusborder.by/info/monitoring-new"

        private const val DEFAULT_TOKEN =
            "test"

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

            connection =
                url.openConnection() as HttpURLConnection

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

                Result.success(body)
            }

        } catch (e: Exception) {

            Result.failure(e)

        } finally {

            connection?.disconnect()
        }
    }
}
