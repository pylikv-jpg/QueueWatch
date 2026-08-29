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


/**
 * Менеджер фоновых звуковых и голосовых
 * оповещений.
 *
 * Работает независимо от Activity.
 */
class QueueAlertManager(
    private val context: Context
) {

    private val scope =
        CoroutineScope(
            Dispatchers.Main.immediate
        )


    private var repeatJob: Job? =
        null


    private var toneGenerator:
        ToneGenerator? = null


    private var textToSpeech:
        TextToSpeech? = null


    var activeAlert:
        QueueAlert? = null
        private set


    init {

        initializeToneGenerator()

        initializeTextToSpeech()
    }


    /* ========================================================
       TTS
       ======================================================== */

    private fun initializeTextToSpeech() {

        textToSpeech =
            TextToSpeech(
                context
            ) { status ->

                if (
                    status ==
                        TextToSpeech.SUCCESS
                ) {

                    try {

                        textToSpeech?.language =
                            Locale(
                                "ru",
                                "RU"
                            )


                        textToSpeech?.setSpeechRate(
                            0.95f
                        )

                    } catch (_: Exception) {
                    }
                }
            }
    }


    /* ========================================================
       ЗВУК
       ======================================================== */

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


    /* ========================================================
       ЗАПУСК ОПОВЕЩЕНИЯ
       ======================================================== */

    fun trigger(
        type: AlertType,
        message: String
    ) {

        if (
            activeAlert != null
        ) {
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


        activeAlert =
            QueueAlert(

                type = type,

                title = title,

                message = message
            )


        /*
         * Первое оповещение сразу.
         */

        playAlert(
            message
        )


        /*
         * Повтор каждые 60 секунд
         * до подтверждения.
         */

        repeatJob?.cancel()


        repeatJob =
            scope.launch {

                while (isActive) {

                    delay(
                        60_000
                    )


                    val alert =
                        activeAlert
                            ?: break


                    playAlert(
                        alert.message
                    )
                }
            }
    }


    /* ========================================================
       СИГНАЛ + ГОЛОС
       ======================================================== */

    private fun playAlert(
        message: String
    ) {

        try {

            toneGenerator?.startTone(

                ToneGenerator.TONE_PROP_BEEP2,

                700
            )

        } catch (_: Exception) {
        }


        scope.launch {

            delay(
                800
            )


            speak(
                message
            )
        }
    }


    /* ========================================================
       ГОЛОС
       ======================================================== */

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


    /* ========================================================
       ПОДТВЕРЖДЕНИЕ
       ======================================================== */

    fun acknowledge() {

        repeatJob?.cancel()

        repeatJob = null

        activeAlert = null


        try {

            textToSpeech?.stop()

        } catch (_: Exception) {
        }
    }


    /* ========================================================
       СБРОС
       ======================================================== */

    fun reset() {

        repeatJob?.cancel()

        repeatJob = null

        activeAlert = null


        try {

            textToSpeech?.stop()

        } catch (_: Exception) {
        }
    }


    /* ========================================================
       ОСВОБОЖДЕНИЕ
       ======================================================== */

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
    }
}
