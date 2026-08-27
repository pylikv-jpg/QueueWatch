package com.pylikv.queuewatch

import org.json.JSONArray
import kotlin.math.max

/**
 * Анализатор электронной очереди QueueWatch.
 *
 * УТВЕРЖДЁННЫЕ ПРАВИЛА ПРОЕКТА:
 *
 * 1. order_id = число
 *    → автомобиль находится в живой очереди
 *    → ему присваивается позиция.
 *
 * 2. order_id отсутствует,
 *    но автомобиль раньше имел order_id
 *    → автомобиль считается ВЫЗВАННЫМ.
 *
 * 3. order_id отсутствует
 *    и предыдущего order_id не было,
 *    но сервер сообщает статус вызова
 *    → автомобиль считается ВЫЗВАННЫМ.
 *
 * 4. Без order_id и без подтверждения вызова
 *    → UNKNOWN.
 *
 * 5. Порядок элементов JSON НЕ считается
 *    источником позиции.
 *
 * 6. Для расчёта скорости и прогноза
 *    используется накопленная история.
 */
class QueueAnalyzer {

    private val history =
        mutableMapOf<String, MutableList<QueueHistoryPoint>>()

    private val previousVehicles =
        mutableMapOf<String, QueueVehicle>()

    /**
     * Разбирает реальный JSON API.
     *
     * Структура полученного JSON:
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
     */
    fun determineState(
        vehicle: QueueVehicle
    ): VehicleState {

        /*
         * Главный признак живой очереди:
         * есть числовой order_id.
         */
        if (vehicle.orderId != null) {
            return VehicleState.IN_QUEUE
        }

        /*
         * Если раньше у этой машины был
         * order_id, а теперь исчез —
         * считаем машину вызванной.
         */
        val previous =
            previousVehicles[vehicle.regnum]

        if (previous?.orderId != null) {
            return VehicleState.CALLED
        }

        /*
         * В полученных нами данных status = 3
         * соответствует состоянию без номера,
         * которое мы рассматриваем как вызов.
         *
         * Это оставляем отдельным правилом,
         * чтобы при необходимости легко изменить
         * его после дополнительных наблюдений.
         */
        if (vehicle.status == 3) {
            return VehicleState.CALLED
        }

        return VehicleState.UNKNOWN
    }

    /**
     * Обработать новый снимок очереди.
     */
    fun processSnapshot(
        json: String,
        timestampMillis: Long =
            System.currentTimeMillis()
    ): List<QueueVehicle> {

        val vehicles =
            parseQueue(json)

        /*
         * Сохраняем ПРЕДЫДУЩЕЕ состояние
         * до обновления previousVehicles.
         *
         * Это важно для определения исчезнувших
         * автомобилей.
         */
        val oldVehicles =
            previousVehicles.toMap()

        val currentMap =
            vehicles.associateBy {
                it.regnum
            }

        /*
         * Обрабатываем автомобили,
         * присутствующие в текущем JSON.
         */
        for (vehicle in vehicles) {

            val previous =
                oldVehicles[vehicle.regnum]

            val state =
                when {

                    vehicle.orderId != null ->
                        VehicleState.IN_QUEUE

                    previous?.orderId != null ->
                        VehicleState.CALLED

                    vehicle.status == 3 ->
                        VehicleState.CALLED

                    else ->
                        VehicleState.UNKNOWN
                }

            /*
             * В историю позиции записываем
             * ТОЛЬКО живую очередь.
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
             * Сохраняем текущее состояние.
             */
            previousVehicles[
                vehicle.regnum
            ] = vehicle
        }

        /*
         * Теперь ищем автомобили,
         * которые полностью исчезли
         * из нового JSON.
         */
        for (
            (regnum, previous)
            in oldVehicles
        ) {

            if (
                regnum !in currentMap &&
                previous.orderId != null
            ) {

                /*
                 * Автомобиль имел позицию,
                 * а затем исчез из живой очереди.
                 *
                 * Считаем его вызванным.
                 *
                 * Позицию больше НЕ записываем.
                 */
                history
                    .getOrPut(regnum) {
                        mutableListOf()
                    }
                    .add(
                        QueueHistoryPoint(
                            timestampMillis =
                                timestampMillis,
                            position = null,
                            state =
                                VehicleState.CALLED
                        )
                    )

                /*
                 * Сохраняем состояние вызова,
                 * даже если запись исчезла из JSON.
                 */
                previousVehicles[regnum] =
                    QueueVehicle(
                        regnum = regnum,
                        status = previous.status,
                        orderId = null,
                        typeQueue =
                            previous.typeQueue,
                        registrationDate =
                            previous.registrationDate,
                        changedDate =
                            previous.changedDate
                    )
            }
        }

        /*
         * Наружу возвращаем только
         * автомобили с реальным order_id.
         *
         * Именно они образуют текущую
         * живую очередь.
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
     * История конкретного автомобиля.
     */
    fun getHistory(
        regnum: String
    ): List<QueueHistoryPoint> {

        return history[regnum]
            ?.toList()
            ?: emptyList()
    }

    /**
     * Расчёт текущей скорости очереди.
     *
     * Единица:
     * позиции / час.
     *
     * В расчёт попадают только реальные
     * изменения позиции автомобиля.
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

        if (points.size < 2) {
            return null
        }

        /*
         * Для устойчивости в будущем
         * можно заменить first/last
         * на скользящее окно.
         *
         * Пока оставляем простой вариант.
         */
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

        if (elapsedMillis <= 0) {
            return null
        }

        /*
         * Например:
         *
         * было 20
         * стало 15
         *
         * значит очередь прошла
         * 5 позиций.
         */
        val positionsPassed =
            firstPosition -
                lastPosition

        if (positionsPassed <= 0) {
            return null
        }

        val hours =
            elapsedMillis /
                3_600_000.0

        val positionsPerHour =
            positionsPassed /
                hours

        if (positionsPerHour <= 0) {
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
     * Прогноз времени до вызова.
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

        val currentPosition =
            points.last().position
                ?: return null

        /*
         * Если автомобиль №1,
         * впереди никого нет.
         */
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
         * Пока накоплено недостаточно
         * истории — позицию показываем,
         * прогноз не выдаём.
         */
        if (speed == null) {

            return QueueForecast(
                currentPosition =
                    currentPosition,
                positionsAhead =
                    positionsAhead,
                speed = null,
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
     * Очистить историю.
     *
     * Используется при смене пункта
     * пропуска или начале нового наблюдения.
     */
    fun reset() {

        history.clear()
        previousVehicles.clear()
    }
}
