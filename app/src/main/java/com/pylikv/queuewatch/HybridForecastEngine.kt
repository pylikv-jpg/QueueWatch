package com.pylikv.queuewatch

import kotlin.math.max
import kotlin.math.min

/**
 * Гибридный двигатель прогнозирования QueueWatch.
 *
 * Основная идея:
 *
 * 1. Позиция автомобиля определяет, сколько позиций осталось.
 * 2. Размер очереди определяет долю очереди.
 * 3. Историческая скорость является базовой моделью.
 * 4. Исторические данные конкретного сегмента имеют приоритет,
 *    если накоплено достаточно наблюдений.
 * 5. Текущая скорость является только корректирующим фактором.
 *
 * Текущая скорость НЕ заменяет историческую модель.
 */
object HybridForecastEngine {

    /**
     * Количество сегментов очереди.
     *
     * 0 = начало очереди
     * 9 = последние 10%
     */
    private const val SEGMENT_COUNT = 10

    /**
     * Минимальное количество наблюдений,
     * после которого сегментная статистика
     * считается пригодной для прогноза.
     */
    private const val MIN_SEGMENT_SAMPLES = 5

    /**
     * Защита от слишком сильного влияния
     * текущей скорости.
     */
    private const val MIN_CORRECTION = 0.65
    private const val MAX_CORRECTION = 1.35

    /**
     * Вес текущей скорости.
     *
     * 0.0 = полностью историческая модель.
     * 1.0 = полностью текущая скорость.
     *
     * На первом этапе используем 35%.
     */
    private const val CURRENT_SPEED_WEIGHT = 0.35

    /**
     * Статистика одного сегмента очереди.
     */
    data class SegmentStat(

        /**
         * Среднее время прохождения
         * одной позиции внутри сегмента.
         */
        val minutesPerPosition: Double,

        /**
         * Количество реальных наблюдений.
         */
        val samples: Int
    )

    /**
     * Результат гибридного прогноза.
     */
    data class Result(

        /**
         * Итоговое время до вызова.
         */
        val estimatedMinutes: Double,

        /**
         * Эффективная скорость,
         * соответствующая полученному ETA.
         */
        val effectiveSpeed: Double?
    )

    /**
     * Расчёт гибридного ETA.
     *
     * @param currentPosition
     *     текущая позиция автомобиля.
     *
     * @param queueCount
     *     количество автомобилей именно
     *     в соответствующей очереди.
     *
     * @param historicalSpeed
     *     историческая скорость,
     *     автомобилей/позиций в час.
     *
     * @param currentSpeed
     *     фактическая текущая скорость,
     *     полученная из последних наблюдений.
     *
     * @param segmentStats
     *     статистика прохождения сегментов.
     */
    fun estimate(
        currentPosition: Int,
        queueCount: Int,
        historicalSpeed: Double?,
        currentSpeed: Double?,
        segmentStats: Map<Int, SegmentStat>
    ): Result {

        val positionsAhead =
            max(
                0,
                currentPosition - 1
            )

        /*
         * Позиция 1 означает,
         * что автомобиль уже первый.
         */
        if (positionsAhead == 0) {

            return Result(
                estimatedMinutes = 0.0,
                effectiveSpeed = historicalSpeed
            )
        }

        /*
         * Размер очереди не может быть меньше
         * текущей позиции.
         */
        val safeQueueCount =
            max(
                queueCount,
                currentPosition
            )

        /*
         * Историческая скорость
         * преобразуется в минуты на позицию.
         */
        val historicalMinutesPerPosition =
            if (
                historicalSpeed != null &&
                historicalSpeed > 0.0
            ) {

                60.0 /
                    historicalSpeed

            } else {

                null
            }

        /*
         * Если исторических данных вообще нет,
         * а текущая скорость есть,
         * используем её как аварийную базу.
         *
         * Но только когда историческая модель
         * действительно отсутствует.
         */
        val fallbackMinutesPerPosition =
            historicalMinutesPerPosition
                ?: if (
                    currentSpeed != null &&
                    currentSpeed > 0.0
                ) {

                    60.0 /
                        currentSpeed

                } else {

                    null
                }

        /*
         * Если вообще нет информации
         * о скорости — прогноз невозможен.
         */
        if (
            fallbackMinutesPerPosition == null
        ) {

            return Result(
                estimatedMinutes = 0.0,
                effectiveSpeed = null
            )
        }

        /*
         * Считаем время прохождения
         * оставшейся части очереди.
         */
        var totalMinutes =
            0.0

        var segmentSamplesUsed =
            0

        for (
            positionAhead
            in positionsAhead downTo 1
        ) {

            /*
             * Доля оставшегося пути
             * относительно всей очереди.
             */
            val fraction =
                positionAhead.toDouble() /
                    safeQueueCount.toDouble()

            /*
             * Определяем сегмент.
             */
            val bucket =
                min(
                    SEGMENT_COUNT - 1,
                    max(
                        0,
                        (
                            fraction *
                                SEGMENT_COUNT
                        ).toInt()
                    )
                )

            val stat =
                segmentStats[bucket]

            /*
             * Используем сегментную статистику
             * только при достаточном количестве
             * реальных наблюдений.
             */
            if (
                stat != null &&
                stat.samples >=
                    MIN_SEGMENT_SAMPLES &&
                stat.minutesPerPosition > 0.0
            ) {

                totalMinutes +=
                    stat.minutesPerPosition

                segmentSamplesUsed +=
                    stat.samples

            } else {

                totalMinutes +=
                    fallbackMinutesPerPosition
            }
        }

        /*
         * Текущая скорость используется
         * только как корректировка.
         *
         * Например:
         *
         * историческая = 20 поз/ч
         * текущая      = 30 поз/ч
         *
         * отношение = 1.5
         *
         * Но мы не разрешаем мгновенно
         * изменить прогноз в 1.5 раза.
         */
        if (
            historicalSpeed != null &&
            historicalSpeed > 0.0 &&
            currentSpeed != null &&
            currentSpeed > 0.0
        ) {

            val ratio =
                currentSpeed /
                    historicalSpeed

            /*
             * Чем больше исторических
             * сегментных данных, тем меньше
             * влияние одного текущего измерения.
             */
            val segmentConfidence =
                min(
                    1.0,
                    segmentSamplesUsed /
                        50.0
                )

            val currentWeight =
                CURRENT_SPEED_WEIGHT *
                    (1.0 - segmentConfidence)

            val correction =
                (
                    1.0 +
                        (
                            ratio - 1.0
                        ) *
                        currentWeight
                )
                    .coerceIn(
                        MIN_CORRECTION,
                        MAX_CORRECTION
                    )

            totalMinutes /=
                correction
        }

        /*
         * Рассчитываем скорость,
         * которая фактически соответствует
         * итоговому прогнозу.
         */
        val effectiveSpeed =
            if (
                totalMinutes > 0.0
            ) {

                positionsAhead.toDouble() *
                    60.0 /
                    totalMinutes

            } else {

                null
            }

        return Result(

            estimatedMinutes =
                totalMinutes,

            effectiveSpeed =
                effectiveSpeed
        )
    }
}
