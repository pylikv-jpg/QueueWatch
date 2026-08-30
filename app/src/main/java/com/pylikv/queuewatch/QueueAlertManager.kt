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

        private const val REPEAT_INTERVAL =
            60_000L
    }


    private val scope =
        CoroutineScope(
            Dispatchers.Main.immediate
        )


    private var repeatJob: Job? =
        null


    private var toneJob: Job? =
        null


    private var toneGenerator: ToneGenerator? =
        null


    private var textToSpeech: TextToSpeech? =
        null


    var activeAlert: QueueAlert? =
        null

        private set


    private var alertCount =
        0


    init {

        initializeToneGenerator()

        initializeTextToSpeech()
    }


    /* ========================================================
       ГОЛОС
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
       ЗВУКОВОЙ СИГНАЛ
       ======================================================== */

    private fun initializeToneGenerator() {

        try {

            /*
             * Используем STREAM_ALARM.
             *
             * Раньше использовался STREAM_NOTIFICATION.
             * Поток будильника заметнее и лучше подходит
             * для важного события QueueWatch.
             *
             * 100 — максимальная громкость самого
             * ToneGenerator.
             */

            toneGenerator =
                ToneGenerator(
                    AudioManager.STREAM_ALARM,
                    100
                )

        } catch (_: Exception) {

            toneGenerator =
                null
        }
    }


    /* ========================================================
       ЗАПУСК СОБЫТИЯ
       ======================================================== */

    fun trigger(
        type: AlertType,
        message: String
    ) {

        /*
         * Одновременно может работать
         * только одно предупреждение.
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


        val newAlert =
            QueueAlert(
                type = type,
                title = title,
                message = message
            )


        activeAlert =
            newAlert


        alertCount =
            0


        repeatJob?.cancel()

        repeatJob =
            null


        toneJob?.cancel()

        toneJob =
            null


        /*
         * Первое предупреждение сразу.
         */

        playAlert(
            newAlert
        )


        alertCount =
            1


        /*
         * Повторяем не чаще одного раза
         * в минуту.
         *
         * Максимум — пять предупреждений
         * по одному событию.
         */

        repeatJob =
            scope.launch {

                while (
                    isActive
                ) {

                    delay(
                        REPEAT_INTERVAL
                    )


                    val current =
                        activeAlert
                            ?: break


                    if (
                        current != newAlert
                    ) {

                        break
                    }


                    if (
                        alertCount >=
                        MAX_ALERT_COUNT
                    ) {

                        finishAlert()

                        break
                    }


                    playAlert(
                        current
                    )


                    alertCount++


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
       ЗВОНКИЙ СИГНАЛ + ГОЛОС
       ======================================================== */

    private fun playAlert(
        alert: QueueAlert
    ) {

        toneJob?.cancel()


        toneJob =
            scope.launch {

                /*
                 * Вместо одного короткого сигнала
                 * используем три звонких импульса.
                 */


                playTone(
                    450
                )


                delay(
                    180
                )


                if (
                    activeAlert != alert
                ) {

                    return@launch
                }


                playTone(
                    450
                )


                delay(
                    180
                )


                if (
                    activeAlert != alert
                ) {

                    return@launch
                }


                playTone(
                    650
                )


                /*
                 * Небольшая пауза после сигнала,
                 * затем голосовое сообщение.
                 */

                delay(
                    850
                )


                if (
                    activeAlert != alert
                ) {

                    return@launch
                }


                speak(
                    alert.message
                )
            }
    }


    private fun playTone(
        durationMilliseconds: Int
    ) {

        try {

            toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_BEEP2,
                durationMilliseconds
            )

        } catch (_: Exception) {
        }
    }


    /* ========================================================
       ГОЛОСОВОЕ СООБЩЕНИЕ
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
         * Полностью прекращаем повторы
         * подтверждённого события.
         */

        repeatJob?.cancel()

        repeatJob =
            null


        toneJob?.cancel()

        toneJob =
            null


        activeAlert =
            null


        alertCount =
            0


        try {

            toneGenerator?.stopTone()

        } catch (_: Exception) {
        }


        try {

            textToSpeech?.stop()

        } catch (_: Exception) {
        }
    }


    /* ========================================================
       ПЯТЬ ПРЕДУПРЕЖДЕНИЙ ЗАКОНЧИЛИСЬ
       ======================================================== */

    private fun finishAlert() {

        repeatJob?.cancel()

        repeatJob =
            null


        toneJob?.cancel()

        toneJob =
            null


        activeAlert =
            null


        alertCount =
            0


        try {

            toneGenerator?.stopTone()

        } catch (_: Exception) {
        }


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

        repeatJob =
            null


        toneJob?.cancel()

        toneJob =
            null


        activeAlert =
            null


        alertCount =
            0


        try {

            toneGenerator?.stopTone()

        } catch (_: Exception) {
        }


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

        repeatJob =
            null


        toneJob?.cancel()

        toneJob =
            null


        try {

            toneGenerator?.stopTone()

        } catch (_: Exception) {
        }


        try {

            textToSpeech?.stop()

            textToSpeech?.shutdown()

        } catch (_: Exception) {
        }


        textToSpeech =
            null


        try {

            toneGenerator?.release()

        } catch (_: Exception) {
        }


        toneGenerator =
            null


        activeAlert =
            null


        alertCount =
            0
    }
}
