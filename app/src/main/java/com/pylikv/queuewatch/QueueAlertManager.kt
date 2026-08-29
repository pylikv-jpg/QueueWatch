package com.pylikv.queuewatch

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale


/**
 * Тип оповещения.
 */
enum class AlertType {

    /**
     * Автомобиль достиг заданной позиции
     * или прошёл её скачком.
     */
    POSITION,

    /**
     * До прогнозируемого вызова
     * осталось заданное количество минут.
     */
    FORECAST,

    /**
     * Сервер подтвердил фактический вызов.
     */
    CALLED
}


/**
 * Данные активного оповещения.
 */
data class QueueAlert(

    val type: AlertType,

    val title: String,

    val message: String
)


/**
 * Менеджер звуковых и голосовых оповещений.
 *
 * Логика:
 *
 * 1. Сигнал.
 * 2. Голосовое сообщение.
 * 3. Всплывающее окно.
 * 4. Если пользователь не подтвердил —
 *    повтор через 60 секунд.
 * 5. После подтверждения повтор прекращается.
 */
class QueueAlertManager(
    private val context: Context
) {

    private val scope =
        CoroutineScope(
            Dispatchers.Main.immediate
        )


    private var repeatJob: Job? = null


    private var toneGenerator: ToneGenerator? =
        null


    private var textToSpeech: TextToSpeech? =
        null


    var activeAlert by mutableStateOf<QueueAlert?>(null)
        private set


    init {

        initializeTextToSpeech()

        initializeToneGenerator()
    }


    /* ========================================================
       ИНИЦИАЛИЗАЦИЯ TTS
       ======================================================== */

    private fun initializeTextToSpeech() {

        textToSpeech =
            TextToSpeech(
                context
            ) { status ->

                if (
                    status == TextToSpeech.SUCCESS
                ) {

                    textToSpeech?.language =
                        Locale("ru", "RU")

                    textToSpeech?.setSpeechRate(
                        0.95f
                    )
                }
            }
    }


    /* ========================================================
       ИНИЦИАЛИЗАЦИЯ ЗВУКА
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

        /*
         * Если уже есть активное
         * неподтверждённое сообщение,
         * новое сообщение не затираем.
         *
         * Оно будет обработано после
         * подтверждения текущего.
         */

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
         * Первое оповещение
         * происходит сразу.
         */

        playAlert(
            message
        )


        /*
         * Затем повторяем
         * каждые 60 секунд,
         * пока пользователь
         * не подтвердит.
         */

        repeatJob?.cancel()


        repeatJob =
            scope.launch {

                while (isActive) {

                    delay(60_000)


                    if (
                        activeAlert == null
                    ) {
                        break
                    }


                    playAlert(
                        activeAlert!!.message
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

        /*
         * Сначала звуковой сигнал.
         */

        try {

            toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_BEEP2,
                700
            )

        } catch (_: Exception) {
        }


        /*
         * Небольшая пауза,
         * затем голос.
         */

        scope.launch {

            delay(800)

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
       СБРОС СЕАНСА
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
       ОСВОБОЖДЕНИЕ РЕСУРСОВ
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
