package com.pylikv.queuewatch

import org.json.JSONArray
import kotlin.math.max

/**
 * Анализатор электронной очереди.
 *
 * Основное правило проекта:
 *
 * 1. order_id != null
 *    → автомобиль находится в живой очереди.
 *
 * 2. Автомобиль раньше имел order_id,
 *    а в новом снимке order_id исчез
 *    → автомобиль считается вызванным.
 *
 * 3. Автомобиль без order_id,
 *    для которого нет предыдущей позиции
 *    → состояние UNKNOWN.
 *
 * Порядок элементов JSON НЕ используется
 * как источник позиции.
 */
class QueueAnalyzer {

    /**
     * История автомобилей.
     *
     * Ключ — регистрационный номер.
     */
    private val history =
        mutableMapOf<String, MutableList<QueueHistoryPoint>>()

    /**
     * Последнее состояние автомобиля.
     */
    private val previousVehicles =
        mutableMapOf<String, QueueVehicle>()

    /**
     * Разбирает JSON и возвращает автомобили,
     * которые реально имеют позицию в живой очереди.
     */
    fun parseQueue(json: String): List<QueueVehicle> {

        val root = JSONArray(json)

        val result = mutableListOf<QueueVehicle>()

        for (i in 0 until root.length()) {

            val item = root.optJSONObject(i)
                ?: continue

            val regnum =
                item.optString("regnum", "")
                    .trim()

            if (regnum.isEmpty()) {
                continue
            }

            val status =
                if (item.has("status") &&
                    !item.isNull("status")
                ) {
                    item.optInt("status")
                } else {
                    null
                }

            val orderId =
                if (item.has("order_id") &&
                    !item.isNull("order_id")
                ) {
                    item.optInt("order_id")
                } else {
                    null
                }

            val typeQueue =
                if (item.has("type_queue") &&
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
     * Определяет состояние автомобиля
     * относительно предыдущего снимка.
     */
    fun determineState(
        vehicle: QueueVehicle
    ): VehicleState {

        if (vehicle.orderId != null) {
            return VehicleState.IN_QUEUE
        }

        val previous =
            previousVehicles[vehicle.regnum]

        return if (
            previous?.orderId != null
        ) {
            VehicleState.CALLED
        } else {
            VehicleState.UNKNOWN
        }
    }

    /**
     * Обрабатывает новый снимок очереди.
     *
     * timestampMillis — фактическое время получения JSON.
     */
    fun processSnapshot(
        json: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): List<QueueVehicle> {

        val vehicles =
            parseQueue(json)

        val currentMap =
            vehicles.associateBy { it.regnum }

        /*
         * Сначала обрабатываем автомобили,
         * присутствующие в текущем JSON.
         */
        for (vehicle in vehicles) {

            val state =
                determineState(vehicle)

            /*
             * В историю позиции записываем только
             * реальные позиции живой очереди.
             */
            if (state == VehicleState.IN_QUEUE) {

                history
                    .getOrPut(vehicle.regnum) {
                        mutableListOf()
                    }
                    .add(
                        QueueHistoryPoint(
                            timestampMillis = timestampMillis,
                            position = vehicle.position,
                            state = state
                        )
                    )
            }

            previousVehicles[
                vehicle.regnum
            ] = vehicle
        }

        /*
         * Теперь проверяем автомобили,
         * которые были в предыдущем снимке,
         * но полностью исчезли из нового.
         *
         * Это также может быть вызовом.
         *
         * Но позицию мы им больше не присваиваем.
         */
        val disappeared =
            previousVehicles.keys
                .filter { it !in currentMap }

        for (regnum in disappeared) {

            val previous =
                previousVehicles[regnum]
                    ?: continue

            if (previous.orderId != null) {

                history
                    .getOrPut(regnum) {
                        mutableListOf()
                    }
                    .add(
                        QueueHistoryPoint(
                            timestampMillis = timestampMillis,
                            position = null,
                            state = VehicleState.CALLED
                        )
                    )
            }
        }

        /*
         * В результате наружу отдаём только
         * автомобили, которые сейчас имеют
         * реальный order_id.
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
     * Возвращает историю автомобиля.
     */
    fun getHistory(
        regnum: String
    ): List<QueueHistoryPoint> {

        return history[regnum]
            ?.toList()
            ?: emptyList()
    }

    /**
     * Рассчитывает текущую скорость очереди
     * по истории изменения позиций.
     *
     * Скорость выражается в позициях/час.
     *
     * Используем только реальные изменения
     * позиции одного и того же автомобиля.
     */
    fun calculateVehicleSpeed(
        regnum: String
    ): QueueSpeed? {

        val points =
            history[regnum]
                ?.filter {
                    it.position != null &&
                    it.state == VehicleState.IN_QUEUE
                }
                ?: return null

        if (points.size < 2) {
            return null
        }

        val first = points.first()
        val last = points.last()

        val firstPosition =
            first.position ?: return null

        val lastPosition =
            last.position ?: return null

        val elapsedMillis =
            last.timestampMillis -
                first.timestampMillis

        if (elapsedMillis <= 0) {
            return null
        }

        /*
         * Положительное движение означает,
         * что номер позиции уменьшается.
         */
        val positionsPassed =
            firstPosition - lastPosition

        if (positionsPassed <= 0) {
            return null
        }

        val hours =
            elapsedMillis / 3_600_000.0

        val positionsPerHour =
            positionsPassed / hours

        if (positionsPerHour <= 0) {
            return null
        }

        val minutesPerPosition =
            60.0 / positionsPerHour

        return QueueSpeed(
            positionsPerHour = positionsPerHour,
            minutesPerPosition = minutesPerPosition
        )
    }

    /**
     * Рассчитывает прогноз времени до вызова.
     *
     * Скорость берётся из накопленной истории.
     */
    fun calculateForecast(
        regnum: String
    ): QueueForecast? {

        val points =
            history[regnum]
                ?.filter {
                    it.position != null &&
                    it.state == VehicleState.IN_QUEUE
                }
                ?: return null

        if (points.isEmpty()) {
            return null
        }

        val currentPosition =
            points.last().position
                ?: return null

        val speed =
            calculateVehicleSpeed(regnum)

        /*
         * Пока скорости недостаточно для расчёта,
         * возвращаем состояние без прогноза.
         */
        if (speed == null) {

            return QueueForecast(
                currentPosition = currentPosition,
                positionsAhead = max(
                    0,
                    currentPosition - 1
                ),
                speed = null,
                estimatedMinutes = null
            )
        }

        val positionsAhead =
            max(
                0,
                currentPosition - 1
            )

        val estimatedMinutes =
            positionsAhead *
                speed.minutesPerPosition

        return QueueForecast(
            currentPosition = currentPosition,
            positionsAhead = positionsAhead,
            speed = speed,
            estimatedMinutes = estimatedMinutes
        )
    }

    /**
     * Полностью очищает накопленную историю.
     *
     * Используется при смене пункта пропуска
     * или начале нового независимого наблюдения.
     */
    fun reset() {

        history.clear()
        previousVehicles.clear()
    }
}
