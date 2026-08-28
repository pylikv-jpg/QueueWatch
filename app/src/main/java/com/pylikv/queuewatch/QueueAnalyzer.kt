package com.pylikv.queuewatch

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Анализатор электронной очереди QueueWatch.
 *
 * Главный принцип:
 *
 * ВСЕ решения принимаются только на основании
 * фактических данных сервера.
 *
 * Анализируется вся truckLiveQueue, а не только
 * выбранный пользователем автомобиль.
 */
class QueueAnalyzer {

    /*
     * История движения каждого автомобиля.
     *
     * Ключ:
     * нормализованный регистрационный номер.
     */
    private val history =
        mutableMapOf<String, MutableList<QueueHistoryPoint>>()


    /*
     * Последняя запись каждого автомобиля,
     * реально полученная от сервера.
     */
    private val previousVehicles =
        mutableMapOf<String, QueueVehicle>()


    /**
     * Нормализация регистрационного номера.
     *
     * 773AGM03
     * 773agm03
     * 773 AGM03
     * 773-AGM03
     *
     * будут считаться одним автомобилем.
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
     * Разбор полного JSON ответа сервера.
     *
     * Читаем именно truckLiveQueue.
     *
     * Никаких фильтров по order_id,
     * status или type_queue здесь нет.
     */
    fun parseQueue(
        json: String
    ): List<QueueVehicle> {

        val root =
            JSONObject(json)

        val queue =
            root.optJSONArray(
                "truckLiveQueue"
            ) ?: JSONArray()

        val result =
            mutableListOf<QueueVehicle>()


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


            val status =
                readNullableInt(
                    item,
                    "status"
                )


            val orderId =
                readNullableInt(
                    item,
                    "order_id"
                )


            val typeQueue =
                readNullableInt(
                    item,
                    "type_queue"
                )


            val registrationDate =
                readNullableString(
                    item,
                    "registration_date"
                )


            val changedDate =
                readNullableString(
                    item,
                    "changed_date"
                )


            result += QueueVehicle(

                regnum =
                    regnum,

                status =
                    status,

                orderId =
                    orderId,

                typeQueue =
                    typeQueue,

                registrationDate =
                    registrationDate,

                changedDate =
                    changedDate
            )
        }


