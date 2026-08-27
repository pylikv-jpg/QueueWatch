package com.pilyk.queuewatch

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
 * Получает актуальные данные электронной очереди
 * с BelarusBorder.by.
 *
 * Важно:
 * этот класс пока возвращает исходный JSON-ответ.
 * Разбор конкретных полей очереди вынесем в отдельный
 * слой, чтобы не смешивать сеть и логику прогнозирования.
 */
class QueueApi {

    companion object {
        private const val BASE_URL =
            "https://belarusborder.by/monitoring-new"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    /**
     * Получает данные мониторинга для выбранного пункта
     * и регистрационного номера автомобиля.
     *
     * checkpointId — идентификатор пункта пропуска.
     * regnum — номер автомобиля.
     */
    suspend fun getMonitoring(
        checkpointId: String,
        regnum: String
    ): Result<String> = withContext(Dispatchers.IO) {

        var connection: HttpURLConnection? = null

        try {
            val encodedCheckpoint =
                URLEncoder.encode(checkpointId, Charsets.UTF_8.name())

            val encodedRegnum =
                URLEncoder.encode(regnum, Charsets.UTF_8.name())

            val url = URL(
                "$BASE_URL?checkpointId=$encodedCheckpoint&regnum=$encodedRegnum"
            )

            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            connection.setRequestProperty(
                "Accept",
                "application/json, text/plain, */*"
            )

            connection.setRequestProperty(
                "User-Agent",
                "QueueWatch/1.0 Android"
            )

            val responseCode = connection.responseCode

            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.use { input ->
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
                        "Ошибка сервера: HTTP $responseCode" +
                            if (body.isNotBlank()) {
                                "\n$body"
                            } else {
                                ""
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
