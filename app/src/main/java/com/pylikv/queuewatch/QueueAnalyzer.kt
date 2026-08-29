package com.pylikv.queuewatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * Анализатор электронной очереди QueueWatch.
 *
 * Основная модель скорости:
 *
 * 1. Историческая скорость:
 *    КПП + тип транспорта + день недели + час.
 *
 * 2. Сегментная статистика:
 *    очередь условно делится на 10 сегментов.
 *
 * 3. Текущая скорость:
 *    рассчитывается по реальным изменениям
 *    позиций автомобилей.
 *
 * 4. Гибридный прогноз:
 *    историческая модель + сегментная статистика
 *    + ограниченная корректировка текущей скоростью.
 *
 * ВАЖНО:
 *
 * Текущая скорость НЕ является единственным
 * источником прогноза.
 */
class QueueAnalyzer(
    context: Context
) {

    companion object {

        /**
         * Количество сегментов очереди.
         */
        private const val SEGMENT_COUNT = 10

        /**
         * Минимальное количество
         * сегментных наблюдений.
         */
        private const val MIN_SEGMENT_SAMPLES = 5

        /**
         * Минимальное количество
         * измерений текущей скорости.
         */
        private const val MIN_CURRENT_SPEED_SAMPLES = 2

        /**
         * Максимальный возраст
         * измерения текущей скорости.
         *
         * 30 минут.
         */
        private const val CURRENT_SPEED_MAX_AGE =
            30 * 60 * 1000L

        /**
         * Минимальный интервал между
         * записями наблюдаемого часа.
         */
        private const val OBSERVATION_INTERVAL =
            5 * 60 * 1000L

        /**
         * Максимальный размер истории
         * одного автомобиля в памяти.
         */
        private const val MAX_HISTORY_POINTS = 100

        /**
         * Максимальная физически допустимая
         * скорость для одного расчёта.
         *
         * Это защита от ошибочных скачков API.
         */
        private const val MAX_CURRENT_SPEED =
            200.0
    }


    private val appContext =
        context.applicationContext


    private val preferences =
        appContext.getSharedPreferences(
            "queuewatch_statistics",
            Context.MODE_PRIVATE
        )


    /**
     * История конкретных автомобилей.
     *
     * Хранится в памяти текущего запуска
     * мониторинга.
     */
    private val history =
        mutableMapOf<
            String,
            MutableList<QueueHistoryPoint>
        >()


    /**
     * Последняя серверная запись
     * автомобиля.
     */
    private val previousVehicles =
        mutableMapOf<
            String,
            QueueVehicle
        >()


    /**
     * Последнее количество автомобилей
     * конкретного типа очереди.
     */
    private val previousQueueCounts =
        mutableMapOf<VehicleType, Int>()


    /**
     * Последний момент наблюдения
     * конкретного автомобиля.
     */
    private val previousObservationTime =
        mutableMapOf<String, Long>()


    /**
     * Последняя рассчитанная
     * текущая скорость.
     *
     * Хранится отдельно для каждого
     * типа транспорта.
     */
    private val currentSpeeds =
        mutableMapOf<
            VehicleType,
            CurrentQueueSpeed
        >()


    /**
     * Уже обработанные события вызова.
     */
    private val processedCallEvents =
        mutableSetOf<String>()


    /**
     * Нормализация номера автомобиля.
     */
    private fun normalizeRegnum(
        value: String
    ): String {

        return value
            .uppercase()
            .replace("\\s".toRegex(), "")
            .replace("-", "")
            .trim()
    }


    /**
     * Разбор полного JSON очереди.
     */
    fun parseQueue(
        json: String
    ): List<QueueVehicle> {

        val root =
            JSONObject(json)

        val result =
            mutableListOf<QueueVehicle>()


        parseQueueArray(
            root,
            "carLiveQueue",
            VehicleType.CAR,
            result
        )

        parseQueueArray(
            root,
            "truckLiveQueue",
            VehicleType.TRUCK,
            result
        )

        parseQueueArray(
            root,
            "busLiveQueue",
            VehicleType.BUS,
            result
        )

        parseQueueArray(
            root,
            "motorcycleLiveQueue",
            VehicleType.MOTORCYCLE,
            result
        )

        return result
    }


    /**
     * Разбор одной очереди.
     */
    private fun parseQueueArray(
        root: JSONObject,
        arrayName: String,
        vehicleType: VehicleType,
        result: MutableList<QueueVehicle>
    ) {

        val queue =
            root.optJSONArray(arrayName)
                ?: JSONArray()


        for (
            i in
            0 until queue.length()
        ) {

            val item =
                queue.optJSONObject(i)
                    ?: continue


            val regnum =
                item.optString(
                    "regnum",
                    ""
                ).trim()


            if (
                regnum.isEmpty()
            ) {
                continue
            }


            result += QueueVehicle(

                regnum =
                    regnum,

                status =
                    readNullableInt(
                        item,
                        "status"
                    ),

                orderId =
                    readNullableInt(
                        item,
                        "order_id"
                    ),

                typeQueue =
                    readNullableInt(
                        item,
                        "type_queue"
                    ),

                registrationDate =
                    readNullableString(
                        item,
                        "registration_date"
                    ),

                changedDate =
                    readNullableString(
                        item,
                        "changed_date"
                    ),

                vehicleType =
                    vehicleType
            )
        }
    }


    /**
     * Чтение nullable Int.
     */
    private fun readNullableInt(
        item: JSONObject,
        key: String
    ): Int? {

        if (
            !item.has(key) ||
            item.isNull(key)
        ) {
            return null
        }


        return when (
            val value =
                item.opt(key)
        ) {

            is Number ->
                value.toInt()

            is String ->
                value.trim()
                    .toIntOrNull()

            else ->
                null
        }
    }


    /**
     * Чтение nullable String.
     */
    private fun readNullableString(
        item: JSONObject,
        key: String
    ): String? {

        if (
            !item.has(key) ||
            item.isNull(key)
        ) {
            return null
        }


        return item
            .optString(key)
            .trim()
            .ifEmpty {
                null
            }
    }


    /**
     * Поиск автомобиля.
     */
    fun findVehicle(
        json: String,
        regnum: String
    ): QueueVehicle? {

        val target =
            normalizeRegnum(
                regnum
            )


        if (
            target.isEmpty()
        ) {
            return null
        }


        return parseQueue(json)
            .firstOrNull {

                normalizeRegnum(
                    it.regnum
                ) == target
            }
    }


    /**
     * Определение состояния автомобиля.
     *
     * status=3 всегда имеет приоритет.
     */
    fun determineState(
        vehicle: QueueVehicle
    ): VehicleState {

        if (
            vehicle.status == 3
        ) {

            return VehicleState.CALLED
        }


        if (
            vehicle.orderId != null
        ) {

            return VehicleState.IN_QUEUE
        }


        return VehicleState.UNKNOWN
    }


    /**
     * Обработка нового снимка.
     *
     * Здесь одновременно:
     *
     * - сохраняется история позиций;
     * - фиксируется текущий размер очереди;
     * - рассчитывается текущая скорость;
     * - записывается статистика вызовов;
     * - записывается сегментная статистика.
     */
    fun processSnapshot(
        json: String,
        checkpointName: String = "unknown",
        timestampMillis: Long =
            System.currentTimeMillis()
    ): List<QueueVehicle> {

        val vehicles =
            parseQueue(json)


        /*
         * Считаем количество автомобилей
         * отдельно по каждому типу.
         */
        val currentCounts =
            mutableMapOf<VehicleType, Int>()


        for (
            vehicle in
            vehicles
        ) {

            currentCounts[
                vehicle.vehicleType
            ] =
                (
                    currentCounts[
                        vehicle.vehicleType
                    ] ?: 0
                ) + 1
        }


        /*
         * Обрабатываем историю каждого
         * автомобиля.
         */
        for (
            vehicle in
            vehicles
        ) {

            val normalizedRegnum =
                normalizeRegnum(
                    vehicle.regnum
                )


            val state =
                determineState(
                    vehicle
                )


            val previous =
                previousVehicles[
                    normalizedRegnum
                ]


            if (
                state ==
                    VehicleState.IN_QUEUE &&
                vehicle.position != null
            ) {

                processVehiclePosition(
                    checkpointName =
                        checkpointName,

                    vehicle =
                        vehicle,

                    previous =
                        previous,

                    currentQueueCount =
                        currentCounts[
                            vehicle.vehicleType
                        ] ?: 0,

                    timestampMillis =
                        timestampMillis
                )
            }


            /*
             * Подтверждённый вызов.
             */
            if (
                state ==
                    VehicleState.CALLED
            ) {

                processCalledVehicle(
                    checkpointName =
                        checkpointName,

                    vehicle =
                        vehicle,

                    timestampMillis =
                        timestampMillis
                )
            }


            previousVehicles[
                normalizedRegnum
            ] =
                vehicle
        }


        /*
         * Обновляем текущую скорость
         * после обработки всех изменений.
         */
        for (
            vehicleType
            in VehicleType.values()
        ) {

            updateCurrentSpeed(
                vehicleType =
                    vehicleType,

                currentQueueCount =
                    currentCounts[
                        vehicleType
                    ] ?: 0,

                timestampMillis =
                    timestampMillis
            )
        }


        /*
         * Фиксируем наблюдение часа.
         */
        val calendar =
            Calendar.getInstance().apply {

                timeInMillis =
                    timestampMillis
            }


        val dayOfWeek =
            calendar.get(
                Calendar.DAY_OF_WEEK
            )


        val hour =
            calendar.get(
                Calendar.HOUR_OF_DAY
            )


        for (
            vehicleType
            in VehicleType.values()
        ) {

            markHourObserved(
                checkpointName =
                    checkpointName,

                vehicleType =
                    vehicleType,

                dayOfWeek =
                    dayOfWeek,

                hour =
                    hour
            )
        }


        return vehicles
    }


    /**
     * Обработка изменения позиции
     * конкретного автомобиля.
     *
     * Это НЕ используется как единственный
     * источник прогноза.
     *
     * Изменение позиции используется
     * для формирования:
     *
     * - текущей скорости;
     * - сегментной статистики.
     */
    private fun processVehiclePosition(
        checkpointName: String,
        vehicle: QueueVehicle,
        previous: QueueVehicle?,
        currentQueueCount: Int,
        timestampMillis: Long
    ) {

        val normalizedRegnum =
            normalizeRegnum(
                vehicle.regnum
            )


        val currentPosition =
            vehicle.position
                ?: return


        val previousPosition =
            previous?.position


        val previousTime =
            previousObservationTime[
                normalizedRegnum
            ]


        /*
         * Сохраняем историю позиции.
         */
        val vehicleHistory =
            history.getOrPut(
                normalizedRegnum
            ) {
                mutableListOf()
            }


        val lastPoint =
            vehicleHistory.lastOrNull()


        if (
            lastPoint == null ||
            lastPoint.position !=
                currentPosition
        ) {

            vehicleHistory.add(

                QueueHistoryPoint(

                    timestampMillis =
                        timestampMillis,

                    position =
                        currentPosition,

                    state =
                        VehicleState.IN_QUEUE
                )
            )


            while (
                vehicleHistory.size >
                    MAX_HISTORY_POINTS
            ) {

                vehicleHistory.removeAt(
                    0
                )
            }
        }


        /*
         * Если предыдущей позиции нет,
         * это первая точка наблюдения.
         */
        if (
            previousPosition == null ||
            previousTime == null
        ) {

            previousObservationTime[
                normalizedRegnum
            ] =
                timestampMillis

            return
        }


        /*
         * Время между двумя наблюдениями.
         */
        val elapsedMillis =
            timestampMillis -
                previousTime


        if (
            elapsedMillis <= 0
        ) {

            previousObservationTime[
                normalizedRegnum
            ] =
                timestampMillis

            return
        }


        /*
         * Если автомобиль ушёл назад,
         * это не движение вперёд очереди.
         *
         * Не используем такое изменение
         * для скорости.
         */
        val movedPositions =
            previousPosition -
                currentPosition


        if (
            movedPositions <= 0
        ) {

            previousObservationTime[
                normalizedRegnum
            ] =
                timestampMillis

            return
        }


        /*
         * Защита от слишком большого
         * скачка позиции.
         *
         * Сам скачок может быть реальным,
         * поэтому мы не отбрасываем его полностью.
         *
         * Но скорость ограничивается
         * физически разумным диапазоном.
         */
        val elapsedHours =
            elapsedMillis /
                3_600_000.0


        if (
            elapsedHours <= 0.0
        ) {

            previousObservationTime[
                normalizedRegnum
            ] =
                timestampMillis

            return
        }


        val rawSpeed =
            movedPositions.toDouble() /
                elapsedHours


        if (
            rawSpeed <= 0.0 ||
            rawSpeed >
                MAX_CURRENT_SPEED
        ) {

            previousObservationTime[
                normalizedRegnum
            ] =
                timestampMillis

            return
        }


        /*
         * Записываем сегментную статистику.
         *
         * Если, например, автомобиль:
         *
         * 100 -> 95
         *
         * за 10 минут,
         *
         * то 10 минут распределяются
         * между пятью пройденными позициями.
         */
        recordSegmentMovement(
            checkpointName =
                checkpointName,

            vehicle =
                vehicle,

            fromPosition =
                previousPosition,

            toPosition =
                currentPosition,

            queueCount =
                max(
                    currentQueueCount,
                    max(
                        previousPosition,
                        currentPosition
                    )
                ),

            elapsedMinutes =
                elapsedMillis /
                    60_000.0,

            timestampMillis =
                timestampMillis
        )


        /*
         * Текущая скорость.
         *
         * Используем сглаживание:
         *
         * новая скорость = 30%
         * старой + 70% нового измерения.
         *
         * Это временная скорость,
         * а не историческая.
         */
        updateCurrentSpeedFromMeasurement(
            vehicleType =
                vehicle.vehicleType,

            measuredSpeed =
                rawSpeed,

            timestampMillis =
                timestampMillis
        )


        previousObservationTime[
            normalizedRegnum
        ] =
            timestampMillis
    }


    /**
     * Запись движения автомобиля
     * по сегментам очереди.
     */
    private fun recordSegmentMovement(
        checkpointName: String,
        vehicle: QueueVehicle,
        fromPosition: Int,
        toPosition: Int,
        queueCount: Int,
        elapsedMinutes: Double,
        timestampMillis: Long
    ) {

        if (
            elapsedMinutes <= 0.0
        ) {
            return
        }


        val movedPositions =
            fromPosition -
                toPosition


        if (
            movedPositions <= 0
        ) {
            return
        }


        val minutesPerPosition =
            elapsedMinutes /
                movedPositions.toDouble()


        /*
         * Распределяем каждую пройденную
         * позицию по соответствующему сегменту.
         */
        for (
            position
            in toPosition until fromPosition
        ) {

            val segment =
                calculateSegment(
                    position =
                        position,

                    queueCount =
                        queueCount
                )


            addSegmentSample(
                checkpointName =
                    checkpointName,

                vehicleType =
                    vehicle.vehicleType,

                segment =
                    segment,

                timestampMillis =
                    timestampMillis,

                minutesPerPosition =
                    minutesPerPosition
            )
        }
    }


    /**
     * Определение сегмента очереди.
     */
    private fun calculateSegment(
        position: Int,
        queueCount: Int
    ): Int {

        if (
            queueCount <= 0
        ) {
            return 0
        }


        val safePosition =
            position.coerceIn(
                1,
                queueCount
            )


        /*
         * Позиция 1 находится
         * ближе всего к началу очереди.
         *
         * Поэтому:
         *
         * 1 / 100 -> сегмент 0
         * 100 / 100 -> сегмент 9
         */
        val fraction =
            safePosition.toDouble() /
                queueCount.toDouble()


        return min(
            SEGMENT_COUNT - 1,
            max(
                0,
                (
                    fraction *
                        SEGMENT_COUNT
                ).toInt()
            )
        )
    }


    /**
     * Добавление одного фактического
     * наблюдения сегмента.
     *
     * Статистика хранится отдельно:
     *
     * КПП
     * + тип транспорта
     * + день недели
     * + час
     * + сегмент.
     */
    private fun addSegmentSample(
        checkpointName: String,
        vehicleType: VehicleType,
        segment: Int,
        timestampMillis: Long,
        minutesPerPosition: Double
    ) {

        if (
            minutesPerPosition <= 0.0
        ) {
            return
        }


        val calendar =
            Calendar.getInstance().apply {

                timeInMillis =
                    timestampMillis
            }


        val dayOfWeek =
            calendar.get(
                Calendar.DAY_OF_WEEK
            )


        val hour =
            calendar.get(
                Calendar.HOUR_OF_DAY
            )


        val key =
            buildSegmentKey(
                checkpointName =
                    checkpointName,

                vehicleType =
                    vehicleType,

                dayOfWeek =
                    dayOfWeek,

                hour =
                    hour,

                segment =
                    segment
            )


        val current =
            loadSegment(
                key
            )


        val updated =
            current.copy(

                totalMinutes =
                    current.totalMinutes +
                        minutesPerPosition,

                positionSamples =
                    current.positionSamples +
                        1
            )


        saveSegment(
            key,
            updated
        )
    }


    /**
     * Получение сегментной статистики
     * для текущего дня/часа.
     */
    private fun getCurrentSegmentStats(
        checkpointName: String,
        vehicleType: VehicleType,
        timestampMillis: Long
    ): Map<
        Int,
        HybridForecastEngine.SegmentStat
    > {

        val calendar =
            Calendar.getInstance().apply {

                timeInMillis =
                    timestampMillis
            }


        val dayOfWeek =
            calendar.get(
                Calendar.DAY_OF_WEEK
            )


        val hour =
            calendar.get(
                Calendar.HOUR_OF_DAY
            )


        val result =
            mutableMapOf<
                Int,
                HybridForecastEngine.SegmentStat
            >()


        for (
            segment
            in 0 until SEGMENT_COUNT
        ) {

            val key =
                buildSegmentKey(
                    checkpointName =
                        checkpointName,

                    vehicleType =
                        vehicleType,

                    dayOfWeek =
                        dayOfWeek,

                    hour =
                        hour,

                    segment =
                        segment
                )


            val stat =
                loadSegment(
                    key
                )


            val minutesPerPosition =
                stat.minutesPerPosition


            if (
                minutesPerPosition != null &&
                stat.positionSamples >=
                    MIN_SEGMENT_SAMPLES
            ) {

                result[
                    segment
                ] =
                    HybridForecastEngine
                        .SegmentStat(

                            minutesPerPosition =
                                minutesPerPosition,

                            samples =
                                stat.positionSamples
                        )
            }
        }


        return result
    }


    /**
     * Обновление текущей скорости
     * по изменению размера очереди.
     *
     * Используется как дополнительный
     * источник текущего состояния.
     */
    private fun updateCurrentSpeed(
        vehicleType: VehicleType,
        currentQueueCount: Int,
        timestampMillis: Long
    ) {

        val previousCount =
            previousQueueCounts[
                vehicleType
            ]


        previousQueueCounts[
            vehicleType
        ] =
            currentQueueCount


        /*
         * Само изменение количества
         * автомобилей в очереди нельзя
         * напрямую считать скоростью:
         *
         * машины могут одновременно
         * добавляться и вызываться.
         *
         * Поэтому здесь только обновляем
         * базовое состояние.
         */
        if (
            previousCount == null
        ) {
            return
        }
    }


    /**
     * Обновление текущей скорости
     * по фактическому движению автомобиля.
     */
    private fun updateCurrentSpeedFromMeasurement(
        vehicleType: VehicleType,
        measuredSpeed: Double,
        timestampMillis: Long
    ) {

        if (
            measuredSpeed <= 0.0 ||
            measuredSpeed >
                MAX_CURRENT_SPEED
        ) {
            return
        }


        val previous =
            currentSpeeds[
                vehicleType
            ]


        val smoothed =
            if (
                previous != null &&
                previous.samples > 0
            ) {

                /*
                 * Сглаживание.
                 *
                 * 70% новое измерение
                 * 30% предыдущая оценка.
                 */
                previous.positionsPerHour *
                    0.30 +
                    measuredSpeed *
                    0.70

            } else {

                measuredSpeed
            }


        val samples =
            (
                previous?.samples ?: 0
            ) + 1


        currentSpeeds[
            vehicleType
        ] =
            CurrentQueueSpeed(

                positionsPerHour =
                    smoothed,

                samples =
                    samples,

                timestampMillis =
                    timestampMillis
            )
    }


    /**
     * Получение текущей скорости.
     *
     * Если данные слишком старые —
     * возвращаем null.
     */
    private fun getCurrentSpeed(
        vehicleType: VehicleType,
        timestampMillis: Long
    ): Double? {

        val current =
            currentSpeeds[
                vehicleType
            ]
                ?: return null


        if (
            current.samples <
                MIN_CURRENT_SPEED_SAMPLES
        ) {
            return null
        }


        if (
            timestampMillis -
                current.timestampMillis >
                CURRENT_SPEED_MAX_AGE
        ) {
            return null
        }


        return current.positionsPerHour
            .takeIf {
                it > 0.0
            }
    }


    /**
     * Регистрация подтверждённого
     * вызова автомобиля.
     */
    private fun processCalledVehicle(
        checkpointName: String,
        vehicle: QueueVehicle,
        timestampMillis: Long
    ) {

        val normalizedRegnum =
            normalizeRegnum(
                vehicle.regnum
            )


        val eventKey =
            checkpointName +
                "|" +
                vehicle.vehicleType.name +
                "|" +
                normalizedRegnum +
                "|" +
                (
                    vehicle.registrationDate
                        ?: ""
                )


        if (
            processedCallEvents.contains(
                eventKey
            )
        ) {
            return
        }


        processedCallEvents.add(
            eventKey
        )


        /*
         * Сохраняем историю вызова.
         */
        val vehicleHistory =
            history.getOrPut(
                normalizedRegnum
            ) {
                mutableListOf()
            }


        vehicleHistory.add(

            QueueHistoryPoint(

                timestampMillis =
                    timestampMillis,

                position =
                    vehicle.position,

                state =
                    VehicleState.CALLED
            )
        )


        while (
            vehicleHistory.size >
                MAX_HISTORY_POINTS
        ) {

            vehicleHistory.removeAt(
                0
            )
        }


        /*
         * Статистика вызова.
         */
        recordCalledVehicle(
            checkpointName =
                checkpointName,

            vehicle =
                vehicle,

            timestampMillis =
                timestampMillis
        )
    }


    /**
     * Запись статистики фактического
     * вызова.
     */
    private fun recordCalledVehicle(
        checkpointName: String,
        vehicle: QueueVehicle,
        timestampMillis: Long
    ) {

        val calendar =
            Calendar.getInstance().apply {

                timeInMillis =
                    timestampMillis
            }


        val dayOfWeek =
            calendar.get(
                Calendar.DAY_OF_WEEK
            )


        val hour =
            calendar.get(
                Calendar.HOUR_OF_DAY
            )


        val key =
            buildCellKey(
                checkpointName,
                vehicle.vehicleType,
                dayOfWeek,
                hour
            )


        val current =
            loadCell(
                key
            )


        val waitingMinutes =
            calculateWaitingMinutes(
                vehicle.registrationDate,
                timestampMillis
            )


        val updated =
            current.copy(

                calledCount =
                    current.calledCount + 1,

                waitingSamples =
                    if (
                        waitingMinutes != null
                    ) {

                        current.waitingSamples + 1

                    } else {

                        current.waitingSamples
                    },

                totalWaitingMinutes =
                    current.totalWaitingMinutes +
                        (
                            waitingMinutes
                                ?: 0.0
                        )
            )


        saveCell(
            key,
            updated
        )
    }


    /**
     * Расчёт фактического времени
     * от регистрации до обнаружения
     * status=3.
     */
    private fun calculateWaitingMinutes(
        registrationDate: String?,
        callTimestampMillis: Long
    ): Double? {

        if (
            registrationDate.isNullOrBlank()
        ) {
            return null
        }


        return try {

            val formatter =
                SimpleDateFormat(
                    "HH:mm:ss dd.MM.yyyy",
                    Locale.getDefault()
                )


            val registration =
                formatter.parse(
                    registrationDate
                )
                    ?: return null


            val difference =
                callTimestampMillis -
                    registration.time


            if (
                difference <= 0
            ) {

                null

            } else {

                difference /
                    60_000.0
            }

        } catch (
            _: Exception
        ) {

            null
        }
    }


    /**
     * Отметка наблюдаемого часа.
     */
    private fun markHourObserved(
        checkpointName: String,
        vehicleType: VehicleType,
        dayOfWeek: Int,
        hour: Int
    ) {

        val key =
            buildCellKey(
                checkpointName,
                vehicleType,
                dayOfWeek,
                hour
            )


        val lastObservedKey =
            "${key}_last_observed"


        val now =
            System.currentTimeMillis()


        val previousTime =
            preferences.getLong(
                lastObservedKey,
                0L
            )


        if (
            now -
                previousTime <
                OBSERVATION_INTERVAL
        ) {
            return
        }


        preferences.edit()
            .putLong(
                lastObservedKey,
                now
            )
            .apply()


        val current =
            loadCell(
                key
            )


        saveCell(

            key,

            current.copy(

                observedCount =
                    current.observedCount + 1
            )
        )
    }


    /**
     * Получение статистики конкретной
     * ячейки.
     */
    fun getStatistics(
        checkpointName: String,
        vehicleType: VehicleType,
        dayOfWeek: Int,
        hour: Int
    ): QueueStatisticsCell {

        return loadCell(

            buildCellKey(
                checkpointName,
                vehicleType,
                dayOfWeek,
                hour
            )
        )
    }


    /**
     * Средняя историческая скорость
     * по всему накопленному архиву.
     */
    private fun getGlobalSpeed(
        checkpointName: String,
        vehicleType: VehicleType
    ): Double? {

        var totalCalls =
            0


        var totalObservedHours =
            0


        for (
            day
            in Calendar.SUNDAY..
                Calendar.SATURDAY
        ) {

            for (
                hour
                in 0..23
            ) {

                val cell =
                    getStatistics(
                        checkpointName,
                        vehicleType,
                        day,
                        hour
                    )


                totalCalls +=
                    cell.calledCount


                totalObservedHours +=
                    cell.observedCount
            }
        }


        if (
            totalObservedHours <= 0
        ) {
            return null
        }


        val speed =
            totalCalls.toDouble() /
                totalObservedHours


        return speed
            .takeIf {
                it > 0.0
            }
    }


    /**
     * Историческая скорость
     * для текущего дня и часа.
     *
     * Если собственной статистики
     * ещё нет — используется глобальная.
     */
    private fun getHistoricalSpeed(
        checkpointName: String,
        vehicleType: VehicleType,
        dayOfWeek: Int,
        hour: Int
    ): Double? {

        val cell =
            getStatistics(
                checkpointName,
                vehicleType,
                dayOfWeek,
                hour
            )


        if (
            cell.observedCount > 0 &&
            cell.callsPerObservedHour > 0.0
        ) {

            return cell.callsPerObservedHour
        }


        return getGlobalSpeed(
            checkpointName,
            vehicleType
        )
    }


    /**
     * Получение исторической скорости
     * для текущего момента.
     */
    private fun getHistoricalSpeedNow(
        checkpointName: String,
        vehicleType: VehicleType,
        timestampMillis: Long
    ): Double? {

        val calendar =
            Calendar.getInstance().apply {

                timeInMillis =
                    timestampMillis
            }


        return getHistoricalSpeed(

            checkpointName =
                checkpointName,

            vehicleType =
                vehicleType,

            dayOfWeek =
                calendar.get(
                    Calendar.DAY_OF_WEEK
                ),

            hour =
                calendar.get(
                    Calendar.HOUR_OF_DAY
                )
        )
    }


    /**
     * Расчёт гибридного прогноза.
     */
    fun calculateForecast(
        regnum: String,
        checkpointName: String
    ): QueueForecast? {

        val vehicle =
            previousVehicles[
                normalizeRegnum(
                    regnum
                )
            ]
                ?: return null


        if (
            determineState(
                vehicle
            ) !=
                VehicleState.IN_QUEUE
        ) {
            return null
        }


        val currentPosition =
            vehicle.position
                ?: return null


        val positionsAhead =
            max(
                0,
                currentPosition - 1
            )


        if (
            positionsAhead == 0
        ) {

            return QueueForecast(

                currentPosition =
                    currentPosition,

                positionsAhead =
                    0,

                speed =
                    null,

                estimatedMinutes =
                    0.0
            )
        }


        val vehicleType =
            vehicle.vehicleType


        val timestampMillis =
            System.currentTimeMillis()


        /*
         * Количество автомобилей
         * именно этого типа.
         *
         * Если текущего значения нет,
         * используем минимум текущей позиции.
         */
        val queueCount =
            max(
                currentQueueCountForType(
                    vehicleType
                ),
                currentPosition
            )


        /*
         * Историческая скорость.
         */
        val historicalSpeed =
            getHistoricalSpeedNow(

                checkpointName =
                    checkpointName,

                vehicleType =
                    vehicleType,

                timestampMillis =
                    timestampMillis
            )


        /*
         * Фактическая текущая скорость.
         */
        val currentSpeed =
            getCurrentSpeed(

                vehicleType =
                    vehicleType,

                timestampMillis =
                    timestampMillis
            )


        /*
         * Сегментная статистика
         * текущего дня/часа.
         */
        val segmentStats =
            getCurrentSegmentStats(

                checkpointName =
                    checkpointName,

                vehicleType =
                    vehicleType,

                timestampMillis =
                    timestampMillis
            )


        /*
         * Передаём всё в гибридный двигатель.
         */
        val result =
            HybridForecastEngine.estimate(

                currentPosition =
                    currentPosition,

                queueCount =
                    queueCount,

                historicalSpeed =
                    historicalSpeed,

                currentSpeed =
                    currentSpeed,

                segmentStats =
                    segmentStats
            )


        if (
            result.estimatedMinutes <= 0.0
        ) {

            return null
        }


        val effectiveSpeed =
            result.effectiveSpeed


        val speed =
            effectiveSpeed?.let {

                QueueSpeed(

                    positionsPerHour =
                        it,

                    minutesPerPosition =
                        if (
                            it > 0.0
                        ) {

                            60.0 /
                                it

                        } else {

                            0.0
                        }
                )
            }


        return QueueForecast(

            currentPosition =
                currentPosition,

            positionsAhead =
                positionsAhead,

            speed =
                speed,

            estimatedMinutes =
                result.estimatedMinutes
        )
    }


    /**
     * Текущее количество автомобилей
     * конкретного типа.
     */
    private fun currentQueueCountForType(
        vehicleType: VehicleType
    ): Int {

        var count =
            0


        for (
            vehicle
            in previousVehicles.values
        ) {

            if (
                vehicle.vehicleType ==
                    vehicleType &&
                determineState(
                    vehicle
                ) ==
                    VehicleState.IN_QUEUE
            ) {

                count++
            }
        }


        return count
    }


    /**
     * Формирование ключа статистики
     * часового интервала.
     */
    private fun buildCellKey(
        checkpointName: String,
        vehicleType: VehicleType,
        dayOfWeek: Int,
        hour: Int
    ): String {

        return "cell|" +
            checkpointName +
            "|" +
            vehicleType.name +
            "|" +
            dayOfWeek +
            "|" +
            hour
    }


    /**
     * Формирование ключа сегментной
     * статистики.
     */
    private fun buildSegmentKey(
        checkpointName: String,
        vehicleType: VehicleType,
        dayOfWeek: Int,
        hour: Int,
        segment: Int
    ): String {

        return "segment|" +
            checkpointName +
            "|" +
            vehicleType.name +
            "|" +
            dayOfWeek +
            "|" +
            hour +
            "|" +
            segment
    }


    /**
     * Загрузка часовой статистики.
     */
    private fun loadCell(
        key: String
    ): QueueStatisticsCell {

        return QueueStatisticsCell(

            dayOfWeek =
                preferences.getInt(
                    "${key}_day",
                    0
                ),

            hour =
                preferences.getInt(
                    "${key}_hour",
                    0
                ),

            calledCount =
                preferences.getInt(
                    "${key}_called",
                    0
                ),

            observedCount =
                preferences.getInt(
                    "${key}_observed",
                    0
                ),

            totalWaitingMinutes =
                preferences.getFloat(
                    "${key}_waiting",
                    0f
                ).toDouble(),

            waitingSamples =
                preferences.getInt(
                    "${key}_samples",
                    0
                )
        )
    }


    /**
     * Сохранение часовой статистики.
     */
    private fun saveCell(
        key: String,
        cell: QueueStatisticsCell
    ) {

        preferences.edit()

            .putInt(
                "${key}_day",
                cell.dayOfWeek
            )

            .putInt(
                "${key}_hour",
                cell.hour
            )

            .putInt(
                "${key}_called",
                cell.calledCount
            )

            .putInt(
                "${key}_observed",
                cell.observedCount
            )

            .putFloat(
                "${key}_waiting",
                cell.totalWaitingMinutes
                    .toFloat()
            )

            .putInt(
                "${key}_samples",
                cell.waitingSamples
            )

            .apply()
    }


    /**
     * Загрузка статистики сегмента.
     */
    private fun loadSegment(
        key: String
    ): QueueSegmentStatistics {

        return QueueSegmentStatistics(

            segment =
                preferences.getInt(
                    "${key}_segment",
                    0
                ),

            totalMinutes =
                preferences.getFloat(
                    "${key}_minutes",
                    0f
                ).toDouble(),

            positionSamples =
                preferences.getInt(
                    "${key}_samples",
                    0
                )
        )
    }


    /**
     * Сохранение статистики сегмента.
     */
    private fun saveSegment(
        key: String,
        statistics:
            QueueSegmentStatistics
    ) {

        preferences.edit()

            .putInt(
                "${key}_segment",
                statistics.segment
            )

            .putFloat(
                "${key}_minutes",
                statistics.totalMinutes
                    .toFloat()
            )

            .putInt(
                "${key}_samples",
                statistics.positionSamples
            )

            .apply()
    }


    /**
     * Полный сброс только временного
     * состояния анализатора.
     *
     * Накопленную статистику НЕ удаляем.
     */
    fun reset() {

        history.clear()

        previousVehicles.clear()

        previousQueueCounts.clear()

        previousObservationTime.clear()

        currentSpeeds.clear()

        processedCallEvents.clear()
    }
}
