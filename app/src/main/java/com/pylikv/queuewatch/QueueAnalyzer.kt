package com.pylikv.queuewatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min


/**
 * Анализатор электронной очереди QueueWatch.
 *
 * Скорость очереди НЕ рассчитывается по скачку order_id
 * конкретного автомобиля.
 *
 * Скорость формируется из подтверждённых вызовов
 * автомобилей и сохраняется в статистике:
 *
 * КПП + тип транспорта + день недели + час.
 */
class QueueAnalyzer(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            "queuewatch_statistics",
            Context.MODE_PRIVATE
        )


    /*
     * История конкретных автомобилей.
     */
    private val history =
        mutableMapOf<String, MutableList<QueueHistoryPoint>>()


    /*
     * Последняя серверная запись автомобиля.
     */
    private val previousVehicles =
        mutableMapOf<String, QueueVehicle>()


    /*
     * Уже обработанные события вызова.
     *
     * Нужно для того, чтобы одна машина,
     * присутствующая со status=3 несколько раз,
     * не была посчитана несколько раз.
     */
    private val processedCallEvents =
        mutableSetOf<String>()


    /*
     * Нормализация номера.
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
     * Разбор полного JSON.
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


    private fun parseQueueArray(
        root: JSONObject,
        arrayName: String,
        vehicleType: VehicleType,
        result: MutableList<QueueVehicle>
    ) {

        val queue =
            root.optJSONArray(arrayName)
                ?: JSONArray()


        for (i in 0 until queue.length()) {

            val item =
                queue.optJSONObject(i)
                    ?: continue


            val regnum =
                item.optString(
                    "regnum",
                    ""
                ).trim()


            if (regnum.isEmpty()) {
                continue
            }


            result += QueueVehicle(

                regnum = regnum,

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
            val value = item.opt(key)
        ) {

            is Number ->
                value.toInt()

            is String ->
                value.trim().toIntOrNull()

            else ->
                null
        }
    }


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
            normalizeRegnum(regnum)


        if (target.isEmpty()) {
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
     * Определение состояния.
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
     * Именно здесь формируется статистика
     * фактически вызванных автомобилей.
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
         * Количество автомобилей каждого типа
         * в текущем снимке.
         *
         * Используется для фиксации того,
         * что час действительно наблюдался.
         */
        val currentCounts =
            mutableMapOf<VehicleType, Int>()


        for (vehicle in vehicles) {

            currentCounts[
                vehicle.vehicleType
            ] =
                (
                    currentCounts[
                        vehicle.vehicleType
                    ] ?: 0
                ) + 1


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


            /*
             * История позиции сохраняется
             * исключительно как история.
             *
             * Она БОЛЬШЕ НЕ используется
             * для вычисления скорости.
             */
            if (
                state ==
                    VehicleState.IN_QUEUE &&
                vehicle.position != null
            ) {

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
                        vehicle.position
                ) {

                    vehicleHistory.add(

                        QueueHistoryPoint(

                            timestampMillis =
                                timestampMillis,

                            position =
                                vehicle.position,

                            state =
                                VehicleState.IN_QUEUE
                        )
                    )
                }
            }


            /*
             * Подтверждённый вызов.
             */
            if (
                state ==
                    VehicleState.CALLED
            ) {

                val eventKey =
                    checkpointName +
                        "|" +
                        vehicle.vehicleType.name +
                        "|" +
                        normalizedRegnum


                if (
                    !processedCallEvents.contains(
                        eventKey
                    )
                ) {

                    processedCallEvents.add(
                        eventKey
                    )


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


                    /*
                     * Регистрируем фактический
                     * вызов в статистике.
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
            }


            previousVehicles[
                normalizedRegnum
            ] = vehicle
        }


        /*
         * Фиксируем наблюдение текущего часа.
         *
         * Наличие snapshot означает,
         * что этот час действительно наблюдался.
         */
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis =
                    timestampMillis
            }


        val dayOfWeek =
            calendar.get(Calendar.DAY_OF_WEEK)


        val hour =
            calendar.get(Calendar.HOUR_OF_DAY)


        for (
            vehicleType in
            VehicleType.values()
        ) {

            val count =
                currentCounts[
                    vehicleType
                ] ?: 0


            markHourObserved(
                checkpointName =
                    checkpointName,

                vehicleType =
                    vehicleType,

                dayOfWeek =
                    dayOfWeek,

                hour =
                    hour,

                queueCount =
                    count
            )
        }


        return vehicles
    }


    /**
     * Запись фактически вызванного автомобиля.
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
            calendar.get(Calendar.DAY_OF_WEEK)


        val hour =
            calendar.get(Calendar.HOUR_OF_DAY)


        val key =
            buildCellKey(
                checkpointName,
                vehicle.vehicleType,
                dayOfWeek,
                hour
            )


        val current =
            loadCell(key)


        /*
         * Пытаемся определить фактическое
         * время ожидания.
         *
         * ВАЖНО:
         * changed_date здесь НЕ считаем
         * временем вызова автоматически.
         *
         * Время вызова берём из момента,
         * когда приложение впервые увидело
         * подтверждённый status=3.
         */
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
     * Регистрация -> фактический момент,
     * когда status=3 впервые был замечен.
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
            e: Exception
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
        hour: Int,
        queueCount: Int
    ) {

        val key =
            buildCellKey(
                checkpointName,
                vehicleType,
                dayOfWeek,
                hour
            )


        val current =
            loadCell(key)


        /*
         * Один вызов processSnapshot()
         * не должен бесконечно увеличивать
         * observedCount при каждом обновлении.
         *
         * Поэтому observedCount здесь означает
         * количество отдельных запусков наблюдения
         * в этом часу.
         *
         * Для 20-секундных запросов используем
         * максимум одно наблюдение примерно
         * раз в 5 минут.
         */
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
                5 * 60 * 1000L
        ) {
            return
        }


        preferences.edit()
            .putLong(
                lastObservedKey,
                now
            )
            .apply()


        saveCell(

            key,

            current.copy(

                observedCount =
                    current.observedCount + 1
            )
        )
    }


    /**
     * Получение статистики конкретной ячейки.
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
     * Средняя фактическая скорость
     * по всей накопленной статистике
     * данного КПП и типа транспорта.
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
            day in
            Calendar.SUNDAY..Calendar.SATURDAY
        ) {

            for (
                hour in
                0..23
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


        return if (
            speed > 0
        ) {
            speed
        } else {
            /*
             * Если наблюдаемые часы были,
             * но машин не прошло,
             * глобальная скорость действительно 0.
             */
            0.0
        }
    }


    /**
     * Скорость для конкретного часа.
     *
     * Если этот час уже наблюдался,
     * его собственная статистика имеет приоритет.
     *
     * Если данных ещё нет —
     * используем накопленную общую скорость.
     */
    private fun getSpeedForHour(
        checkpointName: String,
        vehicleType: VehicleType,
        dayOfWeek: Int,
        hour: Int
    ): HourlySpeed {

        val cell =
            getStatistics(
                checkpointName,
                vehicleType,
                dayOfWeek,
                hour
            )


        if (
            cell.observedCount > 0
        ) {

            return HourlySpeed(

                dayOfWeek =
                    dayOfWeek,

                hour =
                    hour,

                positionsPerHour =
                    cell.callsPerObservedHour,

                observed =
                    true
            )
        }


        val global =
            getGlobalSpeed(
                checkpointName,
                vehicleType
            )


        return HourlySpeed(

            dayOfWeek =
                dayOfWeek,

            hour =
                hour,

            positionsPerHour =
                global ?: 0.0,

            observed =
                false
        )
    }


    /**
     * Рассчитывает прогноз,
     * проходя по часам вперёд.
     *
     * Поэтому час со скоростью 0
     * автоматически добавляет время ожидания.
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
            determineState(vehicle) !=
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


        var remaining =
            positionsAhead.toDouble()


        var totalMinutes =
            0.0


        var cursor =
            Calendar.getInstance()


        /*
         * Максимальная глубина расчёта —
         * 14 суток, чтобы исключить бесконечный цикл.
         */
        var safety =
            0


        var firstSpeed: Double? =
            null


        while (
            remaining > 0 &&
            safety < 336
        ) {

            safety++


            val dayOfWeek =
                cursor.get(
                    Calendar.DAY_OF_WEEK
                )


            val hour =
                cursor.get(
                    Calendar.HOUR_OF_DAY
                )


            val minute =
                cursor.get(
                    Calendar.MINUTE
                )


            val second =
                cursor.get(
                    Calendar.SECOND
                )


            val secondsPassed =
                minute * 60 +
                    second


            val minutesLeftInHour =
                (
                    3600 -
                        secondsPassed
                ) / 60.0


            val hourly =
                getSpeedForHour(

                    checkpointName =
                        checkpointName,

                    vehicleType =
                        vehicleType,

                    dayOfWeek =
                        dayOfWeek,

                    hour =
                        hour
                )


            val speed =
                hourly.positionsPerHour


            if (
                firstSpeed == null
            ) {
                firstSpeed = speed
            }


            /*
             * Пересменка / остановка.
             *
             * В этом часу машины не проходят.
             */
            if (
                speed <= 0.0
            ) {

                totalMinutes +=
                    minutesLeftInHour

                cursor.add(
                    Calendar.HOUR_OF_DAY,
                    1
                )

                cursor.set(
                    Calendar.MINUTE,
                    0
                )

                cursor.set(
                    Calendar.SECOND,
                    0
                )

                continue
            }


            val capacityThisHour =
                speed *
                    (
                        minutesLeftInHour /
                            60.0
                    )


            if (
                remaining <=
                    capacityThisHour
            ) {

                val neededMinutes =
                    remaining /
                        speed *
                        60.0


                totalMinutes +=
                    neededMinutes


                remaining = 0.0

            } else {

                remaining -=
                    capacityThisHour


                totalMinutes +=
                    minutesLeftInHour


                cursor.add(
                    Calendar.HOUR_OF_DAY,
                    1
                )

                cursor.set(
                    Calendar.MINUTE,
                    0
                )

                cursor.set(
                    Calendar.SECOND,
                    0
                )
            }
        }


        if (
            remaining > 0
        ) {
            return null
        }


        val effectiveSpeed =
            if (
                firstSpeed != null &&
                firstSpeed > 0
            ) {
                firstSpeed
            } else {
                null
            }


        val speed =
            effectiveSpeed?.let {

                QueueSpeed(

                    positionsPerHour =
                        it,

                    minutesPerPosition =
                        60.0 / it
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
                totalMinutes
        )
    }


    /**
     * Совместимость со старым кодом.
     *
     * Старый расчёт скорости по позициям
     * намеренно отключён.
     */
    fun calculateVehicleSpeed(
        regnum: String
    ): QueueSpeed? {

        return null
    }


    /**
     * История автомобиля.
     */
    fun getHistory(
        regnum: String
    ): List<QueueHistoryPoint> {

        return history[
            normalizeRegnum(
                regnum
            )
        ]
            ?.toList()
            ?: emptyList()
    }


    fun getPreviousVehicle(
        regnum: String
    ): QueueVehicle? {

        return previousVehicles[
            normalizeRegnum(
                regnum
            )
        ]
    }


    fun getTrackedVehicleCount(): Int {
        return history.size
    }


    /**
     * Очищает только текущий сеанс.
     *
     * Недельная статистика НЕ удаляется.
     */
    fun reset() {

        history.clear()

        previousVehicles.clear()

        processedCallEvents.clear()
    }


    /*
     * ========================================================
     * PERSISTENT STATISTICS
     * ========================================================
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
                    "${key}_calls",
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
                    "${key}_waiting_samples",
                    0
                )
        )
    }


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
                "${key}_calls",
                cell.calledCount
            )

            .putInt(
                "${key}_observed",
                cell.observedCount
            )

            .putFloat(
                "${key}_waiting",
                cell.totalWaitingMinutes.toFloat()
            )

            .putInt(
                "${key}_waiting_samples",
                cell.waitingSamples
            )

            .apply()
    }
}
