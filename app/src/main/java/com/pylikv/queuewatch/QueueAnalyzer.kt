package com.pylikv.queuewatch

import org.json.JSONArray
import kotlin.math.max

/**
 * Анализатор электронной очереди QueueWatch.
 *
 * ЛОГИКА:
 *
 * 1. order_id существует
 *    -> автомобиль находится в живой очереди.
 *    -> order_id является позицией.
 *
 * 2. order_id отсутствует
 *    -> это НЕ означает автоматически вызов.
 *
 * 3. Временное исчезновение автомобиля из JSON
 *    -> UNKNOWN.
 *    -> ложный CALLED не создаём.
 *
 * 4. status == 3
 *    -> подтверждённый сервером вызов.
 *    -> CALLED.
 *
 * 5. status == 2 / type_queue == 1
 *    -> само по себе НЕ является вызовом.
 *
 * 6. Порядок элементов JSON
 *    -> не используется для определения позиции.
 *
 * 7. История содержит только реальные позиции
 *    из order_id.
 *
 * 8. Скорость очереди рассчитывается
 *    по накопленной истории.
 */
class QueueAnalyzer {

    private val history =
        mutableMapOf<String, MutableList<QueueHistoryPoint>>()

    private val previousVehicles =
        mutableMapOf<String, QueueVehicle>()

    /**
     * Разбирает реальный JSON API.
     *
     * Ожидаемая структура:
     *
     * {
     *   "info": {...},
     *   "truckLiveQueue": [...]
     * }
     */
    fun parseQueue(json: String): List<QueueVehicle> {

        val root =
            org.json.JSONObject(json)

        val queue =
            root.optJSONArray("truckLiveQueue")
                ?: JSONArray()

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
                if (
                    item.has("status") &&
                    !item.isNull("status")
                ) {
                    item.optInt("status")
                } else {
                    null
                }

            val orderId =
                if (
                    item.has("order_id") &&
                    !item.isNull("order_id")
                ) {
                    item.optInt("order_id")
                } else {
                    null
                }

            val typeQueue =
                if (
                    item.has("type_queue") &&
                    !item.isNull("type_queue")
                ) {
                    item.optInt("type_queue")
                } else {
                    null
                }

            val registrationDate =
                item.optString(
                    "registration_date",
                    null
                )

            val changedDate =
                item.optString(
                    "changed_date",
                    null
                )

            result += QueueVehicle(
                regnum = regnum,
                status = status,
                orderId = orderId,
                typeQueue = typeQueue,
                registrationDate = registrationDate,
                changedDate = changedDate
            )
        }

