package com.pylikv.queuewatch

/**
 * Состояние одного автомобиля из снимка очереди.
 *
 * position:
 *   номер в живой очереди.
 *   null означает, что автомобиль больше
 *   не имеет номера в живой очереди.
 */
data class QueueVehicle(
    val regnum: String,
    val status: Int?,
    val orderId: Int?,
    val typeQueue: Int?,
    val registrationDate: String?,
    val changedDate: String?
) {
    val position: Int?
        get() = orderId
}

/**
 * Результат анализа конкретного автомобиля.
 */
enum class VehicleState {

    /**
     * Автомобиль имеет номер в живой очереди.
     */
    IN_QUEUE,

    /**
     * Автомобиль раньше имел номер,
     * но в новом снимке номер исчез.
     *
     * Это наша утверждённая логика:
     * автомобиль считается вызванным.
     */
    CALLED,

    /**
     * Автомобиль обнаружен без номера
     * и у него нет подтверждённой предыдущей позиции.
     */
    UNKNOWN
}

/**
 * Историческая точка наблюдения.
 *
 * Каждая новая загрузка JSON создаёт
 * новую точку для автомобиля.
 */
data class QueueHistoryPoint(
    val timestampMillis: Long,
    val position: Int?,
    val state: VehicleState
)

/**
 * Динамическая статистика движения очереди.
 */
data class QueueSpeed(
    val positionsPerHour: Double,
    val minutesPerPosition: Double
)

/**
 * Прогноз для конкретного автомобиля.
 */
data class QueueForecast(
    val currentPosition: Int?,
    val positionsAhead: Int?,
    val speed: QueueSpeed?,
    val estimatedMinutes: Double?
)
