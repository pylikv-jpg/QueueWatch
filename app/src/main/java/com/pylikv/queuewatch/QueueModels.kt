package com.pylikv.queuewatch

/**
 * Тип транспортного средства.
 */
enum class VehicleType {
    CAR,
    TRUCK,
    BUS,
    MOTORCYCLE
}


/**
 * Одна запись транспортного средства
 * непосредственно из API.
 */
data class QueueVehicle(
    val regnum: String,
    val status: Int?,
    val orderId: Int?,
    val typeQueue: Int?,
    val registrationDate: String?,
    val changedDate: String?,
    val vehicleType: VehicleType = VehicleType.CAR
) {
    val position: Int?
        get() = orderId
}


/**
 * Состояние автомобиля.
 */
enum class VehicleState {
    IN_QUEUE,
    CALLED,
    UNKNOWN
}


/**
 * Историческая точка наблюдения.
 */
data class QueueHistoryPoint(
    val timestampMillis: Long,
    val position: Int?,
    val state: VehicleState
)


/**
 * Скорость прохождения очереди.
 *
 * positionsPerHour:
 * фактическое количество автомобилей,
 * прошедших через очередь за час.
 */
data class QueueSpeed(
    val positionsPerHour: Double,
    val minutesPerPosition: Double
)


/**
 * Прогноз автомобиля.
 */
data class QueueForecast(
    val currentPosition: Int?,
    val positionsAhead: Int?,
    val speed: QueueSpeed?,
    val estimatedMinutes: Double?
)


/**
 * Одна статистическая ячейка.
 *
 * 7 дней недели × 24 часа.
 */
data class QueueStatisticsCell(
    val dayOfWeek: Int,
    val hour: Int,

    /**
     * Сколько подтверждённых вызовов
     * накоплено в этом часовом интервале.
     */
    val calledCount: Int = 0,

    /**
     * Количество наблюдений,
     * при которых этот час был реально
     * проверен программой.
     */
    val observedCount: Int = 0,

    /**
     * Суммарное время ожидания автомобилей,
     * у которых удалось определить
     * регистрацию и вызов.
     */
    val totalWaitingMinutes: Double = 0.0,

    /**
     * Количество автомобилей,
     * для которых известно время ожидания.
     */
    waitingSamples: Int = 0
) {

    /**
     * Среднее количество вызванных автомобилей
     * за наблюдаемый час.
     */
    val callsPerObservedHour: Double
        get() =
            if (observedCount > 0) {
                calledCount.toDouble() / observedCount
            } else {
                0.0
            }

    /**
     * Среднее фактическое время ожидания.
     */
    val averageWaitingMinutes: Double?
        get() =
            if (waitingSamples > 0) {
                totalWaitingMinutes / waitingSamples
            } else {
                null
            }
}


/**
 * Результат расчёта скорости
 * с учётом конкретного часового интервала.
 */
data class HourlySpeed(
    val dayOfWeek: Int,
    val hour: Int,
    val positionsPerHour: Double,
    val observed: Boolean
)
