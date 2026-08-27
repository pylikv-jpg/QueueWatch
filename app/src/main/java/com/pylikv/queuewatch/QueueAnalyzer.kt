package com.pylikv.queuewatch

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Анализатор электронной очереди QueueWatch.
 *
 * ВАЖНО:
 *
 * Анализатор НЕ придумывает состояние автомобиля.
 * Он работает только с данными, полученными от сервера.
 *
 * Основные правила:
 *
 * 1. Автомобиль ищется непосредственно в truckLiveQueue.
 *
 * 2. order_id != null:
 *      сервер сообщает текущую позицию автомобиля
 *      в живой очереди.
 *
 * 3. status == 3:
 *      сервер сообщает подтверждённый вызов.
 *
 * 4. status == 2 / type_queue == 1:
 *      сами по себе не считаются вызовом.
 *
 * 5. Если автомобиль временно отсутствует
 *    в очередном JSON:
 *      состояние НЕ меняем автоматически.
 *
 * 6. Позиция всегда берётся из order_id,
 *    если сервер его передал.
 */
class QueueAnalyzer {

    private val history =
        mutableMapOf<String, MutableList<QueueHistoryPoint>>()

    private val previousVehicles =
        mutableMapOf<String, QueueVehicle>()


    /**
     * Нормализация регистрационного номера.
     *
     * Примеры:
     *
     * NGK209
     * ngk209
     * NGK 209
     * NGK-209
     *
     * будут сопоставляться одинаково.
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
     * Мы НЕ фильтруем записи по order_id.
     *
     * Это принципиально важно:
     * сначала получаем все реальные записи
     * из truckLiveQueue,
     * а уже потом определяем состояние
     * конкретного автомобиля.
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
     * Чтение nullable Integer.
     *
     * Сервер может прислать:
     *
     * null
     * ""
     * число
     * строку с числом
     *
     * Поэтому не используем слепой optInt().
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

        val value =
            item.opt(key)

        return when (value) {

            is Number ->
                value.toInt()

            is String ->
                value.trim().toIntOrNull()

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
     * Поиск конкретного автомобиля
     * непосредственно в текущем серверном JSON.
     *
     * ВАЖНО:
     *
     * Здесь НЕТ проверки order_id.
     *
     * Поэтому автомобиль будет найден
     * независимо от того,
     * какой status/order_id/type_queue
     * сервер ему прислал.
     */
    fun findVehicle(
        json: String,
        regnum: String
    ): QueueVehicle? {

        val normalizedTarget =
            normalizeRegnum(regnum)

        if (normalizedTarget.isEmpty()) {
            return null
        }

        val vehicles =
            parseQueue(json)

        return vehicles.firstOrNull {

            normalizeRegnum(
                it.regnum
            ) == normalizedTarget
        }
    }


    /**
     * Определение состояния автомобиля
     * ИСКЛЮЧИТЕЛЬНО по текущей записи сервера.
     *
     * Никаких искусственных состояний
     * по предыдущей позиции здесь нет.
     */
    fun determineState(
        vehicle: QueueVehicle
    ): VehicleState {

        /*
         * Сервер дал order_id.
         *
         * Значит сервер сообщает,
         * что автомобиль находится
         * в живой очереди.
         */
        if (
            vehicle.orderId != null
        ) {
            return VehicleState.IN_QUEUE
        }


        /*
         * Сервер явно сообщил status = 3.
         *
         * Только этот серверный признак
         * позволяет определить CALLED.
         */
        if (
            vehicle.status == 3
        ) {
            return VehicleState.CALLED
        }


        /*
         * Остальные комбинации
         * пока не считаем вызовом.
         */
        return VehicleState.UNKNOWN
    }


    /**
     * Обрабатывает новый снимок очереди.
     *
     * ВАЖНО:
     *
     * Отсутствие автомобиля в новом JSON
     * НЕ меняет его состояние автоматически.
     */
    fun processSnapshot(
        json: String,
        timestampMillis: Long =
            System.currentTimeMillis()
    ): List<QueueVehicle> {

        val vehicles =
            parseQueue(json)


        /*
         * Обрабатываем только то,
         * что реально пришло от сервера.
         */
        for (
            vehicle in vehicles
        ) {

            val state =
                determineState(
                    vehicle
                )


            /*
             * История позиции создаётся
             * только когда сервер
             * реально передал order_id.
             */
            if (
                state ==
                VehicleState.IN_QUEUE &&
                vehicle.position != null
            ) {

                history
                    .getOrPut(
                        normalizeRegnum(
                            vehicle.regnum
                        )
                    ) {
                        mutableListOf()
                    }
                    .add(

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


            /*
             * Сохраняем последнюю
             * серверную запись автомобиля.
             *
             * Никаких изменений в ней
             * не производим.
             */
            previousVehicles[
                normalizeRegnum(
                    vehicle.regnum
                )
            ] = vehicle
        }


        /*
         * Возвращаем ВСЕ записи,
         * полученные от сервера.
         *
         * Никакой фильтрации
         * по order_id здесь больше нет.
         */
        return vehicles
    }


    /**
     * Возвращает последнюю серверную запись
     * автомобиля, если она ранее была получена.
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
     * Возвращает историю конкретного автомобиля.
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
     * Расчёт скорости движения очереди.
     *
     * Используются только реальные
     * серверные позиции.
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


        val positionsPassed =
            firstPosition -
                lastPosition


        /*
         * Очередь должна реально
         * продвинуться вперёд.
         */
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
     * Расчёт прогноза до начала вызова.
     *
     * Текущая позиция всегда берётся
     * из последней подтверждённой
     * серверной позиции.
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
         * Истории ещё недостаточно
         * для прогноза.
         *
         * Но текущую позицию
         * мы всё равно можем показать.
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
     * Сбрасывает историю нового мониторинга.
     */
    fun reset() {

        history.clear()

        previousVehicles.clear()
    }
}