        return result
    }


    /**
     * Безопасное чтение nullable Int.
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


    /**
     * Безопасное чтение nullable String.
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
     * Поиск конкретного автомобиля.
     *
     * Автомобиль ищется непосредственно
     * в текущем truckLiveQueue.
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
     * Определение состояния автомобиля.
     *
     * ПОРЯДОК ПРОВЕРКИ ПРИНЦИПИАЛЬНО ВАЖЕН:
     *
     * 1. status == 3
     *      подтверждённый вызов.
     *
     * 2. order_id != null
     *      автомобиль находится в живой очереди.
     *
     * 3. всё остальное
     *      состояние неизвестно.
     *
     * Поэтому явный status=3 имеет приоритет
     * даже если сервер одновременно передал order_id.
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
     * Обработка нового снимка очереди.
     *
     * Здесь анализируется ВСЯ очередь.
     *
     * Это позволяет в дальнейшем получать
     * статистику движения КПП независимо
     * от того, какой автомобиль выбрал пользователь.
     */
    fun processSnapshot(
        json: String,
        timestampMillis: Long =
            System.currentTimeMillis()
    ): List<QueueVehicle> {

        val vehicles =
            parseQueue(json)


        for (
            vehicle in vehicles
        ) {

            val normalizedRegnum =
                normalizeRegnum(
                    vehicle.regnum
                )


            val state =
                determineState(
                    vehicle
                )


            /*
             * Предыдущая запись этого автомобиля.
             */
            val previous =
                previousVehicles[
                    normalizedRegnum
                ]


            /*
             * Записываем историю только тогда,
             * когда сервер реально сообщает позицию.
             */
            if (
                state ==
                    VehicleState.IN_QUEUE &&
                vehicle.position != null
            ) {

                val currentPosition =
                    vehicle.position


                val vehicleHistory =
                    history.getOrPut(
                        normalizedRegnum
                    ) {
                        mutableListOf()
                    }


                /*
                 * Последняя записанная точка.
                 */
                val lastPoint =
                    vehicleHistory.lastOrNull()


                /*
                 * Записываем новую историческую точку:
                 *
                 * - если это первая позиция;
                 * - если позиция изменилась;
                 * - если предыдущего наблюдения нет.
                 *
                 * Одинаковые позиции каждую минуту
                 * НЕ засоряют историю.
                 */
                val shouldRecord =
                    lastPoint == null ||
                        lastPoint.position !=
                            currentPosition


                if (
                    shouldRecord
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
                }
            }


            /*
             * Если сервер явно сообщил вызов,
             * тоже сохраняем событие в историю.
             *
             * Позиция при этом может быть null.
             */
            if (
                state ==
                    VehicleState.CALLED
            ) {

                val vehicleHistory =
                    history.getOrPut(
                        normalizedRegnum
                    ) {
                        mutableListOf()
                    }


                val lastPoint =
                    vehicleHistory.lastOrNull()


                /*
                 * Не создаём бесконечные одинаковые
                 * события CALLED при каждом запросе.
                 */
                val alreadyCalled =
                    lastPoint?.state ==
                        VehicleState.CALLED


                if (
                    !alreadyCalled
                ) {

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
                }
            }


            /*
             * Сохраняем последнюю серверную запись.
             */
            previousVehicles[
                normalizedRegnum
            ] = vehicle
        }


        /*
         * Возвращаем всю очередь.
         */
        return vehicles
    }


    /**
     * Последняя серверная запись автомобиля.
     */
    fun getPreviousVehicle(
        regnum: String
    ): QueueVehicle? {

        return previousVehicles[
            normalizeRegnum(
                regnum
            )
        ]
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


    /**
     * Сколько автомобилей уже имеет историю.
     *
     * В будущем это пригодится для статистики КПП.
     */
    fun getTrackedVehicleCount(): Int {

        return history.size
    }


    /**
     * Расчёт скорости движения очереди
     * для конкретного автомобиля.
     *
     * Используются реальные изменения позиции.
     */
    fun calculateVehicleSpeed(
        regnum: String
    ): QueueSpeed? {

        val points =
            history[
                normalizeRegnum(
                    regnum
                )
            ]
                ?.filter {

                    it.position != null &&
                        it.state ==
                        VehicleState.IN_QUEUE
                }
                ?: return null


        if (
            points.size < 2
        ) {
            return null
        }


        val first =
            points.first()


        val last =
            points.last()


        val firstPosition =
            first.position
                ?: return null


        val lastPosition =
            last.position
                ?: return null


        val elapsedMillis =
            last.timestampMillis -
                first.timestampMillis


        if (
            elapsedMillis <= 0
        ) {
            return null
        }


        /*
         * Положительное значение означает,
         * что автомобиль продвинулся вперёд.
         */
        val positionsPassed =
            firstPosition -
                lastPosition


        if (
            positionsPassed <= 0
        ) {
            return null
        }


        val hours =
            elapsedMillis /
                3_600_000.0


        if (
            hours <= 0
        ) {
            return null
        }


        val positionsPerHour =
            positionsPassed /
                hours


        if (
            positionsPerHour <= 0
        ) {
            return null
        }


        val minutesPerPosition =
            60.0 /
                positionsPerHour


        return QueueSpeed(

            positionsPerHour =
                positionsPerHour,

            minutesPerPosition =
                minutesPerPosition
        )
    }


    /**
     * Расчёт прогноза для автомобиля.
     *
     * Если истории пока недостаточно,
     * возвращается текущая позиция,
     * но скорость и время остаются null.
     */
    fun calculateForecast(
        regnum: String
    ): QueueForecast? {

        val points =
            history[
                normalizeRegnum(
                    regnum
                )
            ]
                ?.filter {

                    it.position != null &&
                        it.state ==
                        VehicleState.IN_QUEUE
                }
                ?: return null


        if (
            points.isEmpty()
        ) {
            return null
        }


        val currentPosition =
            points.last().position
                ?: return null


        val positionsAhead =
            max(
                0,
                currentPosition - 1
            )


        val speed =
            calculateVehicleSpeed(
                regnum
            )


        /*
         * Истории ещё мало.
         */
        if (
            speed == null
        ) {

            return QueueForecast(

                currentPosition =
                    currentPosition,

                positionsAhead =
                    positionsAhead,

                speed =
                    null,

                estimatedMinutes =
                    null
            )
        }


        val estimatedMinutes =
            positionsAhead *
                speed.minutesPerPosition


        return QueueForecast(

            currentPosition =
                currentPosition,

            positionsAhead =
                positionsAhead,

            speed =
                speed,

            estimatedMinutes =
                estimatedMinutes
        )
    }


    /**
     * Полная очистка текущей истории.
     *
     * Используется при начале нового
     * независимого сеанса мониторинга.
     */
    fun reset() {

        history.clear()

        previousVehicles.clear()
    }
}
