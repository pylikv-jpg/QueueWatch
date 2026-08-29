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
 * Для каждого отдельного события:
 *
 * 1. Первое оповещение сразу.
 * 2. Повтор через 60 секунд.
 * 3. Максимум 5 оповещений.
 * 4. Подтверждение пользователя
 *    прекращает оповещения раньше.
 * 5. После пятого оповещения
 *    событие автоматически завершается.
 *
 * Новый вызов trigger() после завершения
 * предыдущего события начинает новый
 * независимый цикл из 5 оповещений.
 *
 * Работает независимо от Activity.
 */
class QueueAlertManager(
    private val context: Context
) {

    companion object {

        /**
         * Максимальное количество
         * оповещений для одного события.
         */
        private const val MAX_ALERT_COUNT = 5

        /**
         * Интервал между повторными
         * оповещениями.
         */
        private const val REPEAT_INTERVAL = 60_000L
    }


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


    /**
     * Текущее активное событие.
     *
     * null означает, что активного
     * неподтверждённого события нет.
     */
    var activeAlert:
        QueueAlert? = null
        private set


    /**
     * Количество уже выполненных
     * оповещений для текущего события.
     *
     * Диапазон:
     *
     * 0 — событие ещё не оповещалось
     * 1 — первое оповещение выполнено
     * ...
     * 5 — лимит достигнут.
     */
    private var alertCount =
        0


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

        /*
         * Если уже существует активное
         * неподтверждённое событие,
         * новое событие не затираем.
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


        /*
         * Создаём новое событие.
         *
         * Счётчик обязательно начинается
         * с нуля именно для нового события.
         */
        activeAlert =
            QueueAlert(

                type = type,

                title = title,

                message = message
            )


        alertCount = 0


        /*
         * На случай, если от предыдущего
         * события осталась старая задача.
         */
        repeatJob?.cancel()

        repeatJob = null


        /*
         * Первое оповещение выполняется
         * сразу.
         */
        playAlert(
            message
        )

        alertCount = 1


        /*
         * Если лимит уже достигнут
         * (защита на будущее), событие
         * завершаем.
         */
        if (
            alertCount >=
                MAX_ALERT_COUNT
        ) {

            finishAlert()

            return
        }


        /*
         * Запускаем повторения.
         */
        repeatJob =
            scope.launch {

                while (isActive) {

                    delay(
                        REPEAT_INTERVAL
                    )


                    val alert =
                        activeAlert
                            ?: break


                    /*
                     * Если лимит уже достигнут,
                     * прекращаем цикл.
                     */
                    if (
                        alertCount >=
                            MAX_ALERT_COUNT
                    ) {

                        finishAlert()

                        break
                    }


                    /*
                     * Следующее оповещение.
                     */
                    playAlert(
                        alert.message
                    )


                    alertCount++


                    /*
                     * После пятого оповещения
                     * автоматически завершаем
                     * событие.
                     */
                    if (
                        alertCount >=
                            MAX_ALERT_COUNT
                    ) {

                        finishAlert()

                        break
                    }
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


            /*
             * Если событие уже завершено
             * или подтверждено, голосовое
             * сообщение не запускаем.
             */
            if (
                activeAlert == null
            ) {
                return@launch
            }


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

        /*
         * Останавливаем повторения.
         */
        repeatJob?.cancel()

        repeatJob = null


        /*
         * Удаляем активное событие.
         */
        activeAlert = null


        /*
         * Сбрасываем счётчик.
         *
         * Следующее событие получит
         * новый независимый лимит 5.
         */
        alertCount = 0


        /*
         * Останавливаем текущую речь.
         */
        try {

            textToSpeech?.stop()

        } catch (_: Exception) {
        }
    }


    /* ========================================================
       АВТОМАТИЧЕСКОЕ ЗАВЕРШЕНИЕ
       ======================================================== */

    private fun finishAlert() {

        /*
         * Останавливаем повторную задачу.
         */
        repeatJob?.cancel()

        repeatJob = null


        /*
         * Событие больше не считается
         * активным.
         *
         * Мониторинг автомобиля при этом
         * НЕ останавливается.
         */
        activeAlert = null


        /*
         * Счётчик сбрасывается для
         * следующего нового события.
         */
        alertCount = 0
    }


    /* ========================================================
       СБРОС
       ======================================================== */

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

        alertCount = 0
    }
}
