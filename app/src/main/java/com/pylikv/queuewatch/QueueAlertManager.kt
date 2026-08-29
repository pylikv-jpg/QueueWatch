
package com.pylikv.queuewatch

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class AlertType {
    POSITION,
    FORECAST,
    CALLED
}

data class QueueAlert(
    val type: AlertType,
    val title: String,
    val message: String
)

class QueueAlertManager(
    private val context: Context
) {

    companion object {
        private const val MAX_ALERT_COUNT = 5
        private const val REPEAT_INTERVAL = 60_000L
    }

    private val scope =
        CoroutineScope(Dispatchers.Main.immediate)

    private var repeatJob: Job? = null

    private var toneGenerator: ToneGenerator? = null

    private var textToSpeech: TextToSpeech? = null

    var activeAlert: QueueAlert? = null
        private set

    private var alertCount = 0

    init {
        initializeToneGenerator()
        initializeTextToSpeech()
    }

    private fun initializeTextToSpeech() {
        textToSpeech =
            TextToSpeech(context) { status ->

                if (status == TextToSpeech.SUCCESS) {

                    try {
                        textToSpeech?.language =
                            Locale("ru", "RU")

                        textToSpeech?.setSpeechRate(0.95f)

                    } catch (_: Exception) {
                    }
                }
            }
    }

    private fun initializeToneGenerator() {

        try {
            toneGenerator =
                ToneGenerator(
                    AudioManager.STREAM_NOTIFICATION,
                    100
                )

        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    fun trigger(
        type: AlertType,
        message: String
    ) {

        if (activeAlert != null) {
            return
        }

        val title =
            when (type) {
                AlertType.POSITION ->
                    "Оповещение по очереди"

                AlertType.FORECAST ->
                    "Оповещение о приближении вызова"

                AlertType.CALLED ->
                    "ВНИМАНИЕ: ВЫЗОВ"
            }

        val newAlert =
            QueueAlert(
                type = type,
                title = title,
                message = message
            )

        activeAlert = newAlert

        alertCount = 0

        repeatJob?.cancel()
        repeatJob = null

        playAlert(newAlert)

        alertCount = 1

        repeatJob =
            scope.launch {

                while (isActive) {

                    delay(REPEAT_INTERVAL)

                    val current =
                        activeAlert ?: break

                    if (current != newAlert) {
                        break
                    }

                    if (alertCount >= MAX_ALERT_COUNT) {
                        finishAlert()
                        break
                    }

                    playAlert(current)

                    alertCount++

                    if (alertCount >= MAX_ALERT_COUNT) {
                        finishAlert()
                        break
                    }
                }
            }
    }

    private fun playAlert(
        alert: QueueAlert
    ) {

        try {
            toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_BEEP2,
                700
            )
        } catch (_: Exception) {
        }

        scope.launch {

            delay(800)

            if (activeAlert != alert) {
                return@launch
            }

            speak(alert.message)
        }
    }

    private fun speak(
        message: String
    ) {

        try {
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "QueueWatchAlert"
            )
        } catch (_: Exception) {
        }
    }

    fun acknowledge() {

        repeatJob?.cancel()
        repeatJob = null

        activeAlert = null

        alertCount = 0

        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
    }

    private fun finishAlert() {

        repeatJob?.cancel()
        repeatJob = null

        activeAlert = null

        alertCount = 0

        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
    }

    fun reset() {

        repeatJob?.cancel()
        repeatJob = null

        activeAlert = null

        alertCount = 0

        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {

        repeatJob?.cancel()
        repeatJob = null

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }

        textToSpeech = null

        try {
            toneGenerator?.release()
        } catch (_: Exception) {
        }

        toneGenerator = null

        activeAlert = null
        alertCount = 0
    }
}
