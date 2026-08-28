package com.pylikv.queuewatch

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Анализатор электронной очереди QueueWatch.
 *
 * Анализирует все типы транспорта,
 * которые возвращает API:
 *
 * - легковые автомобили
 * - грузовые автомобили
 * - автобусы
 * - мотоциклы
 *
 * Главный принцип:
 *
 * ВСЕ решения принимаются только на основании
 * фактических данных сервера.
 *
 * Временное исчезновение автомобиля из ответа
 * не считается автоматически вызовом.
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
     * считаются одним автомобилем.
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
     * Анализируются ВСЕ массивы живой очереди:
     *
     * carLiveQueue
     * truckLiveQueue
     * busLiveQueue
     * motorcycleLiveQueue
     *
     * Каждый элемент получает соответствующий
     * VehicleType.
     */
    fun parseQueue(
        json: String
    ): List<QueueVehicle> {

        val root =
            JSONObject(json)

        val result =
            mutableListOf<QueueVehicle>()


        /*
         * Легковые автомобили.
         */
        parseQueueArray(
            root = root,
            arrayName = "carLiveQueue",
            vehicleType = VehicleType.CAR,
            result = result
        )


        /*
         * Грузовые автомобили.
         */
        parseQueueArray(
            root = root,
            arrayName = "truckLiveQueue",
            vehicleType = VehicleType.TRUCK,
            result = result
        )


        /*
         * Автобусы.
         */
        parseQueueArray(
            root = root,
            arrayName = "busLiveQueue",
            vehicleType = VehicleType.BUS,
            result = result
        )


        /*
         * Мотоциклы.
         */
        parseQueueArray(
            root = root,
            arrayName = "motorcycleLiveQueue",
            vehicleType = VehicleType.MOTORCYCLE,
            result = result
        )


        return result
    }


    /**
     * Разбор одного массива очереди.
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
            i in 0 until queue.length()
        ) {

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
                    changedDate,

                vehicleType =
                    vehicleType
            )
        }
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
     * Поиск конкретного автомобиля
     * во всех типах очередей.
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
     * 3. остальное
     *      состояние неизвестно.
     *
     * Таким образом status=3 имеет приоритет.
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
     * Анализируется вся очередь независимо
     * от выбранного пользователем транспорта.
     *
     * Это позволяет собирать статистику
     * движения очереди по каждому типу транспорта.
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
             * Предыдущая запись автомобиля.
             */
            val previous =
                previousVehicles[
                    normalizedRegnum
                ]


            /*
             * Если автомобиль находится
             * в живой очереди и сервер сообщает
             * реальную позицию — записываем её.
             *
             * Временное отсутствие автомобиля
             * из следующего JSON здесь ничего
             * не меняет.
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


                val lastPoint =
                    vehicleHistory.lastOrNull()


                /*
                 * Новую точку записываем только если:
                 *
                 * - это первое наблюдение;
                 * - позиция действительно изменилась.
                 *
                 * Одинаковые позиции не засоряют историю.
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
             * Явный status=3.
             *
             * Это подтверждённый вызов.
             *
             * Сохраняем событие только один раз.
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
         * ВАЖНО:
         *
         * Мы НЕ удаляем автомобили,
         * которые временно исчезли из ответа.
         *
         * Поэтому исчезновение из JSON само по себе
         * НЕ превращается в CALLED.
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
     * Количество автомобилей,
     * для которых накоплена история.
     */
    fun getTrackedVehicleCount(): Int {

        return history.size
    }


    /**
     * Расчёт скорости движения очереди
     * для конкретного автомобиля.
     *
     * Используются только реальные
     * изменения позиции.
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
     * Если истории ещё недостаточно,
     * возвращается текущая подтверждённая
     * позиция без расчётного времени.
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
         * Истории пока недостаточно.
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
     * Очистка текущей истории.
     *
     * Используется при начале нового
     * независимого сеанса мониторинга.
     */
    fun reset() {

        history.clear()

        previousVehicles.clear()
    }
}