        return result
    }

    /**
     * Определяет состояние автомобиля.
     *
     * ВАЖНО:
     *
     * Отсутствие order_id НЕ является достаточным
     * признаком вызова.
     */
    fun determineState(
        vehicle: QueueVehicle
    ): VehicleState {

        /*
         * Есть order_id:
         *
         * автомобиль находится в живой очереди.
         */
        if (vehicle.orderId != null) {
            return VehicleState.IN_QUEUE
        }

        /*
         * Явный серверный статус вызова.
         *
         * Только здесь без order_id
         * разрешаем состояние CALLED.
         */
        if (vehicle.status == 3) {
            return VehicleState.CALLED
        }

        /*
         * Нет order_id.
         *
         * Нет подтверждённого вызова.
         *
         * Поэтому состояние UNKNOWN.
         */
        return VehicleState.UNKNOWN
    }

    /**
     * Обрабатывает новый снимок очереди.
     *
     * ВАЖНО:
     *
     * Если автомобиль полностью исчез из нового JSON,
     * мы НЕ объявляем его вызванным.
     *
     * Причины исчезновения могут быть разными:
     *
     * - временный сбой API;
     * - неполный ответ;
     * - обновление данных;
     * - переход записи между состояниями;
     * - временная ошибка сервера.
     */
    fun processSnapshot(
        json: String,
        timestampMillis: Long =
            System.currentTimeMillis()
    ): List<QueueVehicle> {

        val vehicles =
            parseQueue(json)

        /*
         * Сохраняем предыдущие данные
         * до обновления текущего состояния.
         */
        val oldVehicles =
            previousVehicles.toMap()

        /*
         * Текущие автомобили по регистрационному номеру.
         */
        val currentMap =
            vehicles.associateBy {
                it.regnum
            }

        /*
         * Обрабатываем автомобили,
         * которые присутствуют в текущем JSON.
         */
        for (vehicle in vehicles) {

            val previous =
                oldVehicles[vehicle.regnum]

            /*
             * Состояние определяется
             * ТОЛЬКО по текущим данным.
             */
            val state =
                when {

                    /*
                     * Реальный order_id
                     * означает живую очередь.
                     */
                    vehicle.orderId != null ->
                        VehicleState.IN_QUEUE

                    /*
                     * Явный серверный статус вызова.
                     */
                    vehicle.status == 3 ->
                        VehicleState.CALLED

                    /*
                     * Все остальные случаи
                     * пока неопределённые.
                     */
                    else ->
                        VehicleState.UNKNOWN
                }

            /*
             * В историю позиции записываем
             * только реальные позиции живой очереди.
             */
            if (
                state ==
                VehicleState.IN_QUEUE
            ) {

                history
                    .getOrPut(
                        vehicle.regnum
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
             * Сохраняем текущее состояние автомобиля.
             *
             * Даже если order_id отсутствует,
             * сохраняем саму запись.
             */
            previousVehicles[
                vehicle.regnum
            ] = vehicle
        }

        /*
         * ВАЖНО!
         *
         * Здесь раньше была ошибка:
         *
         * автомобиль имел order_id
         * ->
         * исчез из JSON
         * ->
         * CALLED
         *
         * ЭТО БОЛЬШЕ НЕ ДЕЛАЕМ.
         *
         * Если автомобиля нет в текущем JSON,
         * ничего не меняем.
         *
         * Его последнее подтверждённое состояние
         * остаётся в previousVehicles.
         *
         * Следующий снимок сможет подтвердить:
         *
         * - возвращение в очередь;
         * - явный status == 3;
         * - либо другое состояние.
         */

        /*
         * Переменная currentMap намеренно создаётся выше:
         * она показывает наличие автомобилей
         * в текущем снимке и оставлена здесь
         * для понятности алгоритма.
         *
         * Отсутствующие автомобили НЕ переводятся
         * автоматически в CALLED.
         */
        @Suppress("UNUSED_VARIABLE")
        val ignoredCurrentMap =
            currentMap

        /*
         * Наружу возвращаем только автомобили,
         * у которых действительно есть order_id.
         *
         * Именно эти автомобили образуют
         * текущую живую очередь.
         */
        return vehicles
            .filter {
                it.orderId != null
            }
            .sortedBy {
                it.orderId
            }
    }

    /**
     * Возвращает историю конкретного автомобиля.
     */
    fun getHistory(
        regnum: String
    ): List<QueueHistoryPoint> {

        return history[regnum]
            ?.toList()
            ?: emptyList()
    }

    /**
     * Рассчитывает скорость движения очереди.
     *
     * Единица измерения:
     *
     * позиции / час.
     *
     * Используются только реальные изменения
     * позиции автомобиля.
     */
    fun calculateVehicleSpeed(
        regnum: String
    ): QueueSpeed? {

        val points =
            history[regnum]
                ?.filter {
                    it.position != null &&
                    it.state ==
                        VehicleState.IN_QUEUE
                }
                ?: return null

        /*
         * Нужны минимум две точки.
         */
        if (points.size < 2) {
            return null
        }

        /*
         * Первая подтверждённая позиция.
         */
        val first =
            points.first()

        /*
         * Последняя подтверждённая позиция.
         */
        val last =
            points.last()

        val firstPosition =
            first.position
                ?: return null

        val lastPosition =
            last.position
                ?: return null

        /*
         * Прошедшее время.
         */
        val elapsedMillis =
            last.timestampMillis -
                first.timestampMillis

        if (elapsedMillis <= 0) {
            return null
        }

        /*
         * Например:
         *
         * было 35
         * стало 30
         *
         * очередь продвинулась
         * на 5 позиций.
         */
        val positionsPassed =
            firstPosition -
                lastPosition

        /*
         * Если позиция не уменьшилась,
         * скорость движения не определяем.
         */
        if (positionsPassed <= 0) {
            return null
        }

        /*
         * Время в часах.
         */
        val hours =
            elapsedMillis /
                3_600_000.0

        if (hours <= 0) {
            return null
        }

        /*
         * Скорость:
         *
         * позиции / час.
         */
        val positionsPerHour =
            positionsPassed /
                hours

        if (positionsPerHour <= 0) {
            return null
        }

        /*
         * Сколько минут занимает одна позиция.
         */
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
     * Рассчитывает прогноз времени
     * до начала вызова автомобиля.
     */
    fun calculateForecast(
        regnum: String
    ): QueueForecast? {

        val points =
            history[regnum]
                ?.filter {
                    it.position != null &&
                    it.state ==
                        VehicleState.IN_QUEUE
                }
                ?: return null

        if (points.isEmpty()) {
            return null
        }

        /*
         * Последняя подтверждённая позиция.
         */
        val currentPosition =
            points.last().position
                ?: return null

        /*
         * Количество автомобилей,
         * находящихся впереди.
         */
        val positionsAhead =
            max(
                0,
                currentPosition - 1
            )

        /*
         * Рассчитываем скорость.
         */
        val speed =
            calculateVehicleSpeed(
                regnum
            )

        /*
         * Истории пока недостаточно.
         *
         * Позицию показываем,
         * прогноз пока не строим.
         */
        if (speed == null) {

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

        /*
         * Расчёт времени:
         *
         * количество позиций впереди
         * × минут на одну позицию.
         */
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
     * Полностью очищает историю
     * и предыдущие состояния.
     *
     * Используется при начале
     * нового мониторинга.
     */
    fun reset() {

        history.clear()

        previousVehicles.clear()
    }
}
